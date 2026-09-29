/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Plot;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;
import sc.fiji.opa.core.AnalysisCancelledException;
import sc.fiji.opa.core.DistanceMode;
import sc.fiji.opa.core.spatial.PatternFunction;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Edge inputs not already covered by the golden corpus or the other unit
 * tests: very large sparse label values, empty and single-object channels in
 * 3D, non-dyadic anisotropic calibration with projection, cancellation
 * followed by a fresh run, and plotting of undefined curves.
 */
public class EdgeCaseTest {

    /** The largest integer a 32-bit float label image can hold exactly. */
    private static final int LARGEST_EXACT_FLOAT_LABEL = 16_777_216;

    @Test(timeout = 20000)
    public void hugeSparseFloatLabelsRunWithoutPerLabelArrays() {
        ImagePlus image = image("float", 16, 16, 1, 32);
        set(image, 2, 2, 0, 1);
        set(image, 12, 12, 0, LARGEST_EXACT_FLOAT_LABEL);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .distanceModes(EnumSet.allOf(DistanceMode.class))
                .build());

        Set<Double> labels = sourceLabels(result);
        assertEquals(new HashSet<Double>(Arrays.asList(
                1.0, (double) LARGEST_EXACT_FLOAT_LABEL)), labels);
    }

    @Test(timeout = 20000)
    public void largestSixteenBitLabelBesideLabelOneIn3D() {
        ImagePlus image = image("short", 8, 8, 3, 16);
        set(image, 1, 1, 0, 1);
        set(image, 6, 6, 2, 65535);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .distanceModes(EnumSet.allOf(DistanceMode.class))
                .build());

        assertEquals(new HashSet<Double>(Arrays.asList(1.0, 65535.0)),
                sourceLabels(result));
    }

    @Test
    public void emptyAndSingleObjectChannelsIn2DAnd3DUseEveryMode() {
        for (int depth : new int[]{1, 3}) {
            ImagePlus single = image("single", 8, 8, depth, 8);
            ImagePlus empty = image("empty", 8, 8, depth, 8);
            set(single, 3, 3, depth / 2, 1);
            OPAResult result = OPA.run(OPAParameters.builder(single, empty)
                    .channelNames(Arrays.asList("single", "empty"))
                    .runPattern(true)
                    .project3DToXY(depth > 1)
                    .patternFunctions(EnumSet.of(
                            PatternFunction.K,
                            PatternFunction.L_MINUS_R,
                            PatternFunction.G,
                            PatternFunction.CROSS_K))
                    .radii(new double[]{1.0, 2.0})
                    .simulations(3)
                    .distanceModes(EnumSet.allOf(DistanceMode.class))
                    .includeSelfDistances(true)
                    .build());

            ResultsTable summary = result.getDistanceSummaryTable();
            assertTrue("depth " + depth, summary.size() > 0);
            for (int row = 0; row < summary.size(); row++) {
                String status = summary.getStringValue("Status", row);
                assertFalse("depth " + depth + " row " + row,
                        "OK".equals(status));
            }
            // Nothing is defined, so nothing is drawn.
            assertTrue(OPAPlots.lMinusRPlots(result).isEmpty());
        }
    }

    @Test
    public void nonDyadicAnisotropicCalibrationWithProjection() {
        ImagePlus image = image("aniso", 12, 10, 3, 8);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.1625;
        calibration.pixelHeight = 0.1625;
        calibration.pixelDepth = 0.37;
        calibration.setUnit("micron");
        image.setCalibration(calibration);
        set(image, 2, 2, 0, 1);
        set(image, 8, 3, 1, 2);
        set(image, 4, 7, 2, 3);
        set(image, 9, 8, 0, 4);

        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runDistances(true)
                .runPattern(true)
                .project3DToXY(true)
                .patternFunctions(EnumSet.of(PatternFunction.K))
                .radii(new double[]{0.5, 1.0})
                .simulations(3)
                .build());

        ResultsTable provenance = result.getProvenanceTable();
        assertEquals(12 * 0.1625,
                provenance.getValue("Window_Max_X", 0), 1.0e-12);
        assertEquals(10 * 0.1625,
                provenance.getValue("Window_Max_Y", 0), 1.0e-12);
        assertEquals(0.37, provenance.getValue("Pixel_Depth", 0), 0.0);
        assertEquals(1, result.getPatternResults().size());
        assertEquals("micron", result.getPatternResults().get(0).getUnit());
    }

    @Test
    public void escapeMidDistanceCancelsAndTheNextRunStillWorks() {
        ImagePlus image = image("cancel", 8, 8, 1, 8);
        set(image, 1, 1, 0, 1);
        set(image, 5, 5, 0, 2);
        boolean cancelled = false;
        try {
            OPA.run(OPAParameters.builder(image)
                    .runPattern(false)
                    .progressListener(new OPAProgressListener() {
                        @Override
                        public void onProgress(double fraction, String message) {
                            if (fraction > 0.0 && fraction < 1.0) {
                                IJ.setKeyDown(KeyEvent.VK_ESCAPE);
                            }
                        }
                    })
                    .build());
        } catch (AnalysisCancelledException expected) {
            cancelled = true;
        } finally {
            IJ.resetEscape();
            IJ.setKeyUp(KeyEvent.VK_ESCAPE);
        }
        assertTrue(cancelled);

        OPAResult next = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .build());
        assertEquals(2, next.getDirectionResults().get(0).getSourceObjectCount());
    }

    @Test
    public void lMinusRPlotIsBuiltForANormalInput() {
        ImagePlus image = image("normal", 20, 20, 1, 8);
        int label = 1;
        for (int y = 2; y < 20; y += 5) {
            for (int x = 2; x < 20; x += 5) {
                set(image, x, y, 0, label++);
            }
        }
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runDistances(false)
                .patternFunctions(EnumSet.of(PatternFunction.L_MINUS_R))
                .radii(new double[]{2.0, 4.0, 6.0})
                .simulations(19)
                .build());

        List<Plot> plots = OPAPlots.lMinusRPlots(result);
        assertEquals(1, plots.size());
    }

    @Test
    public void lMinusRPlotIsSkippedForASingleObject() {
        // Regression: a one-object channel has no defined L(r)-r, and the
        // plot was built from all-NaN values, including its envelope polygon.
        ImagePlus image = image("one", 10, 10, 1, 8);
        set(image, 4, 4, 0, 1);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runDistances(false)
                .patternFunctions(EnumSet.of(PatternFunction.L_MINUS_R))
                .radii(new double[]{1.0, 2.0})
                .simulations(3)
                .build());

        assertEquals(1, result.getPatternResults().size());
        assertTrue(OPAPlots.lMinusRPlots(result).isEmpty());
    }

    @Test
    public void envelopeIsFilledOnlyWhereBothBoundsAreDefined() {
        double nan = Double.NaN;
        double[] radii = {1, 2, 3, 4, 5, 6, 7, 8};
        double[] lower = {nan, 0, 0, nan, 0, 0, 0, nan};
        double[] upper = {nan, 1, 1, 1, 1, 1, nan, 1};
        Plot plot = new Plot("envelope", "r", "L");
        // Defined at radii 2-3 and 5-6; radius 7 has no upper bound.
        assertEquals(2, OPAPlots.addEnvelope(plot, radii, lower, upper));

        Plot none = new Plot("none", "r", "L");
        assertEquals(0, OPAPlots.addEnvelope(none,
                radii, new double[8], filled(nan, 8)));
        // A single defined radius cannot make an area.
        assertEquals(0, OPAPlots.addEnvelope(new Plot("one", "r", "L"),
                new double[]{1, 2, 3},
                new double[]{nan, 0, nan},
                new double[]{nan, 1, nan}));
    }

    private static double[] filled(double value, int length) {
        double[] values = new double[length];
        Arrays.fill(values, value);
        return values;
    }

    private static Set<Double> sourceLabels(OPAResult result) {
        Set<Double> labels = new HashSet<Double>();
        for (Map.Entry<String, ResultsTable> entry
                : result.getPerObjectTables().entrySet()) {
            ResultsTable table = entry.getValue();
            for (int row = 0; row < table.size(); row++) {
                labels.add(table.getValue("Source_Label", row));
            }
        }
        return labels;
    }

    private static ImagePlus image(String title, int width, int height,
                                   int depth, int bitDepth) {
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            ImageProcessor processor;
            if (bitDepth == 32) processor = new FloatProcessor(width, height);
            else if (bitDepth == 16) processor = new ShortProcessor(width, height);
            else processor = new ByteProcessor(width, height);
            stack.addSlice(processor);
        }
        ImagePlus image = new ImagePlus(title, stack);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 1.0;
        calibration.setUnit("micron");
        image.setCalibration(calibration);
        return image;
    }

    private static void set(ImagePlus image, int x, int y, int z, int label) {
        ImageProcessor processor = image.getStack().getProcessor(z + 1);
        if (processor instanceof FloatProcessor) {
            processor.setf(x, y, (float) label);
        } else {
            processor.set(x, y, label);
        }
    }
}
