/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import opa.spatial.PatternFunction;
import opa.spatial.RectangularWindow;
import org.junit.Test;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OPATest {

    @Test
    public void publicApiRunsAllDirectedPairsAndBuildsTables() {
        ImagePlus first = labels("A", 10, 10, 1);
        first.getProcessor().set(2, 2, 1);
        first.getProcessor().set(7, 7, 2);
        ImagePlus second = labels("B", 10, 10, 1);
        second.getProcessor().set(3, 2, 1);
        second.getProcessor().set(8, 7, 2);
        calibrate(first);
        calibrate(second);

        OPAParameters parameters = OPAParameters.builder(first, second)
                .channelNames(Arrays.asList("Cells", "Puncta"))
                .distanceModes(EnumSet.of(
                        DistanceMode.CENTRE_TO_CENTRE,
                        DistanceMode.EDGE_TO_EDGE))
                .neighborCount(1)
                .contactDistance(1.0)
                .patternFunctions(EnumSet.of(
                        PatternFunction.K,
                        PatternFunction.CROSS_K))
                .radii(new double[]{1.0, 2.0})
                .simulations(3)
                .seed(9L)
                .build();

        OPAResult result = OPA.run(parameters);

        assertEquals(4, result.getDirectionResults().size());
        assertEquals(4, result.getPatternResults().size());
        assertEquals(4, result.getPerObjectTables().size());
        assertFalse(result.getCurveTables().isEmpty());
        assertTrue(result.getDistanceSummaryTable().size() > 0);
        assertEquals(4, result.getPatternSummaryTable().size());
        assertTrue(result.hasPhysicalCalibration());
        ResultsTableAssertions.assertColumnEquals(
                result.getCurveTables().values().iterator().next(),
                "Value_Unit",
                "\u00b5m^2");
    }

    @Test
    public void uncalibratedInputUsesPixelUnitsWithoutClaimingMicrometres() {
        ImagePlus image = labels("pixels", 5, 5, 1);
        image.getProcessor().set(2, 2, 1);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .build());

        assertFalse(result.hasPhysicalCalibration());
        assertEquals("pixel", result.getChannels().get(0)
                .getCalibration().getUnit());
        assertNotNull(result.getDistanceSummaryTable());
    }

    @Test(expected = IllegalArgumentException.class)
    public void physicalCalibrationCanBeRequired() {
        ImagePlus image = labels("pixels", 5, 5, 1);
        image.getProcessor().set(2, 2, 1);
        OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .requirePhysicalCalibration(true)
                .build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidPhysicalCalibrationIsRejectedRatherThanNormalized() {
        ImagePlus image = labels("invalid-calibration", 5, 5, 1);
        image.getProcessor().set(2, 2, 1);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.0;
        calibration.pixelHeight = 1.0;
        calibration.pixelDepth = 1.0;
        calibration.setUnit("um");
        image.setCalibration(calibration);

        OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void threeDimensionalPatternRequiresExplicitProjection() {
        ImagePlus image = labels("volume", 5, 5, 2);
        image.getStack().getProcessor(1).set(2, 2, 1);
        image.getStack().getProcessor(2).set(2, 2, 1);
        OPA.run(OPAParameters.builder(image)
                .runDistances(false)
                .patternFunctions(EnumSet.of(PatternFunction.K))
                .radii(new double[]{1.0})
                .simulations(1)
                .build());
    }

    @Test
    public void observationWindowFiltersObjectsByCentroid() {
        ImagePlus image = labels("windowed", 10, 10, 1);
        image.getProcessor().set(2, 2, 1);
        image.getProcessor().set(8, 8, 2);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .observationWindow(new RectangularWindow(0.0, 0.0, 5.0, 5.0))
                .build());

        assertEquals(1, result.getChannels().get(0).getObjects().size());
        assertEquals(1, result.getChannels().get(0).getObjects().get(0).getLabel());
    }

    @Test
    public void distanceSummaryIncludesEveryRequestedNeighborRank() {
        ImagePlus image = labels("ranked", 10, 3, 1);
        image.getProcessor().set(1, 1, 1);
        image.getProcessor().set(4, 1, 2);
        image.getProcessor().set(8, 1, 3);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .distanceModes(EnumSet.of(DistanceMode.CENTRE_TO_CENTRE))
                .neighborCount(2)
                .build());

        assertEquals(2, result.getDistanceSummaryTable().size());
        assertEquals(1.0, result.getDistanceSummaryTable()
                .getValue("Rank", 0), 0.0);
        assertEquals(2.0, result.getDistanceSummaryTable()
                .getValue("Rank", 1), 0.0);
    }

    @Test
    public void sanitizedChannelNameCollisionsDoNotOverwriteResultTables() {
        ImagePlus first = labels("first", 7, 3, 1);
        ImagePlus second = labels("second", 7, 3, 1);
        first.getProcessor().set(1, 1, 1);
        first.getProcessor().set(5, 1, 2);
        second.getProcessor().set(2, 1, 1);
        second.getProcessor().set(6, 1, 2);

        OPAResult result = OPA.run(OPAParameters.builder(first, second)
                .channelNames(Arrays.asList("A B", "A_B"))
                .distanceModes(EnumSet.of(DistanceMode.CENTRE_TO_CENTRE))
                .patternFunctions(EnumSet.of(
                        PatternFunction.K,
                        PatternFunction.CROSS_K))
                .radii(new double[]{1.0})
                .simulations(1)
                .build());

        assertEquals(4, result.getDirectionResults().size());
        assertEquals(4, result.getPerObjectTables().size());
        assertEquals(4, result.getHistogramTables().size());
        assertEquals(4, result.getEcdfTables().size());
        assertEquals(4, result.getPatternResults().size());
        assertEquals(4, result.getCurveTables().size());
    }

    @Test
    public void duplicateImageTitlesGetDistinctDialogChoices() {
        String[] choices = Object_Proximity_Analysis.disambiguateImageTitles(
                new String[]{"Labels", "Labels", "Reference"});

        assertEquals("[None]", choices[0]);
        assertFalse(choices[1].equals(choices[2]));
        assertTrue(choices[1].contains("Labels"));
        assertTrue(choices[2].contains("Labels"));
        assertEquals("Reference", choices[3]);
    }

    @Test
    public void duplicateApiChannelNamesGetDistinctOutputIdentities() {
        ImagePlus first = labels("Labels", 7, 3, 1);
        ImagePlus second = labels("Labels", 7, 3, 1);
        first.getProcessor().set(1, 1, 1);
        second.getProcessor().set(5, 1, 1);

        OPAResult result = OPA.run(OPAParameters.builder(first, second)
                .channelNames(Arrays.asList("Labels", "Labels"))
                .runPattern(false)
                .distanceModes(EnumSet.of(DistanceMode.CENTRE_TO_CENTRE))
                .build());

        Set<String> names = new HashSet<String>();
        names.add(result.getChannels().get(0).getName());
        names.add(result.getChannels().get(1).getName());
        assertEquals(2, names.size());
        for (int row = 0; row < result.getDistanceSummaryTable().size(); row++) {
            assertTrue(names.contains(result.getDistanceSummaryTable()
                    .getStringValue("Source_Channel", row)));
            assertTrue(names.contains(result.getDistanceSummaryTable()
                    .getStringValue("Target_Channel", row)));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void multichannelHyperstackIsRejected() {
        ImagePlus image = labels("channels", 5, 5, 2);
        image.setDimensions(2, 1, 1);
        OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void timeSeriesIsRejected() {
        ImagePlus image = labels("frames", 5, 5, 2);
        image.setDimensions(1, 1, 2);
        OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void singleChannelDistanceAnalysisRequiresSelfDistances() {
        ImagePlus image = labels("single", 5, 5, 1);
        image.getProcessor().set(2, 2, 1);
        OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .includeSelfDistances(false)
                .build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void singleChannelCrossOnlyPatternAnalysisIsRejected() {
        ImagePlus image = labels("single-cross", 5, 5, 1);
        image.getProcessor().set(2, 2, 1);
        OPA.run(OPAParameters.builder(image)
                .runDistances(false)
                .patternFunctions(EnumSet.of(PatternFunction.CROSS_K))
                .radii(new double[]{1.0})
                .simulations(1)
                .build());
    }

    @Test
    public void distanceOnlyIgnoresDisabledPatternSettings() {
        ImagePlus image = labels("distance-only", 5, 5, 1);
        image.getProcessor().set(1, 1, 1);
        image.getProcessor().set(3, 3, 2);

        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .patternFunctions(EnumSet.noneOf(PatternFunction.class))
                .radii(new double[0])
                .radiusBins(0)
                .maximumRadius(-1.0)
                .simulations(0)
                .edgeCorrection(null)
                .build());

        assertFalse(result.getDirectionResults().isEmpty());
    }

    @Test
    public void patternOnlyIgnoresDisabledDistanceSettings() {
        ImagePlus image = labels("pattern-only", 5, 5, 1);
        image.getProcessor().set(1, 1, 1);
        image.getProcessor().set(3, 3, 2);

        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runDistances(false)
                .distanceModes(EnumSet.noneOf(DistanceMode.class))
                .includeSelfDistances(false)
                .neighborCount(0)
                .contactDistance(-1.0)
                .histogramBins(0)
                .patternFunctions(EnumSet.of(PatternFunction.K))
                .radii(new double[]{1.0})
                .simulations(1)
                .build());

        assertEquals(1, result.getPatternResults().size());
    }

    @Test
    public void escapeCancelsAnActivePatternAnalysis() {
        ImagePlus image = labels("cancel-pattern", 5, 5, 1);
        image.getProcessor().set(1, 1, 1);
        image.getProcessor().set(3, 3, 2);
        boolean cancelled = false;
        try {
            IJ.setKeyDown(KeyEvent.VK_ESCAPE);
            OPA.run(OPAParameters.builder(image)
                    .runDistances(false)
                    .patternFunctions(EnumSet.of(PatternFunction.K))
                    .radii(new double[]{1.0})
                    .simulations(10)
                    .build());
        } catch (AnalysisCancelledException expected) {
            cancelled = true;
        } finally {
            IJ.resetEscape();
            IJ.setKeyUp(KeyEvent.VK_ESCAPE);
        }
        assertTrue(cancelled);
    }

    @Test
    public void customWindowMarksObjectsCrossingItsBoundaryAsEdgeObjects() {
        ImagePlus image = labels("custom-window", 10, 6, 1);
        image.getProcessor().set(3, 2, 1);
        image.getProcessor().set(4, 2, 1);
        image.getProcessor().set(5, 2, 1);
        image.getProcessor().set(1, 1, 2);

        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .observationWindow(new RectangularWindow(0.0, 0.0, 5.0, 5.0))
                .build());

        assertTrue(result.getChannels().get(0).getObject(1).isEdgeObject());
        assertFalse(result.getChannels().get(0).getObject(2).isEdgeObject());
    }

    private static ImagePlus labels(String title, int width, int height, int depth) {
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            stack.addSlice(new ByteProcessor(width, height));
        }
        return new ImagePlus(title, stack);
    }

    private static void calibrate(ImagePlus image) {
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 1.0;
        calibration.setUnit("um");
        image.setCalibration(calibration);
    }

    private static final class ResultsTableAssertions {
        private ResultsTableAssertions() {
        }

        private static void assertColumnEquals(ij.measure.ResultsTable table,
                                               String heading,
                                               String expected) {
            assertEquals(expected, table.getStringValue(heading, 0));
        }
    }
}
