/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.equivalence;

import ij.IJ;
import sc.fiji.opa.core.spatial.EdgeCorrection;
import sc.fiji.opa.core.spatial.MonteCarloAnalyzer;
import sc.fiji.opa.core.spatial.MonteCarloResult;
import sc.fiji.opa.core.spatial.PatternFunction;
import sc.fiji.opa.core.spatial.RectangularWindow;
import sc.fiji.opa.core.spatial.SpatialStatistics;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Engine golden for cross pair correlation, added at opa-core 0.2.0.
 *
 * <p>A separate file from {@code _engine-contract.txt} on purpose. That golden
 * records the output of the eight functions that existed when it was captured,
 * and a new statistic is new coverage, not a reason to rewrite it. Keeping them
 * apart is what lets "all 546 earlier goldens are byte-identical" stay a
 * checkable claim rather than a claim about a file that grew.</p>
 *
 * <p>Same contract: every value as its raw IEEE-754 bit pattern, captured once,
 * no regeneration switch.</p>
 */
public class CrossPairCorrelationGoldenTest {

    private static final double[] RADII = {0.5, 1.0, 1.5, 2.0, 3.0};

    private static final double[][] REGULAR = {
            {1.0, 1.0}, {3.0, 1.0}, {5.0, 1.0},
            {1.0, 3.0}, {3.0, 3.0}, {5.0, 3.0},
            {1.0, 5.0}, {3.0, 5.0}, {5.0, 5.0}
    };

    private static final double[][] CLUSTERED = {
            {1.0, 1.0}, {1.2, 1.1}, {1.4, 1.3}, {1.1, 1.4},
            {5.0, 5.0}, {5.2, 5.1}, {5.4, 5.3}
    };

    private static final double[][] SPARSE = {
            {0.5, 0.5}, {5.5, 5.5}
    };

    @Test
    public void crossPairCorrelationMatchesItsGolden() throws IOException {
        IJ.resetEscape();
        RectangularWindow window = new RectangularWindow(0.0, 0.0, 6.0, 6.0);
        double[][][] patterns = {REGULAR, CLUSTERED, SPARSE};
        String[] names = {"regular", "clustered", "sparse"};
        EdgeCorrection[] corrections = {
                EdgeCorrection.NONE, EdgeCorrection.TRANSLATION
        };
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < patterns.length; i++) {
            for (int j = 0; j < patterns.length; j++) {
                for (EdgeCorrection correction : corrections) {
                    String key = names[i] + "_to_" + names[j] + "/"
                            + correction.name();
                    curve(text, "crossPCF/" + key,
                            SpatialStatistics.computeCrossPairCorrelation(
                                    patterns[i], patterns[j], window,
                                    RADII, correction));
                    // Pinned alongside so the documented identity -- the cross
                    // form is the annulus derivative of cross-K -- is part of
                    // the record, not just of the prose.
                    curve(text, "crossPCFviaK/" + key,
                            SpatialStatistics.pairCorrelationFromK(
                                    SpatialStatistics.computeCrossK(
                                            patterns[i], patterns[j], window,
                                            RADII, correction),
                                    RADII));
                }
            }
        }

        curve(text, "expected/CROSS_PAIR_CORRELATION/intensity0.25",
                SpatialStatistics.expected(
                        PatternFunction.CROSS_PAIR_CORRELATION, RADII, 0.25));
        curve(text, "expected/CROSS_PAIR_CORRELATION/intensity0",
                SpatialStatistics.expected(
                        PatternFunction.CROSS_PAIR_CORRELATION, RADII, 0.0));

        long[] seeds = {0L, 12345L, 0x0B1EC7L};
        for (long seed : seeds) {
            for (EdgeCorrection correction : corrections) {
                String key = correction.name() + "/seed" + seed;
                monteCarloResult(text, "bivariate/" + key,
                        MonteCarloAnalyzer.analyzeBivariate(
                                PatternFunction.CROSS_PAIR_CORRELATION,
                                REGULAR, CLUSTERED, window, RADII,
                                correction, 9, seed));
                monteCarloResult(text, "bivariate-sparse/" + key,
                        MonteCarloAnalyzer.analyzeBivariate(
                                PatternFunction.CROSS_PAIR_CORRELATION,
                                SPARSE, SPARSE, window, RADII,
                                correction, 9, seed));
            }
        }
        monteCarloResult(text, "undefined/empty-target",
                MonteCarloAnalyzer.analyzeBivariate(
                        PatternFunction.CROSS_PAIR_CORRELATION, REGULAR,
                        new double[0][2], window, RADII,
                        EdgeCorrection.TRANSLATION, 9, 1L));

        reject(text, "border-correction", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.computeCrossPairCorrelation(
                        REGULAR, CLUSTERED,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        RADII, EdgeCorrection.BORDER);
            }
        });
        reject(text, "border-correction-through-null", new Runnable() {
            @Override
            public void run() {
                MonteCarloAnalyzer.analyzeBivariate(
                        PatternFunction.CROSS_PAIR_CORRELATION,
                        REGULAR, CLUSTERED,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        RADII, EdgeCorrection.BORDER, 9, 1L);
            }
        });
        reject(text, "univariate-analysis", new Runnable() {
            @Override
            public void run() {
                MonteCarloAnalyzer.analyzeUnivariate(
                        PatternFunction.CROSS_PAIR_CORRELATION, REGULAR,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        RADII, EdgeCorrection.TRANSLATION, 9, 1L);
            }
        });
        reject(text, "point-outside-window", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.computeCrossPairCorrelation(
                        REGULAR, new double[][]{{99.0, 1.0}},
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        RADII, EdgeCorrection.TRANSLATION);
            }
        });
        reject(text, "decreasing-radii", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.computeCrossPairCorrelation(
                        REGULAR, CLUSTERED,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        new double[]{2.0, 1.0}, EdgeCorrection.TRANSLATION);
            }
        });

        compare(text.toString());
    }

    private static void monteCarloResult(StringBuilder text,
                                         String key,
                                         MonteCarloResult result) {
        text.append("MC ").append(key).append('\n');
        text.append("  status=").append(result.getStatus().name()).append('\n');
        text.append("  seed=").append(result.getSeed()).append('\n');
        text.append("  rankSampleCount=")
                .append(result.getRankSampleCount()).append('\n');
        text.append("  completeEnvelope=")
                .append(result.hasCompletePointwiseEnvelope()).append('\n');
        text.append("  globalP=")
                .append(GoldenDump.hex(result.getGlobalPValue())).append('\n');
        text.append("  maximumDeviation=")
                .append(GoldenDump.hex(result.getMaximumDeviation())).append('\n');
        text.append("  maximumDeviationRadius=")
                .append(GoldenDump.hex(result.getMaximumDeviationRadius()))
                .append('\n');
        curve(text, key + "/observed", result.getObserved());
        curve(text, key + "/expected", result.getExpected());
        curve(text, key + "/lower", result.getLower());
        curve(text, key + "/upper", result.getUpper());
        text.append("N ").append(key).append("/envelopeCounts");
        for (int count : result.getEnvelopeSampleCounts()) {
            text.append(' ').append(count);
        }
        text.append('\n');
    }

    private static void reject(StringBuilder text, String key, Runnable action) {
        try {
            action.run();
            text.append("REJECT ").append(key)
                    .append(" NONE <no exception thrown>\n");
        } catch (RuntimeException rejected) {
            text.append("REJECT ").append(key)
                    .append(' ').append(rejected.getClass().getName())
                    .append(' ').append(GoldenDump.escape(rejected.getMessage()))
                    .append('\n');
        }
    }

    private static void curve(StringBuilder text, String key, double[] values) {
        text.append("C ").append(key).append(' ').append(values.length);
        for (double value : values) {
            text.append(' ').append(GoldenDump.hex(value));
        }
        text.append('\n');
    }

    private static void compare(String actual) throws IOException {
        File golden = new File(GoldenMasterTest.goldenRoot(),
                "_engine-contract-cross-pcf.txt");
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
                assertEquals("cross pair correlation moved at line " + (i + 1),
                        expectedLines[i], actualLines[i]);
            }
        }
        fail("cross pair correlation golden length moved: "
                + expectedLines.length + " -> " + actualLines.length);
    }
}
