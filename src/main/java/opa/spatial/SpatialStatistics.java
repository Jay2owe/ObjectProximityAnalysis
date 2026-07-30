/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.spatial;

/**
 * Pure, dependency-free 2D point-pattern statistics.
 */
public final class SpatialStatistics {

    private SpatialStatistics() {
    }

    public static double[] computeK(double[][] points,
                                    RectangularWindow window,
                                    double[] radii,
                                    EdgeCorrection correction) {
        validateInputs(points, window, radii, correction);
        double[] values = new double[radii.length];
        int count = points.length;
        if (count < 2) return nanArray(radii.length);

        for (int radiusIndex = 0; radiusIndex < radii.length; radiusIndex++) {
            double radius = radii[radiusIndex];
            if (correction == EdgeCorrection.BORDER) {
                values[radiusIndex] = borderK(
                        points, points, true, window, radius);
            } else {
                values[radiusIndex] = pairK(
                        points, points, true, window, radius, correction);
            }
        }
        return values;
    }

    public static double[] computeCrossK(double[][] source,
                                         double[][] target,
                                         RectangularWindow window,
                                         double[] radii,
                                         EdgeCorrection correction) {
        validateInputs(source, window, radii, correction);
        validatePoints(target, window, "target");
        double[] values = new double[radii.length];
        if (source.length == 0 || target.length == 0) {
            return nanArray(radii.length);
        }

        for (int radiusIndex = 0; radiusIndex < radii.length; radiusIndex++) {
            double radius = radii[radiusIndex];
            if (correction == EdgeCorrection.BORDER) {
                values[radiusIndex] = borderK(
                        source, target, false, window, radius);
            } else {
                values[radiusIndex] = pairK(
                        source, target, false, window, radius, correction);
            }
        }
        return values;
    }

    /**
     * Variance-stabilised L(r) = sqrt(K(r) / pi).
     */
    public static double[] computeL(double[] kValues) {
        if (kValues == null) {
            throw new IllegalArgumentException("K values must not be null.");
        }
        double[] values = new double[kValues.length];
        for (int i = 0; i < kValues.length; i++) {
            values[i] = Double.isNaN(kValues[i]) || kValues[i] < 0.0
                    ? Double.NaN
                    : Math.sqrt(kValues[i] / Math.PI);
        }
        return values;
    }

    public static double[] computeLMinusR(double[] kValues, double[] radii) {
        if (kValues == null || radii == null || kValues.length != radii.length) {
            throw new IllegalArgumentException(
                    "K values and radii must be non-null arrays of equal length.");
        }
        double[] values = computeL(kValues);
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) values[i] -= radii[i];
        }
        return values;
    }

    public static double[] computeG(double[][] points, double[] radii) {
        validateRadii(radii);
        validateFinitePoints(points, "points");
        if (points.length < 2) return nanArray(radii.length);
        double[] nearest = nearestDistances(points, points, true);
        return empiricalCdf(nearest, radii);
    }

    public static double[] computeCrossG(double[][] source,
                                         double[][] target,
                                         double[] radii) {
        validateRadii(radii);
        validateFinitePoints(source, "source");
        validateFinitePoints(target, "target");
        if (source.length == 0 || target.length == 0) {
            return nanArray(radii.length);
        }
        return empiricalCdf(nearestDistances(source, target, false), radii);
    }

    /**
     * Ring-normalised pair correlation derived from increments of K.
     */
    public static double[] computePairCorrelation(double[][] points,
                                                  RectangularWindow window,
                                                  double[] radii,
                                                  EdgeCorrection correction) {
        return pairCorrelationFromK(computeK(points, window, radii, correction), radii);
    }

    public static double[] pairCorrelationFromK(double[] kValues, double[] radii) {
        if (kValues == null || radii == null || kValues.length != radii.length) {
            throw new IllegalArgumentException(
                    "K values and radii must be non-null arrays of equal length.");
        }
        validateRadii(radii);
        double[] values = new double[radii.length];
        double previousK = 0.0;
        double previousRadius = 0.0;
        for (int i = 0; i < radii.length; i++) {
            double annulusArea = Math.PI
                    * (radii[i] * radii[i] - previousRadius * previousRadius);
            if (annulusArea <= 0.0
                    || Double.isNaN(kValues[i])
                    || Double.isNaN(previousK)) {
                values[i] = Double.NaN;
            } else {
                values[i] = (kValues[i] - previousK) / annulusArea;
            }
            previousK = kValues[i];
            previousRadius = radii[i];
        }
        return values;
    }

    public static double[] expected(PatternFunction function,
                                    double[] radii,
                                    double targetIntensity) {
        if (function == null) {
            throw new IllegalArgumentException("Pattern function must not be null.");
        }
        validateRadii(radii);
        if (!Double.isFinite(targetIntensity) || targetIntensity < 0.0) {
            throw new IllegalArgumentException(
                    "Target intensity must be finite and non-negative.");
        }
        double[] values = new double[radii.length];
        for (int i = 0; i < radii.length; i++) {
            double radius = radii[i];
            switch (function) {
                case K:
                case CROSS_K:
                    values[i] = Math.PI * radius * radius;
                    break;
                case L:
                case CROSS_L:
                    values[i] = radius;
                    break;
                case L_MINUS_R:
                    values[i] = 0.0;
                    break;
                case G:
                case CROSS_G:
                    values[i] = 1.0 - Math.exp(
                            -targetIntensity * Math.PI * radius * radius);
                    break;
                case PAIR_CORRELATION:
                    values[i] = 1.0;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported pattern function: " + function);
            }
        }
        return values;
    }

    static double[] evaluate(PatternFunction function,
                             double[][] source,
                             double[][] target,
                             RectangularWindow window,
                             double[] radii,
                             EdgeCorrection correction) {
        switch (function) {
            case K:
                return computeK(source, window, radii, correction);
            case L:
                return computeL(computeK(source, window, radii, correction));
            case L_MINUS_R:
                return computeLMinusR(
                        computeK(source, window, radii, correction), radii);
            case G:
                return computeG(source, radii);
            case PAIR_CORRELATION:
                return computePairCorrelation(source, window, radii, correction);
            case CROSS_K:
                return computeCrossK(source, target, window, radii, correction);
            case CROSS_L:
                return computeL(
                        computeCrossK(source, target, window, radii, correction));
            case CROSS_G:
                return computeCrossG(source, target, radii);
            default:
                throw new IllegalArgumentException(
                        "Unsupported pattern function: " + function);
        }
    }

    private static double pairK(double[][] source,
                                double[][] target,
                                boolean self,
                                RectangularWindow window,
                                double radius,
                                EdgeCorrection correction) {
        double weightedPairs = 0.0;
        for (int sourceIndex = 0; sourceIndex < source.length; sourceIndex++) {
            for (int targetIndex = 0; targetIndex < target.length; targetIndex++) {
                if (self && sourceIndex == targetIndex) continue;
                double dx = target[targetIndex][0] - source[sourceIndex][0];
                double dy = target[targetIndex][1] - source[sourceIndex][1];
                if (squaredDistance(dx, dy) > radius * radius) continue;
                if (correction == EdgeCorrection.TRANSLATION) {
                    double overlap = window.translationOverlap(dx, dy);
                    if (overlap > 0.0) weightedPairs += window.area() / overlap;
                } else {
                    weightedPairs += 1.0;
                }
            }
        }
        double denominator = self
                ? source.length * (double) (source.length - 1)
                : source.length * (double) target.length;
        return window.area() * weightedPairs / denominator;
    }

    private static double borderK(double[][] source,
                                  double[][] target,
                                  boolean self,
                                  RectangularWindow window,
                                  double radius) {
        int eligibleSources = 0;
        int pairCount = 0;
        double squaredRadius = radius * radius;
        for (int sourceIndex = 0; sourceIndex < source.length; sourceIndex++) {
            if (window.boundaryDistance(
                    source[sourceIndex][0], source[sourceIndex][1]) < radius) {
                continue;
            }
            eligibleSources++;
            for (int targetIndex = 0; targetIndex < target.length; targetIndex++) {
                if (self && sourceIndex == targetIndex) continue;
                double dx = target[targetIndex][0] - source[sourceIndex][0];
                double dy = target[targetIndex][1] - source[sourceIndex][1];
                if (squaredDistance(dx, dy) <= squaredRadius) pairCount++;
            }
        }
        if (eligibleSources == 0) return Double.NaN;
        int possibleTargets = self ? target.length - 1 : target.length;
        if (possibleTargets <= 0) return 0.0;
        return window.area() * pairCount
                / (eligibleSources * (double) possibleTargets);
    }

    private static double[] nearestDistances(double[][] source,
                                             double[][] target,
                                             boolean self) {
        double[] distances = new double[source.length];
        for (int sourceIndex = 0; sourceIndex < source.length; sourceIndex++) {
            double minimum = Double.POSITIVE_INFINITY;
            for (int targetIndex = 0; targetIndex < target.length; targetIndex++) {
                if (self && sourceIndex == targetIndex) continue;
                double dx = target[targetIndex][0] - source[sourceIndex][0];
                double dy = target[targetIndex][1] - source[sourceIndex][1];
                double distance = Math.sqrt(squaredDistance(dx, dy));
                if (distance < minimum) minimum = distance;
            }
            distances[sourceIndex] = minimum;
        }
        return distances;
    }

    private static double[] empiricalCdf(double[] distances, double[] radii) {
        double[] values = new double[radii.length];
        if (distances.length == 0) return values;
        for (int radiusIndex = 0; radiusIndex < radii.length; radiusIndex++) {
            int count = 0;
            for (double distance : distances) {
                if (distance <= radii[radiusIndex]) count++;
            }
            values[radiusIndex] = count / (double) distances.length;
        }
        return values;
    }

    private static double squaredDistance(double dx, double dy) {
        return dx * dx + dy * dy;
    }

    private static double[] nanArray(int length) {
        double[] values = new double[length];
        java.util.Arrays.fill(values, Double.NaN);
        return values;
    }

    private static void validateInputs(double[][] points,
                                       RectangularWindow window,
                                       double[] radii,
                                       EdgeCorrection correction) {
        if (window == null) {
            throw new IllegalArgumentException("Observation window must not be null.");
        }
        if (correction == null) {
            throw new IllegalArgumentException("Edge correction must not be null.");
        }
        validateRadii(radii);
        validatePoints(points, window, "points");
    }

    private static void validatePoints(double[][] points,
                                       RectangularWindow window,
                                       String name) {
        validateFinitePoints(points, name);
        for (int i = 0; i < points.length; i++) {
            if (!window.contains(points[i][0], points[i][1])) {
                throw new IllegalArgumentException(
                        name + " point " + i + " lies outside the observation window.");
            }
        }
    }

    private static void validateFinitePoints(double[][] points, String name) {
        if (points == null) {
            throw new IllegalArgumentException(name + " must not be null.");
        }
        for (int i = 0; i < points.length; i++) {
            if (points[i] == null || points[i].length < 2
                    || !Double.isFinite(points[i][0])
                    || !Double.isFinite(points[i][1])) {
                throw new IllegalArgumentException(
                        name + " point " + i + " must have finite x and y coordinates.");
            }
        }
    }

    private static void validateRadii(double[] radii) {
        if (radii == null) {
            throw new IllegalArgumentException("Radii must not be null.");
        }
        double previous = -1.0;
        for (int i = 0; i < radii.length; i++) {
            if (!Double.isFinite(radii[i]) || radii[i] < 0.0) {
                throw new IllegalArgumentException(
                        "Radii must be finite and non-negative.");
            }
            if (radii[i] < previous) {
                throw new IllegalArgumentException(
                        "Radii must be sorted in ascending order.");
            }
            previous = radii[i];
        }
    }
}
