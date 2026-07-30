/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import opa.spatial.EdgeCorrection;
import opa.spatial.PatternFunction;
import opa.spatial.RectangularWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Immutable input bundle for headless Object Proximity Analysis.
 */
public final class OPAParameters {

    public static final int MIN_IMAGES = 1;
    public static final int MAX_IMAGES = 5;
    public static final long DEFAULT_SEED = 0x0B1EC7L;

    private final List<ImagePlus> images;
    private final List<String> channelNames;
    private final boolean runDistances;
    private final boolean runPattern;
    private final boolean includeSelfDistances;
    private final EnumSet<DistanceMode> distanceModes;
    private final int neighborCount;
    private final double contactDistance;
    private final EnumSet<PatternFunction> patternFunctions;
    private final double[] radii;
    private final double maximumRadius;
    private final int radiusBins;
    private final int simulations;
    private final long seed;
    private final EdgeCorrection edgeCorrection;
    private final boolean project3DToXY;
    private final int histogramBins;
    private final boolean requirePhysicalCalibration;
    private final RectangularWindow observationWindow;

    private OPAParameters(Builder builder) {
        this.images = immutableCopy(builder.images);
        this.channelNames = immutableCopy(builder.channelNames);
        this.runDistances = builder.runDistances;
        this.runPattern = builder.runPattern;
        this.includeSelfDistances = builder.includeSelfDistances;
        this.distanceModes = copy(builder.distanceModes);
        this.neighborCount = builder.neighborCount;
        this.contactDistance = builder.contactDistance;
        this.patternFunctions = copy(builder.patternFunctions);
        this.radii = builder.radii == null ? null : builder.radii.clone();
        this.maximumRadius = builder.maximumRadius;
        this.radiusBins = builder.radiusBins;
        this.simulations = builder.simulations;
        this.seed = builder.seed;
        this.edgeCorrection = builder.edgeCorrection;
        this.project3DToXY = builder.project3DToXY;
        this.histogramBins = builder.histogramBins;
        this.requirePhysicalCalibration = builder.requirePhysicalCalibration;
        this.observationWindow = builder.observationWindow;
    }

    public static Builder builder(List<ImagePlus> images) {
        return new Builder().images(images);
    }

    /**
     * Starts an option-only builder. A batch runner can supply images later.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static Builder builderFrom(OPAParameters template) {
        if (template == null) {
            throw new IllegalArgumentException("Parameter template must not be null.");
        }
        return new Builder()
                .images(template.images)
                .channelNames(template.channelNames)
                .runDistances(template.runDistances)
                .runPattern(template.runPattern)
                .includeSelfDistances(template.includeSelfDistances)
                .distanceModes(template.distanceModes)
                .neighborCount(template.neighborCount)
                .contactDistance(template.contactDistance)
                .patternFunctions(template.patternFunctions)
                .radii(template.radii)
                .maximumRadius(template.maximumRadius)
                .radiusBins(template.radiusBins)
                .simulations(template.simulations)
                .seed(template.seed)
                .edgeCorrection(template.edgeCorrection)
                .project3DToXY(template.project3DToXY)
                .histogramBins(template.histogramBins)
                .requirePhysicalCalibration(template.requirePhysicalCalibration)
                .observationWindow(template.observationWindow);
    }

    public static Builder builder(ImagePlus image) {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        images.add(image);
        return builder(images);
    }

    public static Builder builder(ImagePlus first, ImagePlus second) {
        List<ImagePlus> images = new ArrayList<ImagePlus>();
        images.add(first);
        images.add(second);
        return builder(images);
    }

    public List<ImagePlus> getImages() {
        return images;
    }

    public List<String> getChannelNames() {
        return channelNames;
    }

    public boolean isRunDistances() {
        return runDistances;
    }

    public boolean isRunPattern() {
        return runPattern;
    }

    public boolean isIncludeSelfDistances() {
        return includeSelfDistances;
    }

    public EnumSet<DistanceMode> getDistanceModes() {
        return copy(distanceModes);
    }

    public int getNeighborCount() {
        return neighborCount;
    }

    public double getContactDistance() {
        return contactDistance;
    }

    public EnumSet<PatternFunction> getPatternFunctions() {
        return copy(patternFunctions);
    }

    public double[] getRadii() {
        return radii == null ? null : radii.clone();
    }

    public double getMaximumRadius() {
        return maximumRadius;
    }

    public int getRadiusBins() {
        return radiusBins;
    }

    public int getSimulations() {
        return simulations;
    }

    public long getSeed() {
        return seed;
    }

    public EdgeCorrection getEdgeCorrection() {
        return edgeCorrection;
    }

    public boolean isProject3DToXY() {
        return project3DToXY;
    }

    public int getHistogramBins() {
        return histogramBins;
    }

    public boolean isRequirePhysicalCalibration() {
        return requirePhysicalCalibration;
    }

    /**
     * Optional calibrated rectangular observation window. When absent, the
     * complete XY image bounds are used.
     */
    public RectangularWindow getObservationWindow() {
        return observationWindow;
    }

    private static <T> List<T> immutableCopy(List<T> input) {
        if (input == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<T>(input));
    }

    private static <E extends Enum<E>> EnumSet<E> copy(EnumSet<E> input) {
        return input.clone();
    }

    public static final class Builder {
        private List<ImagePlus> images = new ArrayList<ImagePlus>();
        private List<String> channelNames = new ArrayList<String>();
        private boolean runDistances = true;
        private boolean runPattern = true;
        private boolean includeSelfDistances = true;
        private EnumSet<DistanceMode> distanceModes =
                EnumSet.allOf(DistanceMode.class);
        private int neighborCount = 1;
        private double contactDistance = 0.0;
        private EnumSet<PatternFunction> patternFunctions =
                EnumSet.allOf(PatternFunction.class);
        private double[] radii;
        private double maximumRadius = 0.0;
        private int radiusBins = 50;
        private int simulations = 99;
        private long seed = DEFAULT_SEED;
        private EdgeCorrection edgeCorrection = EdgeCorrection.TRANSLATION;
        private boolean project3DToXY = false;
        private int histogramBins = 20;
        private boolean requirePhysicalCalibration = false;
        private RectangularWindow observationWindow;

        private Builder() {
        }

        public Builder images(List<ImagePlus> images) {
            this.images = images == null
                    ? new ArrayList<ImagePlus>()
                    : new ArrayList<ImagePlus>(images);
            return this;
        }

        public Builder channelNames(List<String> channelNames) {
            this.channelNames = channelNames == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(channelNames);
            return this;
        }

        public Builder runDistances(boolean runDistances) {
            this.runDistances = runDistances;
            return this;
        }

        public Builder runPattern(boolean runPattern) {
            this.runPattern = runPattern;
            return this;
        }

        public Builder includeSelfDistances(boolean includeSelfDistances) {
            this.includeSelfDistances = includeSelfDistances;
            return this;
        }

        public Builder distanceModes(EnumSet<DistanceMode> distanceModes) {
            this.distanceModes = distanceModes == null
                    ? EnumSet.noneOf(DistanceMode.class)
                    : copy(distanceModes);
            return this;
        }

        public Builder neighborCount(int neighborCount) {
            this.neighborCount = neighborCount;
            return this;
        }

        public Builder contactDistance(double contactDistance) {
            this.contactDistance = contactDistance;
            return this;
        }

        public Builder patternFunctions(EnumSet<PatternFunction> patternFunctions) {
            this.patternFunctions = patternFunctions == null
                    ? EnumSet.noneOf(PatternFunction.class)
                    : copy(patternFunctions);
            return this;
        }

        public Builder radii(double[] radii) {
            this.radii = radii == null ? null : radii.clone();
            return this;
        }

        public Builder maximumRadius(double maximumRadius) {
            this.maximumRadius = maximumRadius;
            return this;
        }

        public Builder radiusBins(int radiusBins) {
            this.radiusBins = radiusBins;
            return this;
        }

        public Builder simulations(int simulations) {
            this.simulations = simulations;
            return this;
        }

        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        public Builder edgeCorrection(EdgeCorrection edgeCorrection) {
            this.edgeCorrection = edgeCorrection;
            return this;
        }

        public Builder project3DToXY(boolean project3DToXY) {
            this.project3DToXY = project3DToXY;
            return this;
        }

        public Builder histogramBins(int histogramBins) {
            this.histogramBins = histogramBins;
            return this;
        }

        public Builder requirePhysicalCalibration(boolean requirePhysicalCalibration) {
            this.requirePhysicalCalibration = requirePhysicalCalibration;
            return this;
        }

        public Builder observationWindow(RectangularWindow observationWindow) {
            this.observationWindow = observationWindow;
            return this;
        }

        public OPAParameters build() {
            return new OPAParameters(this);
        }
    }
}
