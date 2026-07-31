/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.spatial;

import ij.IJ;
import opa.AnalysisCancelledException;
import opa.OPAParameters;
import opa.OPAProgressListener;

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
        return analyzeUnivariate(
                function,
                points,
                window,
                radii,
                correction,
                simulations,
                seed,
                null);
    }

    public static MonteCarloResult analyzeUnivariate(PatternFunction function,
                                                     double[][] points,
                                                     RectangularWindow window,
                                                     double[] radii,
                                                     EdgeCorrection correction,
                                                     int simulations,
                                                     long seed,
                                                     OPAProgressListener progress) {
        if (function == null || function.isBivariate()) {
            throw new IllegalArgumentException(
                    "A univariate pattern function is required.");
        }
        validateSimulationWork(simulations, radii);
        double[] observed = SpatialStatistics.evaluate(
                function, points, null, window, radii, correction);
        double intensity = points.length / window.area();
        double[] expected = SpatialStatistics.expected(function, radii, intensity);
        if (points.length < 2) {
            reportProgress(progress, 1.0, "Point pattern is undefined");
            return undefined(
                    function,
                    radii,
                    observed,
                    expected,
                    simulations,
                    seed,
                    PatternStatus.INSUFFICIENT_POINTS);
        }

        Random random = new Random(seed);
        double[][] samples = new double[simulations][radii.length];
        for (int simulation = 0; simulation < simulations; simulation++) {
            checkCancelled();
            double[][] randomPoints = generate(
                    points.length, window, random);
            samples[simulation] = SpatialStatistics.evaluate(
                    function, randomPoints, null, window, radii, correction);
            checkCancelled();
            reportProgress(
                    progress,
                    (simulation + 1.0) / simulations,
                    "Monte Carlo simulation " + (simulation + 1)
                            + " of " + simulations);
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
        return analyzeBivariate(
                function,
                source,
                target,
                window,
                radii,
                correction,
                simulations,
                seed,
                null);
    }

    public static MonteCarloResult analyzeBivariate(PatternFunction function,
                                                    double[][] source,
                                                    double[][] target,
                                                    RectangularWindow window,
                                                    double[] radii,
                                                    EdgeCorrection correction,
                                                    int simulations,
                                                    long seed,
                                                    OPAProgressListener progress) {
        if (function == null || !function.isBivariate()) {
            throw new IllegalArgumentException(
                    "A bivariate pattern function is required.");
        }
        validateSimulationWork(simulations, radii);
        double[] observed = SpatialStatistics.evaluate(
                function, source, target, window, radii, correction);
        double targetIntensity = target.length / window.area();
        double[] expected = SpatialStatistics.expected(
                function, radii, targetIntensity);
        if (source.length == 0 || target.length == 0) {
            reportProgress(progress, 1.0, "Point pattern is undefined");
            return undefined(
                    function,
                    radii,
                    observed,
                    expected,
                    simulations,
                    seed,
                    PatternStatus.INSUFFICIENT_POINTS);
        }

        Random random = new Random(seed);
        double[][] samples = new double[simulations][radii.length];
        for (int simulation = 0; simulation < simulations; simulation++) {
            checkCancelled();
            double[][] randomSource = generate(source.length, window, random);
            double[][] randomTarget = generate(target.length, window, random);
            samples[simulation] = SpatialStatistics.evaluate(
                    function,
                    randomSource,
                    randomTarget,
                    window,
                    radii,
                    correction);
            checkCancelled();
            reportProgress(
                    progress,
                    (simulation + 1.0) / simulations,
                    "Monte Carlo simulation " + (simulation + 1)
                            + " of " + simulations);
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
        int[] envelopeSampleCounts = new int[radiusCount];

        for (int radiusIndex = 0; radiusIndex < radiusCount; radiusIndex++) {
            checkCancelled();
            double[] finite = finiteColumn(samples, radiusIndex);
            envelopeSampleCounts[radiusIndex] = finite.length;
            if (finite.length != simulations) {
                lower[radiusIndex] = Double.NaN;
                upper[radiusIndex] = Double.NaN;
                continue;
            }
            Arrays.sort(finite);
            lower[radiusIndex] = percentile(finite, 0.025);
            upper[radiusIndex] = percentile(finite, 0.975);
        }

        double[] exchangeableScales = exchangeableScales(observed, samples);
        double observedMaximum = standardizedMaximum(
                observed, expected, exchangeableScales);
        int rankSampleCount = 0;
        int asOrMoreExtreme = 0;
        if (!Double.isNaN(observedMaximum)) {
            rankSampleCount++;
            asOrMoreExtreme++;
            for (double[] sample : samples) {
                checkCancelled();
                double simulatedMaximum = standardizedMaximum(
                        sample, expected, exchangeableScales);
                if (Double.isNaN(simulatedMaximum)) continue;
                rankSampleCount++;
                if (simulatedMaximum >= observedMaximum) asOrMoreExtreme++;
            }
        }
        boolean completeRank = rankSampleCount == simulations + 1;
        double globalP = completeRank
                ? asOrMoreExtreme / (double) rankSampleCount
                : Double.NaN;

        int maximumIndex = -1;
        double maximumDeviation = Double.NaN;
        for (int i = 0; i < observed.length; i++) {
            checkCancelled();
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
                envelopeSampleCounts,
                globalP,
                maximumDeviation,
                maximumRadius,
                simulations,
                seed,
                rankSampleCount == 0
                        ? PatternStatus.NO_VALID_RADII
                        : completeRank
                                ? PatternStatus.OK
                                : PatternStatus.INCOMPLETE_MONTE_CARLO,
                rankSampleCount);
    }

    private static MonteCarloResult undefined(PatternFunction function,
                                              double[] radii,
                                              double[] observed,
                                              double[] expected,
                                              int simulations,
                                              long seed,
                                              PatternStatus status) {
        double[] undefined = new double[radii.length];
        Arrays.fill(undefined, Double.NaN);
        int[] envelopeSampleCounts = new int[radii.length];
        return new MonteCarloResult(
                function,
                radii,
                observed,
                expected,
                undefined,
                undefined,
                envelopeSampleCounts,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                simulations,
                seed,
                status,
                0);
    }

    private static double[][] generate(int count,
                                       RectangularWindow window,
                                       Random random) {
        double[][] points = new double[count][2];
        for (int i = 0; i < count; i++) {
            if ((i & 255) == 0) checkCancelled();
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

    private static double[] exchangeableScales(double[] observed,
                                               double[][] samples) {
        double[] scales = new double[observed.length];
        for (int column = 0; column < scales.length; column++) {
            checkCancelled();
            int count = Double.isFinite(observed[column]) ? 1 : 0;
            for (double[] sample : samples) {
                if (Double.isFinite(sample[column])) count++;
            }
            if (count < 2) {
                scales[column] = count == 1 ? 0.0 : Double.NaN;
                continue;
            }
            double[] values = new double[count];
            int index = 0;
            if (Double.isFinite(observed[column])) {
                values[index++] = observed[column];
            }
            for (double[] sample : samples) {
                if (Double.isFinite(sample[column])) {
                    values[index++] = sample[column];
                }
            }
            double centre = mean(values);
            scales[column] = standardDeviation(values, centre);
        }
        return scales;
    }

    private static double standardizedMaximum(double[] values,
                                              double[] expected,
                                              double[] standardDeviations) {
        double maximum = 0.0;
        boolean found = false;
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i]) || !Double.isFinite(expected[i])) continue;
            double deviation = Math.abs(values[i] - expected[i]);
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

    private static void validateSimulationWork(int simulations,
                                               double[] radii) {
        if (simulations < 1 || simulations > OPAParameters.MAX_SIMULATIONS) {
            throw new IllegalArgumentException(
                    "Simulation count must be between 1 and "
                            + OPAParameters.MAX_SIMULATIONS + ".");
        }
        if (radii == null) {
            throw new IllegalArgumentException("Radii must not be null.");
        }
        if (radii.length == 0) {
            throw new IllegalArgumentException(
                    "At least one pattern radius is required.");
        }
        if (radii.length > OPAParameters.MAX_RADIUS_BINS) {
            throw new IllegalArgumentException(
                    "Pattern radius count must not exceed "
                            + OPAParameters.MAX_RADIUS_BINS + ".");
        }
        if ((long) simulations * radii.length
                > OPAParameters.MAX_MONTE_CARLO_VALUES) {
            throw new IllegalArgumentException(
                    "Monte Carlo simulations multiplied by radius count "
                            + "must not exceed "
                            + OPAParameters.MAX_MONTE_CARLO_VALUES + ".");
        }
    }

    private static void reportProgress(OPAProgressListener progress,
                                       double fraction,
                                       String message) {
        if (progress != null) progress.onProgress(fraction, message);
    }

    private static void checkCancelled() {
        if (IJ.escapePressed()) throw new AnalysisCancelledException();
    }
}
