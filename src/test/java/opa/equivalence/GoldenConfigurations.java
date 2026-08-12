/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.equivalence;

import ij.ImagePlus;
import sc.fiji.opa.core.CalibrationInfo;
import sc.fiji.opa.core.DistanceMode;
import opa.OPAParameters;
import sc.fiji.opa.core.spatial.EdgeCorrection;
import sc.fiji.opa.core.spatial.PatternFunction;
import sc.fiji.opa.core.spatial.RectangularWindow;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The configuration sweep applied to every corpus case.
 *
 * <p>Each entry varies several axes at once rather than one: the point is to
 * cover the documented option space, not to enumerate its cross product. A
 * configuration that a given case cannot run is not skipped — the rejection it
 * produces is itself recorded in the golden, so every documented rejection is
 * gated alongside every documented output.</p>
 */
final class GoldenConfigurations {

    /**
     * The eight pattern functions that existed when the goldens were captured.
     *
     * <p>Spelled out rather than written {@code EnumSet.allOf(...)}. A golden
     * pins the output of a stated input, and {@code allOf} is not a stated
     * input — it is whatever the enum happens to contain, so adding a constant
     * would silently change what the recorded configuration asked for and move
     * 32 goldens that nothing had actually broken. A new function gets a new
     * configuration and new goldens of its own; it does not edit these.</p>
     */
    /**
     * The five distance modes these goldens were captured over.
     *
     * <p>Not {@code MODES_AT_CAPTURE}, for the same reason the
     * pattern-function set is spelled out: a golden records the output of a
     * <em>stated</em> input, and {@code allOf} is not a stated input — it is
     * whatever the enum happens to hold when the test runs.</p>
     */
    static final EnumSet<DistanceMode> MODES_AT_CAPTURE = EnumSet.of(
            DistanceMode.CENTRE_TO_CENTRE,
            DistanceMode.CENTRE_TO_EDGE,
            DistanceMode.EDGE_TO_CENTRE,
            DistanceMode.EDGE_TO_EDGE,
            DistanceMode.SURFACE_CONTACT);

    static final EnumSet<PatternFunction> FUNCTIONS_AT_CAPTURE = EnumSet.of(
            PatternFunction.K,
            PatternFunction.L,
            PatternFunction.L_MINUS_R,
            PatternFunction.G,
            PatternFunction.PAIR_CORRELATION,
            PatternFunction.CROSS_K,
            PatternFunction.CROSS_L,
            PatternFunction.CROSS_G);

    /**
     * The eight above plus cross pair correlation, added at opa-core 0.2.0.
     *
     * <p>Also spelled out, for the same reason: a tenth function must arrive
     * with its own configuration and its own goldens, not by quietly widening
     * this one.</p>
     */
    static EnumSet<PatternFunction> allNineFunctions() {
        EnumSet<PatternFunction> functions = EnumSet.copyOf(FUNCTIONS_AT_CAPTURE);
        functions.add(PatternFunction.CROSS_PAIR_CORRELATION);
        return functions;
    }

    private GoldenConfigurations() {
    }

    abstract static class Configuration {

        private final String name;
        private final int parallelism;

        Configuration(String name, int parallelism) {
            this.name = name;
            this.parallelism = parallelism;
        }

        String getName() {
            return name;
        }

        int getParallelism() {
            return parallelism;
        }

        abstract void apply(OPAParameters.Builder builder, List<ImagePlus> images);
    }

    static List<Configuration> configurations() {
        List<Configuration> configurations = new ArrayList<Configuration>();

        configurations.add(new Configuration("d01_all_modes_k1_c0", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(true)
                        .runPattern(false)
                        .includeSelfDistances(true)
                        .distanceModes(MODES_AT_CAPTURE)
                        .neighborCount(1)
                        .contactDistance(0.0)
                        .histogramBins(20);
            }
        });

        configurations.add(new Configuration("d02_all_modes_k3_c2_bins3", 4) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(true)
                        .runPattern(false)
                        .includeSelfDistances(true)
                        .distanceModes(MODES_AT_CAPTURE)
                        .neighborCount(3)
                        .contactDistance(2.0)
                        .histogramBins(3);
            }
        });

        configurations.add(new Configuration("d03_centre_only_no_self", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(true)
                        .runPattern(false)
                        .includeSelfDistances(false)
                        .distanceModes(EnumSet.of(DistanceMode.CENTRE_TO_CENTRE))
                        .neighborCount(1)
                        .contactDistance(0.0)
                        .histogramBins(5);
            }
        });

        configurations.add(new Configuration("d04_surface_contact_only_k2", 4) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(true)
                        .runPattern(false)
                        .includeSelfDistances(true)
                        .distanceModes(EnumSet.of(DistanceMode.SURFACE_CONTACT))
                        .neighborCount(2)
                        .contactDistance(1.0)
                        .histogramBins(20);
            }
        });

        configurations.add(new Configuration("d05_edge_modes_c05", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(true)
                        .runPattern(false)
                        .includeSelfDistances(true)
                        .distanceModes(EnumSet.of(
                                DistanceMode.CENTRE_TO_EDGE,
                                DistanceMode.EDGE_TO_CENTRE,
                                DistanceMode.EDGE_TO_EDGE))
                        .neighborCount(1)
                        .contactDistance(0.5)
                        .histogramBins(4);
            }
        });

        configurations.add(new Configuration("d06_require_physical_calibration", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(true)
                        .runPattern(false)
                        .includeSelfDistances(true)
                        .distanceModes(EnumSet.of(DistanceMode.EDGE_TO_EDGE))
                        .neighborCount(1)
                        .contactDistance(0.0)
                        .requirePhysicalCalibration(true);
            }
        });

        configurations.add(new Configuration("p01_k_translation_auto_radii", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(PatternFunction.K))
                        .edgeCorrection(EdgeCorrection.TRANSLATION)
                        .radii(null)
                        .radiusBins(5)
                        .maximumRadius(0.0)
                        .simulations(9)
                        .seed(OPAParameters.DEFAULT_SEED)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("p02_all_univariate_no_correction", 4) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(
                                PatternFunction.K,
                                PatternFunction.L,
                                PatternFunction.L_MINUS_R,
                                PatternFunction.G,
                                PatternFunction.PAIR_CORRELATION))
                        .edgeCorrection(EdgeCorrection.NONE)
                        .radii(new double[]{0.5, 1.0, 2.0})
                        .simulations(5)
                        .seed(42L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("p03_border_correction", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(
                                PatternFunction.K,
                                PatternFunction.L,
                                PatternFunction.G,
                                PatternFunction.CROSS_K))
                        .edgeCorrection(EdgeCorrection.BORDER)
                        .radii(null)
                        .radiusBins(4)
                        .maximumRadius(3.0)
                        .simulations(7)
                        .seed(7L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("p04_cross_only_translation", 4) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(
                                PatternFunction.CROSS_K,
                                PatternFunction.CROSS_L,
                                PatternFunction.CROSS_G))
                        .edgeCorrection(EdgeCorrection.TRANSLATION)
                        .radii(new double[]{1.0, 2.0, 3.0})
                        .simulations(9)
                        .seed(123L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("p05_g_only_one_simulation", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(PatternFunction.G))
                        .edgeCorrection(EdgeCorrection.NONE)
                        .radii(new double[]{1.0})
                        .simulations(1)
                        .seed(0L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("p06_pair_correlation_translation", 4) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(PatternFunction.PAIR_CORRELATION))
                        .edgeCorrection(EdgeCorrection.TRANSLATION)
                        .radii(new double[]{0.5, 1.5, 2.5})
                        .simulations(9)
                        .seed(555L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("p07_pair_correlation_border_rejected", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(
                                PatternFunction.K,
                                PatternFunction.PAIR_CORRELATION))
                        .edgeCorrection(EdgeCorrection.BORDER)
                        .radii(new double[]{1.0, 2.0})
                        .simulations(3)
                        .seed(3L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("p08_no_projection", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(PatternFunction.K, PatternFunction.G))
                        .edgeCorrection(EdgeCorrection.TRANSLATION)
                        .radii(new double[]{1.0, 2.0})
                        .simulations(5)
                        .seed(11L)
                        .project3DToXY(false);
            }
        });

        configurations.add(new Configuration("p09_maximum_radius_explicit", 4) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(
                                PatternFunction.K, PatternFunction.L_MINUS_R))
                        .edgeCorrection(EdgeCorrection.TRANSLATION)
                        .radii(null)
                        .radiusBins(3)
                        .maximumRadius(2.0)
                        .simulations(9)
                        .seed(99L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("p10_cross_pair_correlation", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(
                                PatternFunction.CROSS_PAIR_CORRELATION))
                        .edgeCorrection(EdgeCorrection.TRANSLATION)
                        .radii(new double[]{0.5, 1.5, 2.5})
                        .simulations(9)
                        .seed(606L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("p11_cross_pcf_border_rejected", 4) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(
                                PatternFunction.CROSS_K,
                                PatternFunction.CROSS_PAIR_CORRELATION))
                        .edgeCorrection(EdgeCorrection.BORDER)
                        .radii(new double[]{1.0, 2.0})
                        .simulations(3)
                        .seed(4L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("p12_cross_pcf_no_correction", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(false)
                        .runPattern(true)
                        .patternFunctions(EnumSet.of(
                                PatternFunction.PAIR_CORRELATION,
                                PatternFunction.CROSS_PAIR_CORRELATION))
                        .edgeCorrection(EdgeCorrection.NONE)
                        .radii(new double[]{0.5, 1.0, 2.0})
                        .simulations(5)
                        .seed(42L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("b03_both_all_nine_functions", 4) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(true)
                        .runPattern(true)
                        .includeSelfDistances(true)
                        .distanceModes(MODES_AT_CAPTURE)
                        .neighborCount(2)
                        .contactDistance(1.0)
                        .histogramBins(6)
                        .patternFunctions(allNineFunctions())
                        .edgeCorrection(EdgeCorrection.TRANSLATION)
                        .radii(null)
                        .radiusBins(4)
                        .maximumRadius(0.0)
                        .simulations(9)
                        .seed(2024L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("b01_both_all_functions", 4) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(true)
                        .runPattern(true)
                        .includeSelfDistances(true)
                        .distanceModes(MODES_AT_CAPTURE)
                        .neighborCount(2)
                        .contactDistance(1.0)
                        .histogramBins(6)
                        .patternFunctions(FUNCTIONS_AT_CAPTURE)
                        .edgeCorrection(EdgeCorrection.TRANSLATION)
                        .radii(null)
                        .radiusBins(4)
                        .maximumRadius(0.0)
                        .simulations(9)
                        .seed(2024L)
                        .project3DToXY(true);
            }
        });

        configurations.add(new Configuration("b02_both_observation_window", 1) {
            @Override
            void apply(OPAParameters.Builder builder, List<ImagePlus> images) {
                builder.runDistances(true)
                        .runPattern(true)
                        .includeSelfDistances(false)
                        .distanceModes(EnumSet.of(
                                DistanceMode.CENTRE_TO_CENTRE,
                                DistanceMode.EDGE_TO_EDGE))
                        .neighborCount(1)
                        .contactDistance(0.25)
                        .histogramBins(8)
                        .patternFunctions(EnumSet.of(
                                PatternFunction.K,
                                PatternFunction.G,
                                PatternFunction.CROSS_G))
                        .edgeCorrection(EdgeCorrection.TRANSLATION)
                        .radii(new double[]{0.5, 1.0, 1.5})
                        .simulations(9)
                        .seed(8L)
                        .project3DToXY(true)
                        .observationWindow(innerWindow(images));
            }
        });

        return configurations;
    }

    /**
     * A window one pixel inside the first image, in calibrated coordinates.
     * Returns null when the image is too small to inset, so the configuration
     * degrades to the default full-image window rather than failing to build.
     */
    private static RectangularWindow innerWindow(List<ImagePlus> images) {
        if (images == null || images.isEmpty() || images.get(0) == null) return null;
        ImagePlus image = images.get(0);
        CalibrationInfo calibration;
        try {
            calibration = CalibrationInfo.from(image);
        } catch (RuntimeException rejected) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 4 || height < 4) return null;
        return new RectangularWindow(
                calibration.xEdge(1.0),
                calibration.yEdge(1.0),
                calibration.xEdge(width - 1.0),
                calibration.yEdge(height - 1.0));
    }
}
