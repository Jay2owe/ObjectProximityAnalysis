/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import opa.spatial.PatternFunction;
import opa.spatial.RectangularWindow;
import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;

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
}
