/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.equivalence;

import ij.IJ;
import ij.ImagePlus;
import sc.fiji.opa.core.DistanceMode;
import opa.OPA;
import opa.OPAParameters;
import sc.fiji.opa.core.spatial.EdgeCorrection;
import sc.fiji.opa.core.spatial.PatternFunction;
import sc.fiji.opa.core.spatial.RectangularWindow;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Every documented parameter rejection, with its exact message text.
 *
 * <p>The configuration sweep in {@link GoldenMasterTest} runs only valid
 * option sets, so the rejections it records are the ones a corpus case
 * triggers. This class covers the rest of {@code OPA.validateOptions}
 * deliberately, one entry per rule, so the complete rejection vocabulary is
 * pinned in one readable file before any code moves.</p>
 */
public class ParameterRejectionGoldenTest {

    @Test
    public void everyDocumentedRejectionKeepsItsExactMessage()
            throws IOException {
        IJ.resetEscape();
        StringBuilder text = new StringBuilder();

        record(text, "null-parameters", null);
        record(text, "zero-images", OPAParameters.builder(
                new ArrayList<ImagePlus>()));
        record(text, "six-images", OPAParameters.builder(images(6)));
        record(text, "null-image-in-list", OPAParameters.builder(
                withNull(images(2))));

        record(text, "no-analysis-enabled", valid()
                .runDistances(false).runPattern(false));
        record(text, "no-distance-modes", distances()
                .distanceModes(EnumSet.noneOf(DistanceMode.class)));
        record(text, "one-channel-without-self-distances",
                OPAParameters.builder(images(1))
                        .runDistances(true)
                        .runPattern(false)
                        .includeSelfDistances(false));
        record(text, "neighbour-count-zero", distances().neighborCount(0));
        record(text, "neighbour-count-above-limit",
                distances().neighborCount(OPAParameters.MAX_NEIGHBOR_COUNT + 1));
        record(text, "contact-distance-nan",
                distances().contactDistance(Double.NaN));
        record(text, "contact-distance-negative",
                distances().contactDistance(-1.0));
        record(text, "histogram-bins-zero", distances().histogramBins(0));
        record(text, "histogram-bins-above-limit",
                distances().histogramBins(OPAParameters.MAX_HISTOGRAM_BINS + 1));

        record(text, "no-pattern-functions", pattern()
                .patternFunctions(EnumSet.noneOf(PatternFunction.class)));
        record(text, "cross-only-one-channel",
                OPAParameters.builder(images(1))
                        .runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(PatternFunction.CROSS_K))
                        .radii(new double[]{1.0})
                        .simulations(3));
        record(text, "simulations-zero", pattern().simulations(0));
        record(text, "simulations-above-limit",
                pattern().simulations(OPAParameters.MAX_SIMULATIONS + 1));
        record(text, "null-edge-correction", pattern().edgeCorrection(null));
        record(text, "pair-correlation-with-border", pattern()
                .patternFunctions(EnumSet.of(PatternFunction.PAIR_CORRELATION))
                .edgeCorrection(EdgeCorrection.BORDER));

        record(text, "radius-bins-zero",
                pattern().radii(null).radiusBins(0).maximumRadius(2.0));
        record(text, "radius-bins-above-limit",
                pattern().radii(null)
                        .radiusBins(OPAParameters.MAX_RADIUS_BINS + 1)
                        .maximumRadius(2.0));
        record(text, "maximum-radius-nan",
                pattern().radii(null).radiusBins(5)
                        .maximumRadius(Double.NaN));
        record(text, "maximum-radius-negative",
                pattern().radii(null).radiusBins(5).maximumRadius(-1.0));
        record(text, "radii-empty", pattern().radii(new double[0]));
        record(text, "radii-decreasing",
                pattern().radii(new double[]{2.0, 1.0}));
        record(text, "radii-repeated",
                pattern().radii(new double[]{1.0, 1.0}));
        record(text, "radii-negative",
                pattern().radii(new double[]{-1.0, 1.0}));
        record(text, "radii-non-finite",
                pattern().radii(new double[]{1.0, Double.POSITIVE_INFINITY}));
        record(text, "radii-above-limit",
                pattern().radii(increasing(OPAParameters.MAX_RADIUS_BINS + 1)));
        record(text, "monte-carlo-work-budget",
                pattern().radii(increasing(OPAParameters.MAX_RADIUS_BINS))
                        .simulations(OPAParameters.MAX_SIMULATIONS));

        record(text, "observation-window-outside-bounds", valid()
                .observationWindow(
                        new RectangularWindow(-100.0, -100.0, 100.0, 100.0)));
        record(text, "physical-calibration-required",
                OPAParameters.builder(uncalibrated(1))
                        .runDistances(true)
                        .runPattern(false)
                        .requirePhysicalCalibration(true));
        record(text, "three-dimensional-pattern-without-projection",
                OPAParameters.builder(stacks(1))
                        .runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(PatternFunction.K))
                        .radii(new double[]{1.0})
                        .simulations(3)
                        .project3DToXY(false));

        compare(text.toString());
    }

    // -------------------------------------------------------------- builders

    private static OPAParameters.Builder valid() {
        return OPAParameters.builder(images(2))
                .runDistances(true)
                .runPattern(true)
                .includeSelfDistances(true)
                .distanceModes(GoldenConfigurations.MODES_AT_CAPTURE)
                .neighborCount(1)
                .contactDistance(0.0)
                .histogramBins(5)
                .patternFunctions(EnumSet.of(PatternFunction.K))
                .edgeCorrection(EdgeCorrection.TRANSLATION)
                .radii(new double[]{1.0, 2.0})
                .simulations(3)
                .project3DToXY(true);
    }

    private static OPAParameters.Builder distances() {
        return valid().runPattern(false);
    }

    private static OPAParameters.Builder pattern() {
        return valid().runDistances(false);
    }

    private static List<ImagePlus> images(int count) {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        for (int i = 0; i < count; i++) {
            ImagePlus image = GoldenCorpus.calibrated(
                    GoldenCorpus.image("I" + i, 12, 12, 1, 8));
            GoldenCorpus.box(image, 1 + i, 1 + i, 0, 2, 2, 1, 1);
            GoldenCorpus.box(image, 7, 7, 0, 2, 2, 1, 2);
            images.add(image);
        }
        return images;
    }

    private static List<ImagePlus> uncalibrated(int count) {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        for (int i = 0; i < count; i++) {
            ImagePlus image = GoldenCorpus.image("U" + i, 12, 12, 1, 8);
            GoldenCorpus.box(image, 2, 2, 0, 2, 2, 1, 1);
            images.add(image);
        }
        return images;
    }

    private static List<ImagePlus> stacks(int count) {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        for (int i = 0; i < count; i++) {
            ImagePlus image = GoldenCorpus.calibrated(
                    GoldenCorpus.image("S" + i, 8, 8, 3, 8));
            GoldenCorpus.box(image, 2, 2, 0, 2, 2, 2, 1);
            images.add(image);
        }
        return images;
    }

    private static List<ImagePlus> withNull(List<ImagePlus> images) {
        images.set(0, null);
        return images;
    }

    private static double[] increasing(int count) {
        double[] radii = new double[count];
        for (int i = 0; i < count; i++) radii[i] = i + 1.0;
        return radii;
    }

    // ----------------------------------------------------------------- gate

    private static void record(StringBuilder text,
                               String key,
                               OPAParameters.Builder builder) {
        text.append("REJECT ").append(key).append(' ');
        try {
            OPA.run(builder == null ? null : builder.build());
            text.append("NONE <no exception thrown>");
        } catch (RuntimeException rejected) {
            text.append(rejected.getClass().getName()).append(' ')
                    .append(GoldenDump.escape(rejected.getMessage()));
        }
        text.append('\n');
    }

    private static void compare(String actual) throws IOException {
        File golden = new File(
                GoldenMasterTest.goldenRoot(), "_parameter-rejections.txt");
        if (!golden.isFile()) {
            GoldenMasterTest.write(golden, actual);
            return;
        }
        String expected = GoldenMasterTest.read(golden);
        if (expected.equals(actual)) return;
        String[] expectedLines = expected.split("\n", -1);
        String[] actualLines = actual.split("\n", -1);
        int limit = Math.min(expectedLines.length, actualLines.length);
        for (int i = 0; i < limit; i++) {
            if (!expectedLines[i].equals(actualLines[i])) {
                assertEquals("rejection message moved at line " + (i + 1),
                        expectedLines[i], actualLines[i]);
            }
        }
        fail("rejection list length moved: " + expectedLines.length
                + " -> " + actualLines.length);
    }
}
