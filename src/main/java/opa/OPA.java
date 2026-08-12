/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import sc.fiji.opa.core.geometry.ChannelGeometry;
import sc.fiji.opa.core.geometry.DirectionResult;
import sc.fiji.opa.core.geometry.LabelGeometryExtractor;
import sc.fiji.opa.core.geometry.ProximityEngine;
import sc.fiji.opa.core.spatial.EdgeCorrection;
import sc.fiji.opa.core.spatial.MonteCarloAnalyzer;
import sc.fiji.opa.core.spatial.MonteCarloResult;
import sc.fiji.opa.core.spatial.PatternFunction;
import sc.fiji.opa.core.spatial.RectangularWindow;
import sc.fiji.opa.core.AnalysisCancelledException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Public Java facade for dialog-free, display-free analysis.
 */
public final class OPA {

    private OPA() {
    }

    public static OPAResult run(ImagePlus image) {
        return run(OPAParameters.builder(image).build());
    }

    public static OPAResult run(ImagePlus first, ImagePlus second) {
        return run(OPAParameters.builder(first, second).build());
    }

    public static OPAResult run(List<ImagePlus> images) {
        return run(OPAParameters.builder(images).build());
    }

    public static OPAResult run(OPAParameters parameters) {
        AnalysisCancelledException.check();
        reportProgress(parameters, 0.0, "Validating inputs");
        validate(parameters);
        List<ChannelGeometry> channels = extractChannels(parameters);
        AnalysisCancelledException.check();
        validateChannels(parameters, channels);
        channels = applyObservationWindow(parameters, channels);
        reportProgress(parameters, 0.1, "Input geometry extracted");

        double distanceEnd = parameters.isRunPattern() ? 0.3 : 0.95;
        List<DirectionResult> directions = parameters.isRunDistances()
                ? analyzeDistances(parameters, channels, 0.1, distanceEnd)
                : new ArrayList<DirectionResult>();
        AnalysisCancelledException.check();
        double patternStart = parameters.isRunDistances() ? distanceEnd : 0.1;
        List<PatternResult> patterns = parameters.isRunPattern()
                ? analyzePatterns(parameters, channels, patternStart, 0.98)
                : new ArrayList<PatternResult>();
        AnalysisCancelledException.check();
        OPAResult result = new OPAResult(
                parameters, channels, directions, patterns);
        AnalysisCancelledException.check();
        reportProgress(parameters, 1.0, "Analysis complete");
        AnalysisCancelledException.check();
        return result;
    }

    private static List<ChannelGeometry> extractChannels(OPAParameters parameters) {
        List<ChannelGeometry> channels = new ArrayList<ChannelGeometry>();
        List<String> names = effectiveChannelNames(parameters);
        for (int i = 0; i < parameters.getImages().size(); i++) {
            AnalysisCancelledException.check();
            ImagePlus image = parameters.getImages().get(i);
            channels.add(LabelGeometryExtractor.extract(image, names.get(i)));
        }
        return channels;
    }

    private static List<String> effectiveChannelNames(OPAParameters parameters) {
        List<String> names =
                new ArrayList<String>(parameters.getImages().size());
        Set<String> used = new HashSet<String>();
        for (int i = 0; i < parameters.getImages().size(); i++) {
            ImagePlus image = parameters.getImages().get(i);
            String candidate = i < parameters.getChannelNames().size()
                    ? parameters.getChannelNames().get(i)
                    : image.getTitle();
            String base = candidate == null || candidate.trim().isEmpty()
                    ? "Channel_" + (i + 1)
                    : candidate.trim();
            String unique = base;
            int suffix = 2;
            while (!used.add(unique)) {
                unique = base + " [channel " + suffix + "]";
                suffix++;
            }
            names.add(unique);
        }
        return names;
    }

    private static List<DirectionResult> analyzeDistances(
            OPAParameters parameters,
            List<ChannelGeometry> channels,
            double progressStart,
            double progressEnd) {
        List<DirectionResult> results = new ArrayList<DirectionResult>();
        int total = channels.size() * (channels.size() - 1)
                + (parameters.isIncludeSelfDistances() ? channels.size() : 0);
        int completed = 0;
        for (int source = 0; source < channels.size(); source++) {
            AnalysisCancelledException.check();
            if (parameters.isIncludeSelfDistances()) {
                results.add(ProximityEngine.analyze(
                        channels.get(source),
                        channels.get(source),
                        parameters.getDistanceModes(),
                        parameters.getNeighborCount(),
                        parameters.getContactDistance()));
                completed++;
                reportProgress(parameters,
                        progress(progressStart, progressEnd, completed, total),
                        "Distance direction " + completed + " of " + total);
            }
            for (int target = 0; target < channels.size(); target++) {
                if (source == target) continue;
                results.add(ProximityEngine.analyze(
                        channels.get(source),
                        channels.get(target),
                        parameters.getDistanceModes(),
                        parameters.getNeighborCount(),
                        parameters.getContactDistance()));
                completed++;
                reportProgress(parameters,
                        progress(progressStart, progressEnd, completed, total),
                        "Distance direction " + completed + " of " + total);
            }
        }
        return results;
    }

    private static List<ChannelGeometry> applyObservationWindow(
            OPAParameters parameters,
            List<ChannelGeometry> channels) {
        if (parameters.getObservationWindow() == null) return channels;
        List<ChannelGeometry> filtered =
                new ArrayList<ChannelGeometry>(channels.size());
        for (ChannelGeometry channel : channels) {
            filtered.add(channel.within(parameters.getObservationWindow()));
        }
        return filtered;
    }

    private static List<PatternResult> analyzePatterns(
            OPAParameters parameters,
            List<ChannelGeometry> channels,
            double progressStart,
            double progressEnd) {
        ChannelGeometry first = channels.get(0);
        RectangularWindow window = parameters.getObservationWindow() == null
                ? new RectangularWindow(
                        first.getCalibration().xEdge(0.0),
                        first.getCalibration().yEdge(0.0),
                        first.getCalibration().xEdge(first.getWidth()),
                        first.getCalibration().yEdge(first.getHeight()))
                : parameters.getObservationWindow();
        double[] radii = resolveRadii(parameters, window);
        List<PatternResult> results = new ArrayList<PatternResult>();
        int totalJobs = patternJobCount(
                parameters.getPatternFunctions(), channels.size());
        int completedJobs = 0;

        for (PatternFunction function : parameters.getPatternFunctions()) {
            AnalysisCancelledException.check();
            if (!function.isBivariate()) {
                for (ChannelGeometry channel : channels) {
                    OPAProgressListener listener = progressSegment(
                            parameters,
                            progressStart,
                            progressEnd,
                            completedJobs,
                            totalJobs);
                    MonteCarloResult statistics =
                            MonteCarloAnalyzer.analyzeUnivariate(
                                    function,
                                    channel.centroidPoints2D(),
                                    window,
                                    radii,
                                    parameters.getEdgeCorrection(),
                                    parameters.getSimulations(),
                                    parameters.getSeed(),
                                    listener);
                    results.add(new PatternResult(
                            channel.getName(),
                            null,
                            channel.getCalibration().getUnit(),
                            statistics));
                    completedJobs++;
                }
            } else {
                for (int source = 0; source < channels.size(); source++) {
                    for (int target = 0; target < channels.size(); target++) {
                        if (source == target) continue;
                        OPAProgressListener listener = progressSegment(
                                parameters,
                                progressStart,
                                progressEnd,
                                completedJobs,
                                totalJobs);
                        ChannelGeometry sourceChannel = channels.get(source);
                        ChannelGeometry targetChannel = channels.get(target);
                        MonteCarloResult statistics =
                                MonteCarloAnalyzer.analyzeBivariate(
                                        function,
                                        sourceChannel.centroidPoints2D(),
                                        targetChannel.centroidPoints2D(),
                                        window,
                                        radii,
                                        parameters.getEdgeCorrection(),
                                        parameters.getSimulations(),
                                        parameters.getSeed(),
                                        listener);
                        results.add(new PatternResult(
                                sourceChannel.getName(),
                                targetChannel.getName(),
                                sourceChannel.getCalibration().getUnit(),
                                statistics));
                        completedJobs++;
                    }
                }
            }
        }
        reportProgress(parameters, progressEnd, "Point-pattern analysis complete");
        return results;
    }

    private static int patternJobCount(Iterable<PatternFunction> functions,
                                       int channelCount) {
        int count = 0;
        for (PatternFunction function : functions) {
            count += function.isBivariate()
                    ? channelCount * (channelCount - 1)
                    : channelCount;
        }
        return count;
    }

    private static OPAProgressListener progressSegment(
            final OPAParameters parameters,
            final double start,
            final double end,
            int jobIndex,
            int jobCount) {
        if (parameters.getProgressListener() == null || jobCount == 0) {
            return null;
        }
        final double jobStart = progress(start, end, jobIndex, jobCount);
        final double jobEnd = progress(start, end, jobIndex + 1, jobCount);
        return new OPAProgressListener() {
            @Override
            public void onProgress(double fraction, String message) {
                reportProgress(
                        parameters,
                        jobStart + (jobEnd - jobStart) * fraction,
                        message);
            }
        };
    }

    private static double progress(double start,
                                   double end,
                                   int completed,
                                   int total) {
        return total == 0
                ? end
                : start + (end - start) * completed / total;
    }

    private static void reportProgress(OPAParameters parameters,
                                       double fraction,
                                       String message) {
        if (parameters == null || parameters.getProgressListener() == null) {
            return;
        }
        parameters.getProgressListener().onProgress(
                Math.max(0.0, Math.min(1.0, fraction)),
                message);
    }

    private static double[] resolveRadii(OPAParameters parameters,
                                         RectangularWindow window) {
        double[] explicit = parameters.getRadii();
        if (explicit != null) return explicit;
        double maximum = parameters.getMaximumRadius() > 0.0
                ? parameters.getMaximumRadius()
                : Math.min(window.width(), window.height()) / 4.0;
        double[] radii = new double[parameters.getRadiusBins()];
        for (int i = 0; i < radii.length; i++) {
            radii[i] = maximum * (i + 1) / radii.length;
        }
        return radii;
    }

    private static void validate(OPAParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("OPA parameters must not be null.");
        }
        List<ImagePlus> images = parameters.getImages();
        if (images.size() < OPAParameters.MIN_IMAGES
                || images.size() > OPAParameters.MAX_IMAGES) {
            throw new IllegalArgumentException(
                    "Object Proximity Analysis requires 1 to 5 label images.");
        }
        validateOptions(parameters, images.size());
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i) == null || images.get(i).getStack() == null) {
                throw new IllegalArgumentException(
                        "Label image " + (i + 1) + " has no image stack.");
            }
        }
    }

    static void validateOptions(OPAParameters parameters, int imageCount) {
        if (parameters == null) {
            throw new IllegalArgumentException("OPA parameters must not be null.");
        }
        if (imageCount < OPAParameters.MIN_IMAGES
                || imageCount > OPAParameters.MAX_IMAGES) {
            throw new IllegalArgumentException(
                    "Object Proximity Analysis requires 1 to 5 label images.");
        }
        if (!parameters.isRunDistances() && !parameters.isRunPattern()) {
            throw new IllegalArgumentException(
                    "At least one of distance or pattern analysis must be enabled.");
        }
        if (parameters.isRunDistances()) {
            if (parameters.getDistanceModes().isEmpty()) {
                throw new IllegalArgumentException(
                        "At least one distance mode must be enabled.");
            }
            if (imageCount == 1 && !parameters.isIncludeSelfDistances()) {
                throw new IllegalArgumentException(
                        "A one-channel distance analysis requires self-distances; "
                                + "enable self-distances or disable distance analysis.");
            }
            if (parameters.getNeighborCount() < 1
                    || parameters.getNeighborCount()
                    > OPAParameters.MAX_NEIGHBOR_COUNT) {
                throw new IllegalArgumentException(
                        "Neighbor count must be between 1 and "
                                + OPAParameters.MAX_NEIGHBOR_COUNT + ".");
            }
            if (!Double.isFinite(parameters.getContactDistance())
                    || parameters.getContactDistance() < 0.0) {
                throw new IllegalArgumentException(
                        "Contact distance must be finite and non-negative.");
            }
            if (parameters.getHistogramBins() < 1
                    || parameters.getHistogramBins()
                    > OPAParameters.MAX_HISTOGRAM_BINS) {
                throw new IllegalArgumentException(
                        "Histogram bin count must be between 1 and "
                                + OPAParameters.MAX_HISTOGRAM_BINS + ".");
            }
        }
        if (parameters.isRunPattern()) {
            if (parameters.getPatternFunctions().isEmpty()) {
                throw new IllegalArgumentException(
                        "At least one point-pattern function must be enabled.");
            }
            if (!hasExecutablePatternFunction(
                    parameters.getPatternFunctions(), imageCount)) {
                throw new IllegalArgumentException(
                        "Cross-pattern functions require at least two label images; "
                                + "enable a univariate function or add another image.");
            }
            if (parameters.getSimulations() < 1
                    || parameters.getSimulations()
                    > OPAParameters.MAX_SIMULATIONS) {
                throw new IllegalArgumentException(
                        "Simulation count must be between 1 and "
                                + OPAParameters.MAX_SIMULATIONS + ".");
            }
            if (parameters.getEdgeCorrection() == null) {
                throw new IllegalArgumentException(
                        "Edge correction must not be null.");
            }
            // Both forms of the statistic carry the same restriction. A rule
            // that held for g(r) but not for cross-g(r) would be worse than
            // the restriction itself.
            if (parameters.getEdgeCorrection()
                    == EdgeCorrection.BORDER
                    && (parameters.getPatternFunctions().contains(
                            PatternFunction.PAIR_CORRELATION)
                        || parameters.getPatternFunctions().contains(
                            PatternFunction.CROSS_PAIR_CORRELATION))) {
                throw new IllegalArgumentException(
                        "Pair correlation cannot use border correction; "
                                + "choose translation or no edge correction, "
                                + "or disable pair correlation.");
            }
            double[] radii = parameters.getRadii();
            if (radii == null) {
                if (parameters.getRadiusBins() < 1
                        || parameters.getRadiusBins()
                        > OPAParameters.MAX_RADIUS_BINS) {
                    throw new IllegalArgumentException(
                            "Radius bin count must be between 1 and "
                                    + OPAParameters.MAX_RADIUS_BINS + ".");
                }
                if (!Double.isFinite(parameters.getMaximumRadius())
                        || parameters.getMaximumRadius() < 0.0) {
                    throw new IllegalArgumentException(
                            "Maximum radius must be finite and non-negative.");
                }
            } else {
                validateRadii(radii);
                if (radii.length > OPAParameters.MAX_RADIUS_BINS) {
                    throw new IllegalArgumentException(
                            "Pattern radius count must not exceed "
                                    + OPAParameters.MAX_RADIUS_BINS + ".");
                }
            }
            int radiusCount = radii == null
                    ? parameters.getRadiusBins()
                    : radii.length;
            if ((long) parameters.getSimulations() * radiusCount
                    > OPAParameters.MAX_MONTE_CARLO_VALUES) {
                throw new IllegalArgumentException(
                        "Monte Carlo simulations multiplied by radius count "
                                + "must not exceed "
                                + OPAParameters.MAX_MONTE_CARLO_VALUES + ".");
            }
        }
    }

    private static void validateRadii(double[] radii) {
        if (radii.length == 0) {
            throw new IllegalArgumentException(
                    "At least one pattern radius is required.");
        }
        double previous = Double.NEGATIVE_INFINITY;
        for (double radius : radii) {
            if (!Double.isFinite(radius) || radius < 0.0
                    || radius <= previous) {
                throw new IllegalArgumentException(
                        "Pattern radii must be finite, non-negative, "
                                + "and strictly increasing.");
            }
            previous = radius;
        }
    }

    private static boolean hasExecutablePatternFunction(
            Iterable<PatternFunction> functions,
            int imageCount) {
        for (PatternFunction function : functions) {
            if (!function.isBivariate() || imageCount > 1) return true;
        }
        return false;
    }

    private static void validateChannels(OPAParameters parameters,
                                         List<ChannelGeometry> channels) {
        ChannelGeometry first = channels.get(0);
        for (int i = 0; i < channels.size(); i++) {
            ChannelGeometry channel = channels.get(i);
            if (channel.getWidth() != first.getWidth()
                    || channel.getHeight() != first.getHeight()
                    || channel.getDepth() != first.getDepth()) {
                throw new IllegalArgumentException(
                        "All label images must have identical dimensions.");
            }
            if (!channel.getCalibration().isCompatibleWith(first.getCalibration())) {
                throw new IllegalArgumentException(
                        "All label images must have identical voxel calibration, "
                                + "spatial origins, and units.");
            }
            if (parameters.isRequirePhysicalCalibration()
                    && !channel.getCalibration().hasPhysicalUnits()) {
                throw new IllegalArgumentException(
                        "Physical voxel calibration is required; image "
                                + (i + 1) + " is calibrated in pixels.");
            }
            if (parameters.isRunPattern()
                    && channel.getDepth() > 1
                    && !parameters.isProject3DToXY()) {
                throw new IllegalArgumentException(
                        "Point-pattern analysis is 2D in v0.2.0. "
                                + "For a 3D label image, explicitly enable XY projection "
                                + "or run distance analysis only.");
            }
        }
        if (parameters.getObservationWindow() != null) {
            RectangularWindow window = parameters.getObservationWindow();
            double minimumX = first.getCalibration().xEdge(0.0);
            double minimumY = first.getCalibration().yEdge(0.0);
            double maximumX = first.getCalibration().xEdge(first.getWidth());
            double maximumY = first.getCalibration().yEdge(first.getHeight());
            if (window.getMinX() < minimumX
                    || window.getMinY() < minimumY
                    || window.getMaxX() > maximumX
                    || window.getMaxY() > maximumY) {
                throw new IllegalArgumentException(
                        "Observation window must lie within the calibrated XY image bounds.");
            }
        }
    }
}
