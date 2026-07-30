/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import opa.geometry.ChannelGeometry;
import opa.geometry.DirectionResult;
import opa.geometry.LabelGeometryExtractor;
import opa.geometry.ProximityEngine;
import opa.spatial.MonteCarloAnalyzer;
import opa.spatial.MonteCarloResult;
import opa.spatial.PatternFunction;
import opa.spatial.RectangularWindow;

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
        validate(parameters);
        List<ChannelGeometry> channels = extractChannels(parameters);
        validateChannels(parameters, channels);
        channels = applyObservationWindow(parameters, channels);

        List<DirectionResult> directions = parameters.isRunDistances()
                ? analyzeDistances(parameters, channels)
                : new ArrayList<DirectionResult>();
        List<PatternResult> patterns = parameters.isRunPattern()
                ? analyzePatterns(parameters, channels)
                : new ArrayList<PatternResult>();
        return new OPAResult(parameters, channels, directions, patterns);
    }

    private static List<ChannelGeometry> extractChannels(OPAParameters parameters) {
        List<ChannelGeometry> channels = new ArrayList<ChannelGeometry>();
        List<String> names = effectiveChannelNames(parameters);
        for (int i = 0; i < parameters.getImages().size(); i++) {
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
            List<ChannelGeometry> channels) {
        List<DirectionResult> results = new ArrayList<DirectionResult>();
        for (int source = 0; source < channels.size(); source++) {
            if (parameters.isIncludeSelfDistances()) {
                results.add(ProximityEngine.analyze(
                        channels.get(source),
                        channels.get(source),
                        parameters.getDistanceModes(),
                        parameters.getNeighborCount(),
                        parameters.getContactDistance()));
            }
            for (int target = 0; target < channels.size(); target++) {
                if (source == target) continue;
                results.add(ProximityEngine.analyze(
                        channels.get(source),
                        channels.get(target),
                        parameters.getDistanceModes(),
                        parameters.getNeighborCount(),
                        parameters.getContactDistance()));
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
            List<ChannelGeometry> channels) {
        ChannelGeometry first = channels.get(0);
        RectangularWindow window = parameters.getObservationWindow() == null
                ? new RectangularWindow(
                        0.0,
                        0.0,
                        first.getWidth() * first.getCalibration().getPixelWidth(),
                        first.getHeight() * first.getCalibration().getPixelHeight())
                : parameters.getObservationWindow();
        double[] radii = resolveRadii(parameters, window);
        List<PatternResult> results = new ArrayList<PatternResult>();

        for (PatternFunction function : parameters.getPatternFunctions()) {
            if (!function.isBivariate()) {
                for (ChannelGeometry channel : channels) {
                    MonteCarloResult statistics =
                            MonteCarloAnalyzer.analyzeUnivariate(
                                    function,
                                    channel.centroidPoints2D(),
                                    window,
                                    radii,
                                    parameters.getEdgeCorrection(),
                                    parameters.getSimulations(),
                                    parameters.getSeed());
                    results.add(new PatternResult(
                            channel.getName(),
                            null,
                            channel.getCalibration().getUnit(),
                            statistics));
                }
            } else {
                for (int source = 0; source < channels.size(); source++) {
                    for (int target = 0; target < channels.size(); target++) {
                        if (source == target) continue;
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
                                        parameters.getSeed());
                        results.add(new PatternResult(
                                sourceChannel.getName(),
                                targetChannel.getName(),
                                sourceChannel.getCalibration().getUnit(),
                                statistics));
                    }
                }
            }
        }
        return results;
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
            if (parameters.getNeighborCount() < 1) {
                throw new IllegalArgumentException(
                        "Neighbor count must be at least 1.");
            }
            if (!Double.isFinite(parameters.getContactDistance())
                    || parameters.getContactDistance() < 0.0) {
                throw new IllegalArgumentException(
                        "Contact distance must be finite and non-negative.");
            }
            if (parameters.getHistogramBins() < 1) {
                throw new IllegalArgumentException(
                        "Histogram bin count must be at least 1.");
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
            if (parameters.getSimulations() < 1) {
                throw new IllegalArgumentException(
                        "Simulation count must be at least 1.");
            }
            if (parameters.getEdgeCorrection() == null) {
                throw new IllegalArgumentException(
                        "Edge correction must not be null.");
            }
            double[] radii = parameters.getRadii();
            if (radii == null) {
                if (parameters.getRadiusBins() < 1) {
                    throw new IllegalArgumentException(
                            "Radius bin count must be at least 1.");
                }
                if (!Double.isFinite(parameters.getMaximumRadius())
                        || parameters.getMaximumRadius() < 0.0) {
                    throw new IllegalArgumentException(
                            "Maximum radius must be finite and non-negative.");
                }
            } else {
                validateRadii(radii);
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
                    || radius < previous) {
                throw new IllegalArgumentException(
                        "Pattern radii must be finite, non-negative, "
                                + "and sorted in ascending order.");
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
                        "Point-pattern analysis is 2D in v0.1.0. "
                                + "For a 3D label image, explicitly enable XY projection "
                                + "or run distance analysis only.");
            }
        }
        if (parameters.getObservationWindow() != null) {
            RectangularWindow window = parameters.getObservationWindow();
            double maximumX = first.getWidth()
                    * first.getCalibration().getPixelWidth();
            double maximumY = first.getHeight()
                    * first.getCalibration().getPixelHeight();
            if (window.getMinX() < 0.0 || window.getMinY() < 0.0
                    || window.getMaxX() > maximumX
                    || window.getMaxY() > maximumY) {
                throw new IllegalArgumentException(
                        "Observation window must lie within the calibrated XY image bounds.");
            }
        }
    }
}
