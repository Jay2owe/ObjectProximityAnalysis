/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.validation;

import sc.fiji.opa.core.spatial.RectangularWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeded point-process generators for the V1 and V5 validation tests.
 *
 * <p>Every generator draws only from the {@link Random} it is handed, so a
 * fixed seed gives a fixed pattern. Processes whose points depend on things
 * outside the window (cluster parents, hard-core competitors) are simulated on
 * an enlarged window and clipped, so the pattern inside the window is a sample
 * of the stationary process rather than one thinned at its own edge.</p>
 */
final class PointProcesses {

    private PointProcesses() {
    }

    /** Binomial process: exactly {@code n} independent uniform points. */
    static double[][] csr(Random random, int n, RectangularWindow window) {
        double[][] points = new double[n][2];
        for (int i = 0; i < n; i++) {
            points[i][0] = window.getMinX() + random.nextDouble() * window.width();
            points[i][1] = window.getMinY() + random.nextDouble() * window.height();
        }
        return points;
    }

    /**
     * Thomas process: Poisson parents of intensity {@code kappa}, a Poisson
     * number of children with mean {@code mu} per parent, each displaced by an
     * isotropic Gaussian of standard deviation {@code sigma}. Parents are
     * simulated on the window enlarged by {@code 4 sigma} on every side.
     *
     * <p>K(r) = pi r^2 + (1 - exp(-r^2 / (4 sigma^2))) / kappa.</p>
     */
    static double[][] thomas(Random random, double kappa, double mu,
                             double sigma, RectangularWindow window) {
        double margin = 4.0 * sigma;
        List<double[]> points = new ArrayList<double[]>();
        for (double[] parent : parents(random, kappa, window, margin)) {
            int children = poisson(random, mu);
            for (int c = 0; c < children; c++) {
                double x = parent[0] + sigma * random.nextGaussian();
                double y = parent[1] + sigma * random.nextGaussian();
                if (window.contains(x, y)) points.add(new double[]{x, y});
            }
        }
        return points.toArray(new double[0][]);
    }

    /**
     * Matern cluster process: Poisson parents of intensity {@code kappa}, a
     * Poisson number of children with mean {@code mu}, each uniform in the
     * disc of radius {@code radius} about its parent.
     */
    static double[][] maternCluster(Random random, double kappa, double mu,
                                    double radius, RectangularWindow window) {
        List<double[]> points = new ArrayList<double[]>();
        for (double[] parent : parents(random, kappa, window, radius)) {
            int children = poisson(random, mu);
            for (int c = 0; c < children; c++) {
                double distance = radius * Math.sqrt(random.nextDouble());
                double angle = 2.0 * Math.PI * random.nextDouble();
                double x = parent[0] + distance * Math.cos(angle);
                double y = parent[1] + distance * Math.sin(angle);
                if (window.contains(x, y)) points.add(new double[]{x, y});
            }
        }
        return points.toArray(new double[0][]);
    }

    /**
     * Matern type II hard-core process: Poisson proposals of intensity
     * {@code lambdaParent}, each with a uniform mark; a proposal is deleted
     * when another proposal within {@code h} carries a smaller mark. No two
     * retained points are closer than {@code h}. Proposals are simulated on
     * the window enlarged by {@code h} so a competitor just outside the window
     * still deletes.
     */
    static double[][] maternHardCoreII(Random random, double lambdaParent,
                                       double h, RectangularWindow window) {
        List<double[]> proposals = parents(random, lambdaParent, window, h);
        int count = proposals.size();
        double[] marks = new double[count];
        for (int i = 0; i < count; i++) marks[i] = random.nextDouble();
        double squaredH = h * h;
        List<double[]> retained = new ArrayList<double[]>();
        for (int i = 0; i < count; i++) {
            double[] p = proposals.get(i);
            if (!window.contains(p[0], p[1])) continue;
            boolean keep = true;
            for (int j = 0; j < count && keep; j++) {
                if (j == i) continue;
                double[] q = proposals.get(j);
                double dx = q[0] - p[0];
                double dy = q[1] - p[1];
                if (dx * dx + dy * dy <= squaredH && marks[j] < marks[i]) {
                    keep = false;
                }
            }
            if (keep) retained.add(p);
        }
        return retained.toArray(new double[0][]);
    }

    private static List<double[]> parents(Random random, double intensity,
                                          RectangularWindow window,
                                          double margin) {
        double minX = window.getMinX() - margin;
        double minY = window.getMinY() - margin;
        double width = window.width() + 2.0 * margin;
        double height = window.height() + 2.0 * margin;
        int count = poisson(random, intensity * width * height);
        List<double[]> parents = new ArrayList<double[]>(count);
        for (int i = 0; i < count; i++) {
            parents.add(new double[]{
                    minX + random.nextDouble() * width,
                    minY + random.nextDouble() * height});
        }
        return parents;
    }

    /**
     * Poisson variate by counting unit-rate exponential arrivals before
     * {@code mean}. Exact for any mean, linear in it, and fine for the
     * few-thousand means used here.
     */
    static int poisson(Random random, double mean) {
        int count = 0;
        double elapsed = -Math.log(1.0 - random.nextDouble());
        while (elapsed < mean) {
            count++;
            elapsed += -Math.log(1.0 - random.nextDouble());
        }
        return count;
    }
}
