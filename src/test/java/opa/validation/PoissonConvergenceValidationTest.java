/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.validation;

import org.junit.Test;
import sc.fiji.opa.core.spatial.EdgeCorrection;
import sc.fiji.opa.core.spatial.RectangularWindow;
import sc.fiji.opa.core.spatial.SpatialStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertTrue;

/**
 * Validation stage V1: the estimators converge to their analytic expectations
 * under complete spatial randomness.
 *
 * <p>For each of two windows (1:1 and 4:1, equal area) and three point counts,
 * independent binomial patterns (a fixed n per realisation, which is the null
 * the plugin's own Monte Carlo simulates) are drawn and every estimator is
 * averaged across realisations. The mean must lie within three standard
 * errors of its expectation at every radius (the assertion itself is
 * Bonferroni-corrected for multiplicity; see the summary block):</p>
 *
 * <ul>
 *   <li>K and cross-K, translation correction, and cross-K, border
 *       correction: pi r^2. These are exactly unbiased under a binomial
 *       process.</li>
 *   <li>Univariate K, border correction: pi r^2, with an allowance of 2/n
 *       (relative) for a known ratio bias. The reduced-sample estimator divides
 *       the pairs found around eligible points by the number of eligible
 *       points, and a point near an eligible one is itself more likely to be
 *       eligible, so numerator and denominator covary and the ratio runs low by
 *       an amount of order 1/n that grows with radius (V1_FINDINGS.md; also
 *       V3_FINDINGS.md). Cross-K border has no such term because the target
 *       pattern does not decide eligibility.</li>
 *   <li>Pair correlation, translation: 1. It is a linear function of
 *       increments of translation K, so it inherits K's unbiasedness.</li>
 *   <li>L = sqrt(K / pi): r, less the second-order Jensen term
 *       Var(K) / (8 pi^2 r^3). L is a concave transform of an unbiased
 *       estimator, so its mean sits below r by exactly that much to second
 *       order; testing the raw mean against r would fail at a thousand
 *       realisations for a reason that is arithmetic, not a defect. The raw
 *       deviation from r is reported alongside. Asserted only where the
 *       relative spread of K is at most 0.3, where the expansion holds.
 *       L(r) - r is L shifted by a constant, so its z-scores are L's.</li>
 *   <li>G and cross-G: these are uncorrected by design, so under CSR they are
 *       biased low near the window edge. They are asserted against their
 *       exact expectation for the uncorrected estimator on this window,
 *       {@code 1 - (1/|W|) integral (1 - |b(x,r) & W| / |W|)^m dx}, computed by
 *       quadrature, and their residual bias against the textbook
 *       {@code 1 - exp(-lambda pi r^2)} is reported as a number. Radii stop at
 *       0.1 x the shorter window side.</li>
 * </ul>
 *
 * <p>Fast mode (default) runs 50 realisations; {@code -Dopa.validation=true}
 * runs 1,000. Report: {@code target/validation/v1-report.txt}.</p>
 */
public class PoissonConvergenceValidationTest {

    private static final double Z_LIMIT = 3.0;
    private static final double FAMILY_ALPHA = 0.01;
    private static final double JENSEN_VALIDITY = 0.3;
    private static final double G_SATURATION = 0.99;
    private static final int[] POINT_COUNTS = {50, 150, 400};
    private static final RectangularWindow[] WINDOWS = {
            new RectangularWindow(0.0, 0.0, 1000.0, 1000.0),
            new RectangularWindow(0.0, 0.0, 2000.0, 500.0)
    };
    private static final String[] WINDOW_NAMES = {"1:1 1000x1000", "4:1 2000x500"};

    // Curve slots in one realisation's result.
    private static final int K_T = 0, K_B = 1, L_T = 2, L_B = 3, CK_T = 4,
            CK_B = 5, PCF_T = 6, G = 7, CG = 8, CURVES = 9;
    private static final String[] CURVE_NAMES = {
            "K / translation", "K / border", "L / translation", "L / border",
            "cross-K / translation", "cross-K / border",
            "pair correlation / translation", "G / uncorrected",
            "cross-G / uncorrected"};

    @Test
    public void estimatorsConvergeToPoissonExpectations() {
        final int realisations = ValidationSupport.realisations(50, 1000);
        StringBuilder report = new StringBuilder();
        report.append("V1 - convergence to analytic Poisson expectations\n");
        report.append(ValidationSupport.format(
                "mode=%s realisations=%d seed=%d z-limit=%.1f%n",
                ValidationSupport.mode(), realisations, ValidationSupport.SEED,
                Z_LIMIT));
        report.append("Binomial process (fixed n), independent second pattern "
                + "of the same n for the cross functions.\n\n");

        List<String> exceedances = new ArrayList<String>();
        List<Double> zScores = new ArrayList<Double>();
        List<String> locations = new ArrayList<String>();
        List<double[]> deviations = new ArrayList<double[]>();
        int checks = 0;
        int beyondTwo = 0;
        double worstZ = 0.0;
        String worstWhere = "";
        int caseIndex = 0;
        for (int w = 0; w < WINDOWS.length; w++) {
            for (int n : POINT_COUNTS) {
                final RectangularWindow window = WINDOWS[w];
                final int count = n;
                final double shortSide = Math.min(window.width(), window.height());
                final double[] kRadii = scaled(shortSide, 0.02);
                final double[] gRadii = scaled(shortSide, 0.01);
                final long index = caseIndex++;

                List<Callable<double[][]>> tasks = new ArrayList<Callable<double[][]>>();
                for (int i = 0; i < realisations; i++) {
                    final long realisation = i;
                    tasks.add(new Callable<double[][]>() {
                        @Override
                        public double[][] call() {
                            return realisation(new Random(ValidationSupport.seed(
                                    index, realisation)), count, window, kRadii, gRadii);
                        }
                    });
                }
                List<double[][]> results = ValidationSupport.runAll(tasks);

                double lambda = count / window.area();
                report.append(ValidationSupport.format(
                        "=== window %s, n=%d, lambda=%.3e ===%n",
                        WINDOW_NAMES[w], count, lambda));
                for (int curve = 0; curve < CURVES; curve++) {
                    boolean nearest = curve == G || curve == CG;
                    double[] radii = nearest ? gRadii : kRadii;
                    double[][] rows = column(results, curve);
                    double[][] kRows = curve == L_T ? column(results, K_T)
                            : curve == L_B ? column(results, K_B) : null;
                    report.append("--- ").append(CURVE_NAMES[curve]).append('\n');
                    report.append(nearest
                            ? "radius      exact_E     mean        SE          z       csr_formula bias_vs_csr\n"
                            : curve == L_T || curve == L_B
                            ? "radius      E_2nd_order mean        SE          z       raw_dev_r   rel_sd_K\n"
                            : "radius      expected    mean        SE          z\n");
                    for (int r = 0; r < radii.length; r++) {
                        double radius = radii[r];
                        double mean = ValidationSupport.mean(rows, r);
                        double se = ValidationSupport.standardError(rows, r);
                        double expected;
                        boolean assessed = true;
                        String extra = "";
                        if (curve == K_T || curve == K_B || curve == CK_T || curve == CK_B) {
                            expected = Math.PI * radius * radius;
                        } else if (curve == PCF_T) {
                            expected = 1.0;
                        } else if (curve == L_T || curve == L_B) {
                            double varianceK = ValidationSupport.variance(kRows, r);
                            double relativeSd = Math.sqrt(varianceK)
                                    / (Math.PI * radius * radius);
                            expected = radius - varianceK
                                    / (8.0 * Math.PI * Math.PI * radius * radius * radius);
                            assessed = relativeSd <= JENSEN_VALIDITY;
                            extra = ValidationSupport.format(" %11.4e %8.4f%s",
                                    mean - radius, relativeSd,
                                    assessed ? "" : "  not assessed");
                        } else {
                            int others = curve == G ? count - 1 : count;
                            expected = expectedUncorrectedNearest(window, radius, others);
                            double csr = 1.0 - Math.exp(-lambda * Math.PI * radius * radius);
                            // Past saturation every realisation reads 1 and the
                            // spread is zero: nothing left to test. The engine's
                            // saturation radius uses the same 0.99 threshold.
                            assessed = expected <= G_SATURATION;
                            extra = ValidationSupport.format(" %11.6f %11.4e%s",
                                    csr, mean - csr, assessed ? "" : "  saturated");
                        }
                        double z = se > 0.0 ? (mean - expected) / se
                                : mean == expected ? 0.0 : Double.POSITIVE_INFINITY;
                        report.append(ValidationSupport.format(
                                "%-11.4f %-11.6g %-11.6g %-11.4e %7.3f%s%n",
                                radius, expected, mean, se, z, extra));
                        if (!assessed) continue;
                        // Allowance for the univariate border ratio bias, as a
                        // fraction of the expectation (L is a square root, so
                        // half as much).
                        double slack = curve == K_B ? 2.0 / count
                                : curve == L_B ? 1.0 / count : 0.0;
                        deviations.add(new double[]{
                                Math.abs(mean - expected), se, slack * Math.abs(expected)});
                        checks++;
                        if (Math.abs(z) > 2.0) beyondTwo++;
                        String where = ValidationSupport.format(
                                "%s n=%d %s r=%.4f z=%.3f",
                                WINDOW_NAMES[w], count, CURVE_NAMES[curve], radius, z);
                        if (Math.abs(z) > Math.abs(worstZ)) {
                            worstZ = z;
                            worstWhere = where;
                        }
                        if (!(Math.abs(z) <= Z_LIMIT)) exceedances.add(where);
                        zScores.add(z);
                        locations.add(where);
                    }
                }
                report.append('\n');
            }
        }
        // The per-radius line is 3 SE, as the validation plan specifies. With
        // several hundred checks, a handful of 3 SE exceedances are expected
        // by chance alone (0.27% each), so the assertion is the Bonferroni
        // bound for a family-wise error of 1%; every 3 SE exceedance is still
        // listed in the report and written up in V1_FINDINGS.md.
        double bonferroni = ValidationSupport.twoSidedNormalQuantile(
                FAMILY_ALPHA / checks);
        List<String> failures = new ArrayList<String>();
        for (int i = 0; i < deviations.size(); i++) {
            double[] d = deviations.get(i);
            if (!(d[0] <= bonferroni * d[1] + d[2])) failures.add(locations.get(i));
        }
        report.append(ValidationSupport.format(
                "SUMMARY checks=%d |z|>2: %d (%.1f expected if independent) "
                        + "|z|>%.0f: %d (%.1f expected) "
                        + "Bonferroni bound (1%% family-wise): %.3f%n"
                        + "worst: %s%n",
                checks, beyondTwo, checks * 0.0455, Z_LIMIT, exceedances.size(),
                checks * 0.0027, bonferroni, worstWhere));
        for (String e : exceedances) report.append("BEYOND_3SE ").append(e).append('\n');
        for (String failure : failures) report.append("FAIL ").append(failure).append('\n');
        ValidationSupport.writeReport("v1-report.txt", report);
        assertTrue("V1 estimates beyond the Bonferroni bound " + bonferroni + ": "
                + failures, failures.isEmpty());
    }

    private static double[][] realisation(Random random, int count,
                                          RectangularWindow window,
                                          double[] kRadii, double[] gRadii) {
        double[][] source = PointProcesses.csr(random, count, window);
        double[][] target = PointProcesses.csr(random, count, window);
        double[][] curves = new double[CURVES][];
        curves[K_T] = SpatialStatistics.computeK(
                source, window, kRadii, EdgeCorrection.TRANSLATION);
        curves[K_B] = SpatialStatistics.computeK(
                source, window, kRadii, EdgeCorrection.BORDER);
        curves[L_T] = SpatialStatistics.computeL(curves[K_T]);
        curves[L_B] = SpatialStatistics.computeL(curves[K_B]);
        curves[CK_T] = SpatialStatistics.computeCrossK(
                source, target, window, kRadii, EdgeCorrection.TRANSLATION);
        curves[CK_B] = SpatialStatistics.computeCrossK(
                source, target, window, kRadii, EdgeCorrection.BORDER);
        curves[PCF_T] = SpatialStatistics.pairCorrelationFromK(curves[K_T], kRadii);
        curves[G] = SpatialStatistics.computeG(source, gRadii);
        curves[CG] = SpatialStatistics.computeCrossG(source, target, gRadii);
        return curves;
    }

    private static double[][] column(List<double[][]> results, int curve) {
        double[][] rows = new double[results.size()][];
        for (int i = 0; i < rows.length; i++) rows[i] = results.get(i)[curve];
        return rows;
    }

    private static double[] scaled(double shortSide, double step) {
        double[] radii = new double[10];
        for (int i = 0; i < radii.length; i++) radii[i] = shortSide * step * (i + 1);
        return radii;
    }

    /**
     * Exact expectation of the uncorrected nearest-neighbour CDF at radius r
     * when each point's candidate neighbours are {@code others} independent
     * uniform points in the window:
     * {@code 1 - (1/|W|) integral_W (1 - a(x)/|W|)^others dx}, with a(x) the
     * area of the disc of radius r about x that lies inside W. Requires r at
     * most half the shorter side, so a disc meets at most one vertical and one
     * horizontal edge.
     */
    static double expectedUncorrectedNearest(RectangularWindow window,
                                             double r, int others) {
        double width = window.width();
        double height = window.height();
        double area = window.area();
        if (r > 0.5 * Math.min(width, height)) {
            throw new IllegalArgumentException("radius too large for the quadrature");
        }
        int n1 = 4000;
        int n2 = 400;
        double h1 = r / n1;
        double far = pow1m(Math.PI * r * r / area, others);
        double stripU = 0.0;
        for (int i = 0; i < n1; i++) {
            double u = (i + 0.5) * h1;
            stripU += pow1m(discInside(r, u, r) / area, others);
        }
        stripU *= h1;
        double h2 = r / n2;
        double corner = 0.0;
        for (int i = 0; i < n2; i++) {
            double u = (i + 0.5) * h2;
            for (int j = 0; j < n2; j++) {
                double v = (j + 0.5) * h2;
                corner += pow1m(discInside(r, u, v) / area, others);
            }
        }
        corner *= h2 * h2;
        double integral = 4.0 * (corner
                + (0.5 * height - r) * stripU
                + (0.5 * width - r) * stripU
                + (0.5 * width - r) * (0.5 * height - r) * far);
        return 1.0 - integral / area;
    }

    private static double pow1m(double fraction, int exponent) {
        return Math.pow(1.0 - fraction, exponent);
    }

    /** Area of the disc of radius r inside a quadrant whose edges are u and v away. */
    private static double discInside(double r, double u, double v) {
        return Math.PI * r * r - segment(r, u) - segment(r, v) + beyondBoth(r, u, v);
    }

    private static double segment(double r, double d) {
        if (d >= r) return 0.0;
        return r * r * Math.acos(d / r) - d * Math.sqrt(r * r - d * d);
    }

    private static double beyondBoth(double r, double u, double v) {
        if (u * u + v * v >= r * r) return 0.0;
        double upper = Math.sqrt(r * r - v * v);
        return antiderivative(r, upper) - antiderivative(r, u) - v * (upper - u);
    }

    private static double antiderivative(double r, double t) {
        return 0.5 * (t * Math.sqrt(Math.max(0.0, r * r - t * t))
                + r * r * Math.asin(Math.min(1.0, t / r)));
    }
}
