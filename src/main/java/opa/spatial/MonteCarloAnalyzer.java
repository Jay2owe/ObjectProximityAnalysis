/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.spatial;

import java.util.Arrays;
import java.util.Random;

/**
 * Deterministic complete-spatial-randomness envelopes for supported curves.
 */
public final class MonteCarloAnalyzer {

    private MonteCarloAnalyzer() {
    }

    public static MonteCarloResult analyzeUnivariate(PatternFunction function,
                                                     double[][] points,
                                                     RectangularWindow window,
                                                     double[] radii,
                                                     EdgeCorrection correction,
                                                     int simulations,
                                                     long seed) {
        if (function == null || function.isBivariate()) {
            throw new IllegalArgumentException(
                    "A univariate pattern function is required.");
        }
        validateSimulationCount(simulations);
        double[] observed = SpatialStatistics.evaluate(
                function, points, null, window, radii, correction);
        double intensity = points.length / window.area();
        double[] expected = SpatialStatistics.expected(function, radii, intensity);

        Random random = new Random(seed);
        double[][] samples = new double[simulations][radii.length];
        for (int simulation = 0; simulation < simulations; simulation++) {
            double[][] randomPoints = generate(
                    points.length, window, random);
            samples[simulation] = SpatialStatistics.evaluate(
                    function, randomPoints, null, window, radii, correction);
        }
        return summarize(
                function, radii, observed, expected, samples, simulations, seed);
    }

    public static MonteCarloResult analyzeBivariate(PatternFunction function,
                                                    double[][] source,
                                                    double[][] target,
                                                    RectangularWindow window,
                                                    double[] radii,
                                                    EdgeCorrection correction,
                                                    int simulations,
                                                    long seed) {
        if (function == null || !function.isBivariate()) {
            throw new IllegalArgumentException(
                    "A bivariate pattern function is required.");
        }
        validateSimulationCount(simulations);
        double[] observed = SpatialStatistics.evaluate(
                function, source, target, window, radii, correction);
        double targetIntensity = target.length / window.area();
        double[] expected = SpatialStatistics.expected(
                function, radii, targetIntensity);

        Random random = new Random(seed);
        double[][] samples = new double[simulations][radii.length];
        for (int simulation = 0; simulation < simulations; simulation++) {
            double[][] randomSource = generate(source.length, window, random);
            double[][] randomTarget = generate(target.length, window, random);
            samples[simulation] = SpatialStatistics.evaluate(
                    function,
                    randomSource,
                    randomTarget,
                    window,
                    radii,
                    correction);
        }
        return summarize(
                function, radii, observed, expected, samples, simulations, seed);
    }

    private static MonteCarloResult summarize(PatternFunction function,
                                              double[] radii,
                                              double[] observed,
                                              double[] expected,
                                              double[][] samples,
                                              int simulations,
                                              long seed) {
        int radiusCount = radii.length;
        double[] lower = new double[radiusCount];
        double[] upper = new double[radiusCount];
        double[] means = new double[radiusCount];
        double[] standardDeviations = new double[radiusCount];

        for (int radiusIndex = 0; radiusIndex < radiusCount; radiusIndex++) {
            double[] finite = finiteColumn(samples, radiusIndex);
            if (finite.length == 0) {
                lower[radiusIndex] = Double.NaN;
                upper[radiusIndex] = Double.NaN;
                means[radiusIndex] = Double.NaN;
                standardDeviations[radiusIndex] = Double.NaN;
                continue;
            }
            Arrays.sort(finite);
            lower[radiusIndex] = percentile(finite, 0.025);
            upper[radiusIndex] = percentile(finite, 0.975);
            means[radiusIndex] = mean(finite);
            standardDeviations[radiusIndex] = standardDeviation(
                    finite, means[radiusIndex]);
        }

        double observedMaximum = standardizedMaximum(
                observed, means, standardDeviations);
        int asOrMoreExtreme = 0;
        for (double[] sample : samples) {
            double simulatedMaximum = standardizedMaximum(
                    sample, means, standardDeviations);
            if (simulatedMaximum >= observedMaximum) asOrMoreExtreme++;
        }
        double globalP = (1.0 + asOrMoreExtreme) / (simulations + 1.0);

        int maximumIndex = -1;
        double maximumDeviation = Double.NaN;
        for (int i = 0; i < observed.length; i++) {
            if (!Double.isFinite(observed[i]) || !Double.isFinite(expected[i])) continue;
            double deviation = Math.abs(observed[i] - expected[i]);
            if (maximumIndex < 0 || deviation > maximumDeviation) {
                maximumIndex = i;
                maximumDeviation = deviation;
            }
        }
        double maximumRadius = maximumIndex < 0
                ? Double.NaN
                : radii[maximumIndex];

        return new MonteCarloResult(
                function,
                radii,
                observed,
                expected,
                lower,
                upper,
                globalP,
                maximumDeviation,
                maximumRadius,
                simulations,
                seed);
    }

    private static double[][] generate(int count,
                                       RectangularWindow window,
                                       Random random) {
        double[][] points = new double[count][2];
        for (int i = 0; i < count; i++) {
            points[i][0] = window.getMinX() + random.nextDouble() * window.width();
            points[i][1] = window.getMinY() + random.nextDouble() * window.height();
        }
        return points;
    }

    private static double[] finiteColumn(double[][] values, int column) {
        int count = 0;
        for (double[] row : values) {
            if (Double.isFinite(row[column])) count++;
        }
        double[] finite = new double[count];
        int index = 0;
        for (double[] row : values) {
            if (Double.isFinite(row[column])) finite[index++] = row[column];
        }
        return finite;
    }

    private static double standardizedMaximum(double[] values,
                                              double[] means,
                                              double[] standardDeviations) {
        double maximum = 0.0;
        boolean found = false;
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i]) || !Double.isFinite(means[i])) continue;
            double deviation = Math.abs(values[i] - means[i]);
            double standardDeviation = standardDeviations[i];
            double standardized = standardDeviation > 0.0
                    ? deviation / standardDeviation
                    : deviation == 0.0 ? 0.0 : Double.POSITIVE_INFINITY;
            if (!found || standardized > maximum) maximum = standardized;
            found = true;
        }
        return found ? maximum : Double.NaN;
    }

    private static double percentile(double[] sorted, double quantile) {
        if (sorted.length == 1) return sorted[0];
        double position = quantile * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return sorted[lower];
        double fraction = position - lower;
        return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double standardDeviation(double[] values, double mean) {
        if (values.length < 2) return 0.0;
        double sumSquares = 0.0;
        for (double value : values) {
            double difference = value - mean;
            sumSquares += difference * difference;
        }
        return Math.sqrt(sumSquares / (values.length - 1));
    }

    private static void validateSimulationCount(int simulations) {
        if (simulations < 1) {
            throw new IllegalArgumentException(
                    "Simulation count must be at least 1.");
        }
    }
}
