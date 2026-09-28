/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.validation;

import org.junit.AfterClass;
import org.junit.Test;
import sc.fiji.opa.core.spatial.EdgeCorrection;
import sc.fiji.opa.core.spatial.MonteCarloAnalyzer;
import sc.fiji.opa.core.spatial.MonteCarloResult;
import sc.fiji.opa.core.spatial.PatternFunction;
import sc.fiji.opa.core.spatial.RectangularWindow;
import sc.fiji.opa.core.spatial.SpatialStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Validation stage V5: behaviour on known non-random patterns.
 *
 * <ul>
 *   <li><b>Thomas process</b> (quantitative). Its K has the closed form
 *       {@code pi r^2 + (1 - exp(-r^2 / (4 sigma^2))) / kappa}, so the mean
 *       translation-corrected estimate is compared with it radius by radius,
 *       and (kappa, sigma) are recovered by minimum contrast on the mean
 *       curve. The validation plan names a Matern cluster process; the Thomas
 *       process is substituted for the quantitative check because its K is
 *       closed-form.</li>
 *   <li><b>Matern cluster process</b> (qualitative). Excess K over pi r^2
 *       must be significant up to the cluster diameter 2R and then stop
 *       growing: the clustering signature at the right spatial scale.</li>
 *   <li><b>Matern type II hard-core process</b>. No pair is closer than h, so
 *       K is exactly 0 below h in every realisation, and K sits significantly
 *       below pi r^2 just above h.</li>
 *   <li><b>Power</b>. The rejection rate of the global maximum-deviation test
 *       must rise monotonically with effect size, for a clustered family
 *       (Thomas, tested with K) and an inhibited family (Matern II, tested
 *       with nearest-neighbour G).</li>
 * </ul>
 *
 * <p>Fast mode (default): 50 realisations for the curve checks, and 16 per
 * effect size at 39 simulations for power. Full mode
 * ({@code -Dopa.validation=true}): 1,000, and 200 per effect size at 119
 * simulations. Report: {@code target/validation/v5-report.txt}.</p>
 */
public class NonRandomPatternValidationTest {

    private static final RectangularWindow WINDOW =
            new RectangularWindow(0.0, 0.0, 1000.0, 1000.0);
    private static final double AREA = WINDOW.area();
    private static final double[] RADII = steps(5.0, 20);
    private static final StringBuilder REPORT = new StringBuilder();

    static {
        REPORT.append("V5 - behaviour on known non-random patterns\n");
        REPORT.append(ValidationSupport.format(
                "mode=%s seed=%d window=1000x1000%n%n",
                ValidationSupport.mode(), ValidationSupport.SEED));
    }

    @AfterClass
    public static void writeReport() {
        ValidationSupport.writeReport("v5-report.txt", REPORT);
    }

    @Test
    public void thomasProcessRecoversClosedFormK() {
        final double kappa = 50.0 / AREA;
        final double mu = 8.0;
        final double sigma = 20.0;
        int realisations = ValidationSupport.realisations(50, 1000);
        double[][] rows = curves(realisations, 101, new Generator() {
            @Override
            public double[][] draw(Random random) {
                return PointProcesses.thomas(random, kappa, mu, sigma, WINDOW);
            }
        });
        StringBuilder section = new StringBuilder();
        section.append(ValidationSupport.format(
                "=== Thomas process: kappa=%.1f/|W| mu=%.1f sigma=%.1f, "
                        + "realisations=%d, K translation ===%n",
                kappa * AREA, mu, sigma, realisations));
        section.append("radius  theory_K     mean_K       SE          z       ratio\n");
        double[] mean = new double[RADII.length];
        List<String> failures = new ArrayList<String>();
        for (int r = 0; r < RADII.length; r++) {
            double radius = RADII[r];
            double theory = thomasK(radius, kappa, sigma);
            mean[r] = ValidationSupport.mean(rows, r);
            double se = ValidationSupport.standardError(rows, r);
            double ratio = mean[r] / theory;
            section.append(ValidationSupport.format(
                    "%-7.1f %-12.2f %-12.2f %-11.3e %7.3f %8.4f%n",
                    radius, theory, mean[r], se, (mean[r] - theory) / se, ratio));
            if (Math.abs(ratio - 1.0) > 0.05) {
                failures.add(ValidationSupport.format("r=%.1f ratio=%.4f", radius, ratio));
            }
        }
        double[] fit = minimumContrast(mean);
        section.append(ValidationSupport.format(
                "minimum contrast: sigma=%.3f (true %.1f, %+.1f%%) "
                        + "kappa=%.2f/|W| (true %.1f, %+.1f%%)%n%n",
                fit[0], sigma, 100.0 * (fit[0] / sigma - 1.0),
                fit[1] * AREA, kappa * AREA, 100.0 * (fit[1] / kappa - 1.0)));
        REPORT.append(section);
        assertTrue("Thomas K off closed form by more than 5%: " + failures,
                failures.isEmpty());
        assertEquals("recovered sigma", sigma, fit[0], 0.10 * sigma);
        assertEquals("recovered kappa", kappa, fit[1], 0.15 * kappa);
    }

    @Test
    public void maternClusterShowsExcessUpToClusterDiameter() {
        final double kappa = 50.0 / AREA;
        final double mu = 8.0;
        final double radiusR = 25.0;
        int realisations = ValidationSupport.realisations(50, 1000);
        double[][] rows = curves(realisations, 102, new Generator() {
            @Override
            public double[][] draw(Random random) {
                return PointProcesses.maternCluster(random, kappa, mu, radiusR, WINDOW);
            }
        });
        // Excess over the Poisson value, per realisation.
        double[][] excess = new double[rows.length][RADII.length];
        for (int i = 0; i < rows.length; i++) {
            for (int r = 0; r < RADII.length; r++) {
                excess[i][r] = rows[i][r] - Math.PI * RADII[r] * RADII[r];
            }
        }
        int diameterIndex = indexOf(2.0 * radiusR);
        double plateau = ValidationSupport.mean(excess, diameterIndex);
        StringBuilder section = new StringBuilder();
        section.append(ValidationSupport.format(
                "=== Matern cluster: kappa=%.1f/|W| mu=%.1f R=%.1f, realisations=%d, "
                        + "K translation ===%n", kappa * AREA, mu, radiusR, realisations));
        section.append("radius  excess_mean  SE          z_vs_0   theory_excess ratio   "
                + "step_from_2R SE_step\n");
        List<String> failures = new ArrayList<String>();
        for (int r = 0; r < RADII.length; r++) {
            double radius = RADII[r];
            double mean = ValidationSupport.mean(excess, r);
            double se = ValidationSupport.standardError(excess, r);
            double theory = maternClusterExcess(radius, kappa, radiusR);
            double[][] step = new double[excess.length][1];
            for (int i = 0; i < excess.length; i++) {
                step[i][0] = excess[i][r] - excess[i][diameterIndex];
            }
            double stepMean = ValidationSupport.mean(step, 0);
            double stepSe = ValidationSupport.standardError(step, 0);
            section.append(ValidationSupport.format(
                    "%-7.1f %-12.2f %-11.3e %8.2f %-13.2f %7.4f %12.2f %-10.3e%n",
                    radius, mean, se, mean / se, theory, mean / theory,
                    stepMean, stepSe));
            if (radius <= 2.0 * radiusR) {
                if (!(mean > 3.0 * se)) {
                    failures.add(ValidationSupport.format(
                            "no significant excess at r=%.1f", radius));
                }
            } else if (Math.abs(stepMean) > 3.0 * stepSe + 0.05 * plateau) {
                failures.add(ValidationSupport.format(
                        "excess still moving past 2R at r=%.1f (step %.1f)",
                        radius, stepMean));
            }
        }
        section.append('\n');
        REPORT.append(section);
        assertTrue("Matern cluster signature: " + failures, failures.isEmpty());
    }

    @Test
    public void hardCoreHasNoPairsInsideH() {
        final double lambdaParent = 400.0 / AREA;
        final double h = 20.0;
        final double[] radii = {5.0, 10.0, 15.0, 19.0, 20.0, 25.0, 30.0, 40.0, 60.0};
        int realisations = ValidationSupport.realisations(50, 1000);
        List<Callable<double[]>> tasks = new ArrayList<Callable<double[]>>();
        for (int i = 0; i < realisations; i++) {
            final long realisation = i;
            tasks.add(new Callable<double[]>() {
                @Override
                public double[] call() {
                    Random random = new Random(ValidationSupport.seed(103, realisation));
                    double[][] points = PointProcesses.maternHardCoreII(
                            random, lambdaParent, h, WINDOW);
                    return SpatialStatistics.computeK(
                            points, WINDOW, radii, EdgeCorrection.TRANSLATION);
                }
            });
        }
        double[][] rows = ValidationSupport.runAll(tasks).toArray(new double[0][]);
        StringBuilder section = new StringBuilder();
        section.append(ValidationSupport.format(
                "=== Matern II hard-core: lambda_parent=%.1f/|W| h=%.1f, "
                        + "realisations=%d, K translation ===%n",
                lambdaParent * AREA, h, realisations));
        section.append("radius  pi_r2        mean_K       SE          max_K       z_vs_pi_r2\n");
        List<String> failures = new ArrayList<String>();
        for (int r = 0; r < radii.length; r++) {
            double radius = radii[r];
            double poisson = Math.PI * radius * radius;
            double mean = ValidationSupport.mean(rows, r);
            double se = ValidationSupport.standardError(rows, r);
            double maximum = 0.0;
            for (double[] row : rows) maximum = Math.max(maximum, row[r]);
            section.append(ValidationSupport.format(
                    "%-7.1f %-12.2f %-12.4f %-11.3e %-11.4f %8.2f%n",
                    radius, poisson, mean, se, maximum,
                    se > 0.0 ? (mean - poisson) / se : Double.NEGATIVE_INFINITY));
            if (radius < h && maximum != 0.0) {
                failures.add(ValidationSupport.format(
                        "K nonzero below h at r=%.1f (max %.4f)", radius, maximum));
            }
            if (radius >= h && radius <= 1.5 * h && !(mean + 3.0 * se < poisson)) {
                failures.add(ValidationSupport.format(
                        "K not significantly below pi r^2 at r=%.1f", radius));
            }
        }
        section.append('\n');
        REPORT.append(section);
        assertTrue("hard-core signature: " + failures, failures.isEmpty());
    }

    @Test
    public void globalTestPowerRisesWithEffectSize() {
        int realisations = ValidationSupport.realisations(16, 200);
        // 39 simulations is the smallest count whose rank envelope expresses
        // 5% exactly; the full run uses 119 as the plan specifies.
        final int simulations = ValidationSupport.FULL ? 119 : 39;
        StringBuilder section = new StringBuilder();
        section.append(ValidationSupport.format(
                "=== Power of the global maximum-deviation test: %d simulations, "
                        + "reject at p <= 0.05, realisations=%d per level ===%n"
                        + "Thomas: K, translation, radii 10..100. "
                        + "Matern II: nearest-neighbour G, radii 2..40.%n",
                simulations, realisations));
        section.append("family       level        mean_n   rejections rate    Wilson95\n");
        List<String> failures = new ArrayList<String>();

        // Clustered: Thomas, about 100 points, clustering tightening with sigma,
        // tested with K, the function built to see clustering.
        final double[] clusterRadii = steps(10.0, 10);
        final double clusterKappa = 100.0 / AREA;
        final double clusterMu = 1.0;
        double[] sigmas = {Double.NaN, 90.0, 60.0, 30.0};
        double[] clusterRates = new double[sigmas.length];
        for (int level = 0; level < sigmas.length; level++) {
            final double sigma = sigmas[level];
            clusterRates[level] = power(section, "Thomas",
                    Double.isNaN(sigma) ? "CSR n=100" : "sigma=" + (int) sigma,
                    200 + level, realisations, PatternFunction.K, clusterRadii,
                    simulations,
                    new Generator() {
                        @Override
                        public double[][] draw(Random random) {
                            return Double.isNaN(sigma)
                                    ? PointProcesses.csr(random, 100, WINDOW)
                                    : PointProcesses.thomas(random, clusterKappa,
                                    clusterMu, sigma, WINDOW);
                        }
                    });
        }
        // Inhibited: Matern II, about 100 points, hard-core distance growing,
        // tested with nearest-neighbour G, which a hard core acts on directly.
        final double[] hardCoreRadii = steps(2.0, 20);
        final double hardCoreParents = 130.0 / AREA;
        double[] hs = {Double.NaN, 20.0, 30.0, 40.0};
        double[] hardCoreRates = new double[hs.length];
        for (int level = 0; level < hs.length; level++) {
            final double h = hs[level];
            hardCoreRates[level] = power(section, "Matern II",
                    Double.isNaN(h) ? "CSR n=100" : "h=" + (int) h,
                    300 + level, realisations, PatternFunction.G, hardCoreRadii,
                    simulations,
                    new Generator() {
                        @Override
                        public double[][] draw(Random random) {
                            return Double.isNaN(h)
                                    ? PointProcesses.csr(random, 100, WINDOW)
                                    : PointProcesses.maternHardCoreII(
                                    random, hardCoreParents, h, WINDOW);
                        }
                    });
        }
        section.append('\n');
        REPORT.append(section);
        checkMonotone("Thomas", clusterRates, failures);
        checkMonotone("Matern II", hardCoreRates, failures);
        assertTrue("power not monotone: " + failures, failures.isEmpty());
    }

    private static void checkMonotone(String family, double[] rates,
                                      List<String> failures) {
        for (int i = 1; i < rates.length; i++) {
            if (rates[i] < rates[i - 1]) {
                failures.add(family + " level " + i + " rate " + rates[i]
                        + " below level " + (i - 1) + " rate " + rates[i - 1]);
            }
        }
        if (!(rates[rates.length - 1] > rates[0])) {
            failures.add(family + " strongest effect not above CSR");
        }
    }

    /**
     * Rejection rate at one effect size. Realisations run in parallel, each
     * with its own seeded pattern and seeded Monte Carlo, and the engine's own
     * worker pool is held at one thread meanwhile so the two levels of
     * parallelism do not oversubscribe the machine. Results do not depend on
     * the worker count (MonteCarloParallelismTest in opa-core).
     */
    private static double power(StringBuilder section, String family,
                                String level, final long caseIndex,
                                int realisations, final PatternFunction function,
                                final double[] radii, final int simulations,
                                final Generator generator) {
        List<Callable<double[]>> tasks = new ArrayList<Callable<double[]>>();
        for (int i = 0; i < realisations; i++) {
            final long realisation = i;
            tasks.add(new Callable<double[]>() {
                @Override
                public double[] call() {
                    Random random = new Random(ValidationSupport.seed(caseIndex, realisation));
                    double[][] pattern = generator.draw(random);
                    MonteCarloResult result = MonteCarloAnalyzer.analyzeUnivariate(
                            function, pattern, WINDOW, radii,
                            EdgeCorrection.TRANSLATION, simulations,
                            ValidationSupport.seed(caseIndex + 1000, realisation));
                    return new double[]{pattern.length, result.getGlobalPValue()};
                }
            });
        }
        String previous = System.getProperty("opa.parallelism");
        List<double[]> outcomes;
        System.setProperty("opa.parallelism", "1");
        try {
            outcomes = ValidationSupport.runAll(tasks);
        } finally {
            if (previous == null) System.clearProperty("opa.parallelism");
            else System.setProperty("opa.parallelism", previous);
        }
        int rejections = 0;
        long points = 0;
        for (double[] outcome : outcomes) {
            points += (long) outcome[0];
            if (outcome[1] <= 0.05) rejections++;
        }
        double rate = rejections / (double) realisations;
        double[] interval = ValidationSupport.wilson(rejections, realisations);
        section.append(ValidationSupport.format(
                "%-12s %-12s %-8.1f %-10d %-7.3f [%.3f, %.3f]%n",
                family, level, points / (double) realisations, rejections, rate,
                interval[0], interval[1]));
        return rate;
    }

    private interface Generator {
        double[][] draw(Random random);
    }

    private static double[][] curves(int realisations, final long caseIndex,
                                     final Generator generator) {
        List<Callable<double[]>> tasks = new ArrayList<Callable<double[]>>();
        for (int i = 0; i < realisations; i++) {
            final long realisation = i;
            tasks.add(new Callable<double[]>() {
                @Override
                public double[] call() {
                    Random random = new Random(ValidationSupport.seed(caseIndex, realisation));
                    return SpatialStatistics.computeK(generator.draw(random),
                            WINDOW, RADII, EdgeCorrection.TRANSLATION);
                }
            });
        }
        return ValidationSupport.runAll(tasks).toArray(new double[0][]);
    }

    static double thomasK(double r, double kappa, double sigma) {
        return Math.PI * r * r
                + (1.0 - Math.exp(-r * r / (4.0 * sigma * sigma))) / kappa;
    }

    /**
     * Excess K over pi r^2 for a Matern cluster process: the probability that
     * two independent uniform points in a disc of radius R lie within r of
     * each other, divided by kappa. The distance density is the standard
     * disc line-picking result
     * {@code f(s) = (4 s / (pi R^2)) (acos(s / 2R) - (s / 2R) sqrt(1 - (s / 2R)^2))}.
     */
    static double maternClusterExcess(double r, double kappa, double radiusR) {
        double upper = Math.min(r, 2.0 * radiusR);
        int steps = 4000;
        double h = upper / steps;
        double sum = 0.0;
        for (int i = 0; i < steps; i++) {
            double s = (i + 0.5) * h;
            double t = s / (2.0 * radiusR);
            sum += 4.0 * s / (Math.PI * radiusR * radiusR)
                    * (Math.acos(t) - t * Math.sqrt(1.0 - t * t));
        }
        return sum * h / kappa;
    }

    /**
     * Minimum-contrast fit of (sigma, kappa) to a mean K curve. For fixed
     * sigma the excess is linear in 1/kappa, so 1/kappa has a closed-form
     * least-squares value; sigma is found by a fine grid search.
     */
    private static double[] minimumContrast(double[] meanK) {
        double bestSigma = Double.NaN;
        double bestKappa = Double.NaN;
        double bestResidual = Double.POSITIVE_INFINITY;
        for (int step = 0; step <= 20000; step++) {
            double sigma = 2.0 + step * 0.005;
            double fy = 0.0;
            double ff = 0.0;
            for (int r = 0; r < RADII.length; r++) {
                double f = 1.0 - Math.exp(-RADII[r] * RADII[r] / (4.0 * sigma * sigma));
                double y = meanK[r] - Math.PI * RADII[r] * RADII[r];
                fy += f * y;
                ff += f * f;
            }
            double inverseKappa = fy / ff;
            double residual = 0.0;
            for (int r = 0; r < RADII.length; r++) {
                double f = 1.0 - Math.exp(-RADII[r] * RADII[r] / (4.0 * sigma * sigma));
                double y = meanK[r] - Math.PI * RADII[r] * RADII[r];
                double d = y - f * inverseKappa;
                residual += d * d;
            }
            if (residual < bestResidual) {
                bestResidual = residual;
                bestSigma = sigma;
                bestKappa = 1.0 / inverseKappa;
            }
        }
        return new double[]{bestSigma, bestKappa};
    }

    private static int indexOf(double radius) {
        for (int i = 0; i < RADII.length; i++) {
            if (Math.abs(RADII[i] - radius) < 1e-9) return i;
        }
        throw new IllegalArgumentException("radius " + radius + " not in the grid");
    }

    private static double[] steps(double step, int count) {
        double[] radii = new double[count];
        for (int i = 0; i < count; i++) radii[i] = step * (i + 1);
        return radii;
    }
}
