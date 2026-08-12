/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.equivalence;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import sc.fiji.opa.core.CalibrationInfo;
import sc.fiji.opa.core.DistanceMode;
import opa.LabelUtils;
import sc.fiji.opa.core.geometry.ChannelGeometry;
import sc.fiji.opa.core.geometry.DirectionResult;
import sc.fiji.opa.core.geometry.LabelGeometryExtractor;
import sc.fiji.opa.core.geometry.NeighborMeasurement;
import sc.fiji.opa.core.geometry.ObjectGeometry;
import sc.fiji.opa.core.geometry.ObjectMeasurement;
import sc.fiji.opa.core.geometry.ProximityEngine;
import sc.fiji.opa.core.spatial.EdgeCorrection;
import sc.fiji.opa.core.spatial.MonteCarloAnalyzer;
import sc.fiji.opa.core.spatial.MonteCarloResult;
import sc.fiji.opa.core.spatial.PatternFunction;
import sc.fiji.opa.core.spatial.RectangularWindow;
import sc.fiji.opa.core.spatial.SpatialStatistics;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Pre-extraction golden for the engine surface itself, called directly.
 *
 * <p>{@link GoldenMasterTest} pins what a user sees. This pins what a
 * <em>consumer</em> sees: the exact numbers and the exact rejection text of
 * the classes that are about to move into {@code opa-core}, reached without
 * going through {@code OPA.run}. Together they mean the extraction is gated
 * from both sides.</p>
 *
 * <p>Same contract as the master golden: every value Tier 1, bit-identical,
 * captured once and never re-recorded.</p>
 */
public class EngineContractGoldenTest {

    private static final double[] RADII = {0.5, 1.0, 1.5, 2.0, 3.0};

    /**
     * The eight functions this golden was captured over.
     *
     * <p>Deliberately not {@code PatternFunction.values()}. The recorded file
     * is the output of a stated set of inputs; iterating the enum would let a
     * later constant rewrite what was asked and move a golden that nothing had
     * broken. New functions get their own golden file.</p>
     */
    private static final PatternFunction[] FUNCTIONS_AT_CAPTURE = {
            PatternFunction.K,
            PatternFunction.L,
            PatternFunction.L_MINUS_R,
            PatternFunction.G,
            PatternFunction.PAIR_CORRELATION,
            PatternFunction.CROSS_K,
            PatternFunction.CROSS_L,
            PatternFunction.CROSS_G
    };

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
    public void engineSurfaceMatchesThePreExtractionGolden() throws IOException {
        IJ.resetEscape();
        StringBuilder text = new StringBuilder();
        spatialStatistics(text);
        monteCarlo(text);
        proximity(text);
        rejections(text);
        compare("_engine-contract.txt", text.toString());
    }

    // ------------------------------------------------------- pure statistics

    private static void spatialStatistics(StringBuilder text) {
        RectangularWindow window = new RectangularWindow(0.0, 0.0, 6.0, 6.0);
        EdgeCorrection[] corrections = {
                EdgeCorrection.NONE,
                EdgeCorrection.BORDER,
                EdgeCorrection.TRANSLATION
        };
        double[][][] patterns = {REGULAR, CLUSTERED, SPARSE};
        String[] names = {"regular", "clustered", "sparse"};

        for (int i = 0; i < patterns.length; i++) {
            double[][] points = patterns[i];
            for (EdgeCorrection correction : corrections) {
                String prefix = "K/" + names[i] + "/" + correction.name();
                double[] k = SpatialStatistics.computeK(
                        points, window, RADII, correction);
                curve(text, prefix, k);
                curve(text, "L/" + names[i] + "/" + correction.name(),
                        SpatialStatistics.computeL(k));
                curve(text, "LmR/" + names[i] + "/" + correction.name(),
                        SpatialStatistics.computeLMinusR(k, RADII));
                if (correction != EdgeCorrection.BORDER) {
                    curve(text, "PCF/" + names[i] + "/" + correction.name(),
                            SpatialStatistics.computePairCorrelation(
                                    points, window, RADII, correction));
                    curve(text, "PCFfromK/" + names[i] + "/" + correction.name(),
                            SpatialStatistics.pairCorrelationFromK(k, RADII));
                }
                for (int j = 0; j < patterns.length; j++) {
                    String cross = names[i] + "_to_" + names[j] + "/"
                            + correction.name();
                    double[] crossK = SpatialStatistics.computeCrossK(
                            points, patterns[j], window, RADII, correction);
                    curve(text, "crossK/" + cross, crossK);
                    curve(text, "crossL/" + cross,
                            SpatialStatistics.computeL(crossK));
                }
            }
            curve(text, "G/" + names[i], SpatialStatistics.computeG(points, RADII));
            for (int j = 0; j < patterns.length; j++) {
                curve(text, "crossG/" + names[i] + "_to_" + names[j],
                        SpatialStatistics.computeCrossG(points, patterns[j], RADII));
            }
        }

        for (PatternFunction function : FUNCTIONS_AT_CAPTURE) {
            curve(text, "expected/" + function.name() + "/intensity0.25",
                    SpatialStatistics.expected(function, RADII, 0.25));
            curve(text, "expected/" + function.name() + "/intensity0",
                    SpatialStatistics.expected(function, RADII, 0.0));
        }
    }

    // ------------------------------------------------------------ null model

    private static void monteCarlo(StringBuilder text) {
        RectangularWindow window = new RectangularWindow(0.0, 0.0, 6.0, 6.0);
        long[] seeds = {0L, 12345L, 0x0B1EC7L};
        EdgeCorrection[] corrections = {
                EdgeCorrection.NONE, EdgeCorrection.TRANSLATION
        };

        for (long seed : seeds) {
            for (EdgeCorrection correction : corrections) {
                for (PatternFunction function : FUNCTIONS_AT_CAPTURE) {
                    if (function == PatternFunction.PAIR_CORRELATION
                            && correction == EdgeCorrection.BORDER) {
                        continue;
                    }
                    String key = function.name() + "/" + correction.name()
                            + "/seed" + seed;
                    if (function.isBivariate()) {
                        monteCarloResult(text, "bivariate/" + key,
                                MonteCarloAnalyzer.analyzeBivariate(
                                        function, REGULAR, CLUSTERED, window,
                                        RADII, correction, 9, seed));
                        monteCarloResult(text, "bivariate-sparse/" + key,
                                MonteCarloAnalyzer.analyzeBivariate(
                                        function, SPARSE, SPARSE, window,
                                        RADII, correction, 9, seed));
                    } else {
                        monteCarloResult(text, "univariate/" + key,
                                MonteCarloAnalyzer.analyzeUnivariate(
                                        function, REGULAR, window,
                                        RADII, correction, 9, seed));
                        monteCarloResult(text, "univariate-clustered/" + key,
                                MonteCarloAnalyzer.analyzeUnivariate(
                                        function, CLUSTERED, window,
                                        RADII, correction, 9, seed));
                    }
                }
            }
        }

        // A single point cannot support a univariate estimate; a bivariate one
        // with an empty partner cannot either. Both are documented states.
        monteCarloResult(text, "undefined/univariate",
                MonteCarloAnalyzer.analyzeUnivariate(
                        PatternFunction.K, new double[][]{{1.0, 1.0}}, window,
                        RADII, EdgeCorrection.NONE, 9, 1L));
        monteCarloResult(text, "undefined/bivariate",
                MonteCarloAnalyzer.analyzeBivariate(
                        PatternFunction.CROSS_K, REGULAR, new double[0][2],
                        window, RADII, EdgeCorrection.NONE, 9, 1L));
    }

    private static void monteCarloResult(StringBuilder text,
                                         String key,
                                         MonteCarloResult result) {
        text.append("MC ").append(key).append('\n');
        text.append("  function=").append(result.getFunction().name()).append('\n');
        text.append("  status=").append(result.getStatus().name()).append('\n');
        text.append("  simulations=").append(result.getSimulations()).append('\n');
        text.append("  seed=").append(result.getSeed()).append('\n');
        text.append("  rankSampleCount=")
                .append(result.getRankSampleCount()).append('\n');
        text.append("  completeEnvelope=")
                .append(result.hasCompletePointwiseEnvelope()).append('\n');
        text.append("  globalP=")
                .append(GoldenDump.hex(result.getGlobalPValue())).append('\n');
        text.append("  minimumAchievableP=")
                .append(GoldenDump.hex(result.getMinimumAchievablePValue()))
                .append('\n');
        text.append("  maximumDeviation=")
                .append(GoldenDump.hex(result.getMaximumDeviation())).append('\n');
        text.append("  maximumDeviationRadius=")
                .append(GoldenDump.hex(result.getMaximumDeviationRadius()))
                .append('\n');
        curve(text, key + "/radii", result.getRadii());
        curve(text, key + "/observed", result.getObserved());
        curve(text, key + "/expected", result.getExpected());
        curve(text, key + "/lower", result.getLower());
        curve(text, key + "/upper", result.getUpper());
        int[] counts = result.getEnvelopeSampleCounts();
        text.append("N ").append(key).append("/envelopeCounts");
        for (int count : counts) text.append(' ').append(count);
        text.append('\n');
    }

    // --------------------------------------------------------- label geometry

    private static void proximity(StringBuilder text) {
        ImagePlus a = GoldenCorpus.calibrated(
                GoldenCorpus.image("A", 12, 12, 1, 16));
        GoldenCorpus.box(a, 1, 1, 0, 3, 3, 1, 1);
        GoldenCorpus.box(a, 6, 1, 0, 2, 2, 1, 2);
        GoldenCorpus.box(a, 1, 8, 0, 2, 3, 1, 3);
        ImagePlus b = GoldenCorpus.calibrated(
                GoldenCorpus.image("B", 12, 12, 1, 16));
        GoldenCorpus.box(b, 4, 1, 0, 2, 3, 1, 1);
        GoldenCorpus.box(b, 8, 8, 0, 3, 3, 1, 2);

        ImagePlus c = GoldenCorpus.calibrate(
                GoldenCorpus.image("C", 8, 8, 3, 16), 0.2, 0.2, 1.0, "micron");
        GoldenCorpus.box(c, 1, 1, 0, 2, 2, 2, 1);
        GoldenCorpus.box(c, 4, 4, 1, 2, 2, 2, 2);
        ImagePlus d = GoldenCorpus.calibrate(
                GoldenCorpus.image("D", 8, 8, 3, 16), 0.2, 0.2, 1.0, "micron");
        GoldenCorpus.box(d, 3, 1, 0, 2, 2, 3, 1);

        ChannelGeometry first = LabelGeometryExtractor.extract(a, "A");
        ChannelGeometry second = LabelGeometryExtractor.extract(b, "B");
        ChannelGeometry third = LabelGeometryExtractor.extract(c, "C");
        ChannelGeometry fourth = LabelGeometryExtractor.extract(d, "D");

        channel(text, first);
        channel(text, second);
        channel(text, third);
        channel(text, fourth);
        channel(text, first.within(new RectangularWindow(0.5, 0.5, 5.0, 5.0)));

        double[] contacts = {0.0, 0.5, 2.0};
        for (double contact : contacts) {
            direction(text, "2d/A-B/c" + contact, ProximityEngine.analyze(
                    first, second, GoldenConfigurations.MODES_AT_CAPTURE, 2, contact));
            direction(text, "2d/A-A/c" + contact, ProximityEngine.analyze(
                    first, first, GoldenConfigurations.MODES_AT_CAPTURE, 2, contact));
            direction(text, "3d/C-D/c" + contact, ProximityEngine.analyze(
                    third, fourth, GoldenConfigurations.MODES_AT_CAPTURE, 1, contact));
        }
        direction(text, "2d/A-B/single-mode", ProximityEngine.analyze(
                first, second, EnumSet.of(DistanceMode.SURFACE_CONTACT), 1, 1.0));
    }

    private static void channel(StringBuilder text, ChannelGeometry channel) {
        text.append("CH ").append(GoldenDump.escape(channel.getName()))
                .append(" w=").append(channel.getWidth())
                .append(" h=").append(channel.getHeight())
                .append(" d=").append(channel.getDepth())
                .append(" n=").append(channel.getObjects().size())
                .append(" unit=")
                .append(GoldenDump.escape(channel.getCalibration().getUnit()))
                .append(" physical=")
                .append(channel.getCalibration().hasPhysicalUnits())
                .append('\n');
        for (ObjectGeometry object : channel.getObjects()) {
            text.append("  OBJ ").append(object.getLabel())
                    .append(" voxels=").append(object.getVoxelCount())
                    .append(" edge=").append(object.isEdgeObject())
                    .append(" cx=").append(GoldenDump.hex(object.getCentroidX()))
                    .append(" cy=").append(GoldenDump.hex(object.getCentroidY()))
                    .append(" cz=").append(GoldenDump.hex(object.getCentroidZ()))
                    .append(" area=").append(GoldenDump.hex(object.getSurfaceArea()))
                    .append('\n');
        }
        double[][] points = channel.centroidPoints2D();
        text.append("  POINTS ").append(points.length);
        for (double[] point : points) {
            text.append(' ').append(GoldenDump.hex(point[0]))
                    .append(':').append(GoldenDump.hex(point[1]));
        }
        text.append('\n');
    }

    private static void direction(StringBuilder text,
                                  String key,
                                  DirectionResult result) {
        text.append("DIR ").append(key)
                .append(" source=").append(GoldenDump.escape(result.getSourceChannel()))
                .append(" target=").append(GoldenDump.escape(result.getTargetChannel()))
                .append(" self=").append(result.isSelfComparison())
                .append(" unit=").append(GoldenDump.escape(result.getUnit()))
                .append(" surfaceUnit=")
                .append(GoldenDump.escape(result.getSurfaceMeasureUnit()))
                .append(" sourceN=").append(result.getSourceObjectCount())
                .append(" targetN=").append(result.getTargetObjectCount())
                .append('\n');
        for (ObjectMeasurement measurement : result.getMeasurements()) {
            text.append("  SRC ").append(measurement.getSourceLabel())
                    .append(" edge=").append(measurement.isEdgeObject())
                    .append('\n');
            for (DistanceMode mode : GoldenConfigurations.MODES_AT_CAPTURE) {
                List<NeighborMeasurement> neighbors =
                        measurement.getNeighbors(mode);
                for (NeighborMeasurement neighbor : neighbors) {
                    text.append("    ").append(mode.getColumnName())
                            .append(" rank=").append(neighbor.getRank())
                            .append(" partner=").append(neighbor.getPartnerLabel())
                            .append(" value=")
                            .append(GoldenDump.hex(neighbor.getValue()))
                            .append(" within=")
                            .append(neighbor.isWithinContactDistance())
                            .append(" exact=")
                            .append(GoldenDump.hex(neighbor.getExactContactArea()))
                            .append(" apposed=")
                            .append(GoldenDump.hex(neighbor.getApposedSurfaceArea()))
                            .append('\n');
                }
            }
        }
    }

    // -------------------------------------------------------- rejection text

    private static void rejections(StringBuilder text) {
        RectangularWindow window = new RectangularWindow(0.0, 0.0, 6.0, 6.0);
        double[] good = {1.0, 2.0};

        reject(text, "K/null-window", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.computeK(REGULAR, null, RADII,
                        EdgeCorrection.NONE);
            }
        });
        reject(text, "K/null-correction", runK(REGULAR, window, RADII, null));
        reject(text, "K/null-radii", runK(REGULAR, window, null,
                EdgeCorrection.NONE));
        reject(text, "K/empty-radii", runK(REGULAR, window, new double[0],
                EdgeCorrection.NONE));
        reject(text, "K/negative-radius",
                runK(REGULAR, window, new double[]{-1.0}, EdgeCorrection.NONE));
        reject(text, "K/infinite-radius",
                runK(REGULAR, window, new double[]{Double.POSITIVE_INFINITY},
                        EdgeCorrection.NONE));
        reject(text, "K/decreasing-radii",
                runK(REGULAR, window, new double[]{2.0, 1.0},
                        EdgeCorrection.NONE));
        reject(text, "K/repeated-radii",
                runK(REGULAR, window, new double[]{1.0, 1.0},
                        EdgeCorrection.NONE));
        reject(text, "K/null-points",
                runK(null, window, good, EdgeCorrection.NONE));
        reject(text, "K/non-finite-point",
                runK(new double[][]{{Double.NaN, 0.0}}, window, good,
                        EdgeCorrection.NONE));
        reject(text, "K/short-point",
                runK(new double[][]{{1.0}}, window, good, EdgeCorrection.NONE));
        reject(text, "K/point-outside-window",
                runK(new double[][]{{99.0, 1.0}}, window, good,
                        EdgeCorrection.NONE));

        reject(text, "crossK/target-outside-window", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.computeCrossK(REGULAR,
                        new double[][]{{99.0, 1.0}},
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        new double[]{1.0}, EdgeCorrection.NONE);
            }
        });
        reject(text, "pcf/border-correction", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.computePairCorrelation(REGULAR,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        new double[]{1.0, 2.0}, EdgeCorrection.BORDER);
            }
        });
        reject(text, "pcf/decreasing-k", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.pairCorrelationFromK(
                        new double[]{5.0, 1.0}, new double[]{1.0, 2.0});
            }
        });
        reject(text, "pcf/negative-k", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.pairCorrelationFromK(
                        new double[]{-1.0, 1.0}, new double[]{1.0, 2.0});
            }
        });
        reject(text, "pcf/mismatched-lengths", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.pairCorrelationFromK(
                        new double[]{1.0}, new double[]{1.0, 2.0});
            }
        });
        reject(text, "L/null-k", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.computeL(null);
            }
        });
        reject(text, "LmR/mismatched-lengths", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.computeLMinusR(
                        new double[]{1.0}, new double[]{1.0, 2.0});
            }
        });
        reject(text, "expected/null-function", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.expected(null, new double[]{1.0}, 1.0);
            }
        });
        reject(text, "expected/negative-intensity", new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.expected(
                        PatternFunction.G, new double[]{1.0}, -1.0);
            }
        });

        reject(text, "mc/univariate-with-bivariate-function", new Runnable() {
            @Override
            public void run() {
                MonteCarloAnalyzer.analyzeUnivariate(
                        PatternFunction.CROSS_K, REGULAR,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        new double[]{1.0}, EdgeCorrection.NONE, 9, 1L);
            }
        });
        reject(text, "mc/bivariate-with-univariate-function", new Runnable() {
            @Override
            public void run() {
                MonteCarloAnalyzer.analyzeBivariate(
                        PatternFunction.K, REGULAR, CLUSTERED,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        new double[]{1.0}, EdgeCorrection.NONE, 9, 1L);
            }
        });
        reject(text, "mc/zero-simulations", new Runnable() {
            @Override
            public void run() {
                MonteCarloAnalyzer.analyzeUnivariate(
                        PatternFunction.K, REGULAR,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        new double[]{1.0}, EdgeCorrection.NONE, 0, 1L);
            }
        });
        reject(text, "mc/too-many-simulations", new Runnable() {
            @Override
            public void run() {
                MonteCarloAnalyzer.analyzeUnivariate(
                        PatternFunction.K, REGULAR,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        new double[]{1.0}, EdgeCorrection.NONE, 10001, 1L);
            }
        });
        reject(text, "mc/null-radii", new Runnable() {
            @Override
            public void run() {
                MonteCarloAnalyzer.analyzeUnivariate(
                        PatternFunction.K, REGULAR,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        null, EdgeCorrection.NONE, 9, 1L);
            }
        });
        reject(text, "mc/empty-radii", new Runnable() {
            @Override
            public void run() {
                MonteCarloAnalyzer.analyzeUnivariate(
                        PatternFunction.K, REGULAR,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        new double[0], EdgeCorrection.NONE, 9, 1L);
            }
        });
        reject(text, "mc/work-budget", new Runnable() {
            @Override
            public void run() {
                double[] radii = new double[10000];
                for (int i = 0; i < radii.length; i++) radii[i] = i + 1.0;
                MonteCarloAnalyzer.analyzeUnivariate(
                        PatternFunction.K, REGULAR,
                        new RectangularWindow(0.0, 0.0, 6.0, 6.0),
                        radii, EdgeCorrection.NONE, 10000, 1L);
            }
        });

        reject(text, "window/zero-area", new Runnable() {
            @Override
            public void run() {
                new RectangularWindow(0.0, 0.0, 0.0, 6.0);
            }
        });
        reject(text, "window/inverted", new Runnable() {
            @Override
            public void run() {
                new RectangularWindow(6.0, 0.0, 0.0, 6.0);
            }
        });
        reject(text, "window/non-finite", new Runnable() {
            @Override
            public void run() {
                new RectangularWindow(0.0, 0.0, Double.NaN, 6.0);
            }
        });

        reject(text, "calibration/null-image", new Runnable() {
            @Override
            public void run() {
                CalibrationInfo.from(null);
            }
        });
        reject(text, "calibration/zero-pixel-height", new Runnable() {
            @Override
            public void run() {
                ImagePlus image = GoldenCorpus.image("z", 4, 4, 1, 8);
                GoldenCorpus.calibrate(image, 1.0, 0.0, 1.0, "micron");
                CalibrationInfo.from(image);
            }
        });
        reject(text, "calibration/negative-pixel-depth", new Runnable() {
            @Override
            public void run() {
                ImagePlus image = GoldenCorpus.image("z", 4, 4, 1, 8);
                GoldenCorpus.calibrate(image, 1.0, 1.0, -1.0, "micron");
                CalibrationInfo.from(image);
            }
        });
        reject(text, "calibration/non-finite-origin", new Runnable() {
            @Override
            public void run() {
                ImagePlus image = GoldenCorpus.image("z", 4, 4, 1, 8);
                GoldenCorpus.calibrate(image, 1.0, 1.0, 1.0, "micron");
                GoldenCorpus.origin(image, Double.NaN, 0.0, 0.0);
                CalibrationInfo.from(image);
            }
        });

        reject(text, "extract/null-image", new Runnable() {
            @Override
            public void run() {
                LabelGeometryExtractor.extract(null, "x");
            }
        });
        reject(text, "extract/hyperstack", new Runnable() {
            @Override
            public void run() {
                ImagePlus image = GoldenCorpus.image("h", 4, 4, 2, 8);
                image.setDimensions(2, 1, 1);
                LabelGeometryExtractor.extract(image, "h");
            }
        });
        reject(text, "extract/non-integer-label", new Runnable() {
            @Override
            public void run() {
                ImagePlus image = GoldenCorpus.image("f", 4, 4, 1, 32);
                image.getStack().getProcessor(1).setf(1, 1, 2.5f);
                LabelGeometryExtractor.extract(image, "f");
            }
        });
        reject(text, "extract/negative-label", new Runnable() {
            @Override
            public void run() {
                ImagePlus image = GoldenCorpus.image("f", 4, 4, 1, 32);
                image.getStack().getProcessor(1).setf(2, 3, -7.0f);
                LabelGeometryExtractor.extract(image, "f");
            }
        });
        reject(text, "extract/infinite-label", new Runnable() {
            @Override
            public void run() {
                ImagePlus image = GoldenCorpus.image("f", 4, 4, 1, 32);
                image.getStack().getProcessor(1)
                        .setf(0, 0, Float.POSITIVE_INFINITY);
                LabelGeometryExtractor.extract(image, "f");
            }
        });

        reject(text, "proximity/null-source", new Runnable() {
            @Override
            public void run() {
                ProximityEngine.analyze(null, null,
                        GoldenConfigurations.MODES_AT_CAPTURE, 1, 0.0);
            }
        });
        reject(text, "proximity/mismatched-dimensions", new Runnable() {
            @Override
            public void run() {
                ProximityEngine.analyze(
                        geometry(4, 4, 1), geometry(6, 6, 1),
                        GoldenConfigurations.MODES_AT_CAPTURE, 1, 0.0);
            }
        });
        reject(text, "proximity/no-modes", new Runnable() {
            @Override
            public void run() {
                ProximityEngine.analyze(
                        geometry(4, 4, 1), geometry(4, 4, 1),
                        EnumSet.noneOf(DistanceMode.class), 1, 0.0);
            }
        });
        reject(text, "proximity/zero-neighbours", new Runnable() {
            @Override
            public void run() {
                ProximityEngine.analyze(
                        geometry(4, 4, 1), geometry(4, 4, 1),
                        GoldenConfigurations.MODES_AT_CAPTURE, 0, 0.0);
            }
        });
        reject(text, "proximity/negative-contact", new Runnable() {
            @Override
            public void run() {
                ProximityEngine.analyze(
                        geometry(4, 4, 1), geometry(4, 4, 1),
                        GoldenConfigurations.MODES_AT_CAPTURE, 1, -1.0);
            }
        });

        reject(text, "roi/line-selection", new Runnable() {
            @Override
            public void run() {
                ImagePlus reference = GoldenCorpus.calibrated(
                        GoldenCorpus.image("r", 8, 8, 1, 8));
                LabelUtils.roiSetToLabelImage(reference,
                        new Roi[]{new Line(0, 0, 4, 4)});
            }
        });
        reject(text, "roi/null-entry", new Runnable() {
            @Override
            public void run() {
                ImagePlus reference = GoldenCorpus.calibrated(
                        GoldenCorpus.image("r", 8, 8, 1, 8));
                LabelUtils.roiSetToLabelImage(reference, new Roi[]{null});
            }
        });
        reject(text, "roi/overlapping", new Runnable() {
            @Override
            public void run() {
                ImagePlus reference = GoldenCorpus.calibrated(
                        GoldenCorpus.image("r", 8, 8, 1, 8));
                LabelUtils.roiSetToLabelImage(reference, new Roi[]{
                        new Roi(1, 1, 3, 3), new Roi(2, 2, 3, 3)});
            }
        });
        reject(text, "roi/out-of-bounds-z", new Runnable() {
            @Override
            public void run() {
                ImagePlus reference = GoldenCorpus.calibrated(
                        GoldenCorpus.image("r", 8, 8, 2, 8));
                Roi roi = new Roi(1, 1, 3, 3);
                roi.setPosition(0, 5, 0);
                LabelUtils.roiSetToLabelImage(reference, new Roi[]{roi});
            }
        });
        reject(text, "roi/covers-nothing", new Runnable() {
            @Override
            public void run() {
                ImagePlus reference = GoldenCorpus.calibrated(
                        GoldenCorpus.image("r", 8, 8, 1, 8));
                LabelUtils.roiSetToLabelImage(reference,
                        new Roi[]{new OvalRoi(20, 20, 3, 3)});
            }
        });
        reject(text, "roi/null-reference", new Runnable() {
            @Override
            public void run() {
                LabelUtils.roiSetToLabelImage(null, new Roi[0]);
            }
        });
        reject(text, "roi/null-array", new Runnable() {
            @Override
            public void run() {
                LabelUtils.roiSetToLabelImage(GoldenCorpus.calibrated(
                        GoldenCorpus.image("r", 8, 8, 1, 8)), null);
            }
        });
        reject(text, "window/no-region-rois", new Runnable() {
            @Override
            public void run() {
                LabelUtils.boundingWindow(GoldenCorpus.calibrated(
                        GoldenCorpus.image("r", 8, 8, 1, 8)), new Roi[0]);
            }
        });
        reject(text, "window/line-region-roi", new Runnable() {
            @Override
            public void run() {
                LabelUtils.boundingWindow(GoldenCorpus.calibrated(
                                GoldenCorpus.image("r", 8, 8, 1, 8)),
                        new Roi[]{new Line(0, 0, 4, 4)});
            }
        });
    }

    private static Runnable runK(final double[][] points,
                                 final RectangularWindow window,
                                 final double[] radii,
                                 final EdgeCorrection correction) {
        return new Runnable() {
            @Override
            public void run() {
                SpatialStatistics.computeK(points, window, radii, correction);
            }
        };
    }

    private static ChannelGeometry geometry(int width, int height, int depth) {
        ImagePlus image = GoldenCorpus.calibrated(
                GoldenCorpus.image("g", width, height, depth, 8));
        GoldenCorpus.set(image, 1, 1, 0, 1);
        return LabelGeometryExtractor.extract(image, "g");
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

    private static void compare(String name, String actual) throws IOException {
        File golden = new File(GoldenMasterTest.goldenRoot(), name);
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
                assertEquals("engine contract moved at line " + (i + 1),
                        expectedLines[i], actualLines[i]);
            }
        }
        fail("engine contract length moved: " + expectedLines.length
                + " -> " + actualLines.length);
    }
}
