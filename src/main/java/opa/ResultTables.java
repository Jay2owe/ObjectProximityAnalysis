/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.measure.ResultsTable;
import sc.fiji.opa.core.geometry.ChannelGeometry;
import sc.fiji.opa.core.geometry.DirectionResult;
import sc.fiji.opa.core.geometry.NeighborMeasurement;
import sc.fiji.opa.core.geometry.ObjectGeometry;
import sc.fiji.opa.core.geometry.ObjectMeasurement;
import sc.fiji.opa.core.spatial.MonteCarloResult;
import sc.fiji.opa.core.spatial.PatternFunction;
import sc.fiji.opa.core.spatial.RectangularWindow;
import sc.fiji.opa.core.DistanceMode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts immutable engine results into ImageJ ResultsTable outputs.
 */
final class ResultTables {

    private ResultTables() {
    }

    static Map<String, ResultsTable> perObject(List<DirectionResult> directions) {
        Map<String, ResultsTable> tables = new LinkedHashMap<String, ResultsTable>();
        for (DirectionResult direction : directions) {
            ResultsTable table = new ResultsTable();
            for (ObjectMeasurement object : direction.getMeasurements()) {
                table.incrementCounter();
                addDirectionIdentity(table, direction);
                table.addValue("Source_Label", object.getSourceLabel());
                table.addValue("Edge_Object", object.isEdgeObject() ? 1 : 0);
                table.addValue("Distance_Unit", direction.getUnit());
                table.addValue("Surface_Measure_Unit",
                        direction.getSurfaceMeasureUnit());
                for (Map.Entry<DistanceMode, List<NeighborMeasurement>> entry
                        : object.getNeighborsByMode().entrySet()) {
                    for (NeighborMeasurement neighbor : entry.getValue()) {
                        String prefix = entry.getKey().getColumnName()
                                + "_NN" + neighbor.getRank();
                        table.addValue(prefix + "_Partner_Label",
                                neighbor.getPartnerLabel());
                        table.addValue(prefix + "_Value", neighbor.getValue());
                        table.addValue(prefix + "_Within_Contact",
                                neighbor.isWithinContactDistance() ? 1 : 0);
                        table.addValue(prefix + "_Exact_Contact",
                                neighbor.getExactContactArea());
                        table.addValue(prefix + "_Apposed_Surface",
                                neighbor.getApposedSurfaceArea());
                    }
                }
            }
            putUnique(tables, directionKey(direction), table);
        }
        return tables;
    }

    static Map<String, ResultsTable> centroids(
            List<ChannelGeometry> channels) {
        Map<String, ResultsTable> tables =
                new LinkedHashMap<String, ResultsTable>();
        for (ChannelGeometry channel : channels) {
            ResultsTable table = new ResultsTable();
            for (ObjectGeometry object : channel.getObjects()) {
                table.incrementCounter();
                table.addValue("Channel", channel.getName());
                table.addValue("Label", object.getLabel());
                table.addValue("Centroid_X", object.getCentroidX());
                table.addValue("Centroid_Y", object.getCentroidY());
                table.addValue("Centroid_Z", object.getCentroidZ());
                table.addValue("Edge_Object",
                        object.isEdgeObject() ? 1 : 0);
                table.addValue("Voxel_Count", object.getVoxelCount());
                table.addValue("Coordinate_Unit",
                        channel.getCalibration().getUnit());
            }
            String key = safe(channel.getName()) + "__Centroids__"
                    + identityHash("CENTROIDS", channel.getName());
            putUnique(tables, key, table);
        }
        return tables;
    }

    static ResultsTable provenance(OPAParameters parameters,
                                   List<ChannelGeometry> channels,
                                   List<PatternResult> patterns) {
        ResultsTable table = new ResultsTable();
        if (channels.isEmpty()) return table;
        ChannelGeometry first = channels.get(0);
        double[] effectiveRadii = patterns.isEmpty()
                ? null
                : patterns.get(0).getStatistics().getRadii();
        RectangularWindow requested = parameters.getObservationWindow();
        RectangularWindow window = requested == null
                ? new RectangularWindow(
                        first.getCalibration().xEdge(0.0),
                        first.getCalibration().yEdge(0.0),
                        first.getCalibration().xEdge(first.getWidth()),
                        first.getCalibration().yEdge(first.getHeight()))
                : requested;
        for (ChannelGeometry channel : channels) {
            table.incrementCounter();
            table.addValue("Channel", channel.getName());
            table.addValue("Width", channel.getWidth());
            table.addValue("Height", channel.getHeight());
            table.addValue("Depth", channel.getDepth());
            table.addValue("Pixel_Width",
                    channel.getCalibration().getPixelWidth());
            table.addValue("Pixel_Height",
                    channel.getCalibration().getPixelHeight());
            table.addValue("Pixel_Depth",
                    channel.getCalibration().getPixelDepth());
            table.addValue("Origin_X",
                    channel.getCalibration().getXOrigin());
            table.addValue("Origin_Y",
                    channel.getCalibration().getYOrigin());
            table.addValue("Origin_Z",
                    channel.getCalibration().getZOrigin());
            table.addValue("Unit", channel.getCalibration().getUnit());
            table.addValue("Physical_Calibration",
                    channel.getCalibration().hasPhysicalUnits() ? 1 : 0);
            table.addValue("Run_Distances",
                    parameters.isRunDistances() ? 1 : 0);
            table.addValue("Run_Pattern",
                    parameters.isRunPattern() ? 1 : 0);
            table.addValue("Include_Self_Distances",
                    parameters.isIncludeSelfDistances() ? 1 : 0);
            table.addValue("Distance_Modes",
                    parameters.getDistanceModes().toString());
            table.addValue("Neighbour_Count",
                    parameters.getNeighborCount());
            table.addValue("Contact_Distance",
                    parameters.getContactDistance());
            table.addValue("Pattern_Functions",
                    parameters.getPatternFunctions().toString());
            table.addValue("Requested_Radii",
                    radiiText(parameters.getRadii()));
            table.addValue("Maximum_Radius",
                    parameters.getMaximumRadius());
            table.addValue("Radius_Bins", parameters.getRadiusBins());
            table.addValue("Effective_Radii",
                    effectiveRadii == null
                            ? "NOT_RUN"
                            : radiiText(effectiveRadii));
            table.addValue("Effective_Maximum_Radius",
                    effectiveRadii == null || effectiveRadii.length == 0
                            ? Double.NaN
                            : effectiveRadii[effectiveRadii.length - 1]);
            table.addValue("Simulations", parameters.getSimulations());
            table.addValue("Seed", Long.toString(parameters.getSeed()));
            table.addValue("Edge_Correction",
                    parameters.getEdgeCorrection() == null
                            ? ""
                            : parameters.getEdgeCorrection().name());
            table.addValue("Project_3D_To_XY",
                    parameters.isProject3DToXY() ? 1 : 0);
            table.addValue("Observation_Window_Source",
                    requested == null ? "FULL_IMAGE" : "CUSTOM_RECTANGLE");
            table.addValue("Window_Min_X", window.getMinX());
            table.addValue("Window_Min_Y", window.getMinY());
            table.addValue("Window_Max_X", window.getMaxX());
            table.addValue("Window_Max_Y", window.getMaxY());
        }
        return table;
    }

    private static String radiiText(double[] radii) {
        if (radii == null) return "AUTO";
        StringBuilder text = new StringBuilder();
        for (double radius : radii) {
            if (text.length() > 0) text.append(';');
            text.append(radius);
        }
        return text.toString();
    }

    static ResultsTable distanceSummary(List<DirectionResult> directions,
                                        Iterable<DistanceMode> requestedModes,
                                        int neighborCount) {
        ResultsTable table = new ResultsTable();
        for (DirectionResult direction : directions) {
            for (DistanceMode mode : requestedModes) {
                for (int rank = 1; rank <= neighborCount; rank++) {
                    List<Double> values = values(direction, mode, rank);
                    double[] sorted = sorted(values);
                    table.incrementCounter();
                    table.addValue("Source_Channel", direction.getSourceChannel());
                    table.addValue("Target_Channel", direction.getTargetChannel());
                    table.addValue("Source_Object_Count",
                            direction.getSourceObjectCount());
                    table.addValue("Target_Object_Count",
                            direction.getTargetObjectCount());
                    table.addValue("Self_Comparison",
                            direction.isSelfComparison() ? 1 : 0);
                    table.addValue("Mode", mode.getColumnName());
                    table.addValue("Rank", rank);
                    table.addValue("Unit", measurementUnit(direction, mode));
                    table.addValue("Status",
                            distanceStatus(direction, rank, sorted.length));
                    table.addValue("N", sorted.length);
                    table.addValue("Mean",
                            sorted.length == 0 ? Double.NaN : mean(sorted));
                    table.addValue("Median",
                            sorted.length == 0 ? Double.NaN : median(sorted));
                    table.addValue("SD",
                            sorted.length == 0
                                    ? Double.NaN
                                    : standardDeviation(sorted));
                    table.addValue("Min",
                            sorted.length == 0
                                    ? Double.NaN
                                    : sorted[0]);
                    table.addValue("Max",
                            sorted.length == 0
                                    ? Double.NaN
                                    : sorted[sorted.length - 1]);
                    table.addValue("Fraction_Within_Contact",
                            fractionWithinContact(direction, mode, rank));
                }
            }
        }
        return table;
    }

    private static String distanceStatus(DirectionResult direction,
                                         int rank,
                                         int finiteValueCount) {
        if (direction.getSourceObjectCount() == 0) {
            return "NO_SOURCE_OBJECTS";
        }
        int availableTargets = direction.isSelfComparison()
                ? direction.getTargetObjectCount() - 1
                : direction.getTargetObjectCount();
        if (availableTargets <= 0) return "NO_TARGET_OBJECTS";
        if (rank > availableTargets) return "INSUFFICIENT_NEIGHBOURS";
        return finiteValueCount == 0 ? "UNDEFINED_MEASUREMENTS" : "OK";
    }

    static Map<String, ResultsTable> histograms(
            List<DirectionResult> directions,
            int binCount) {
        Map<String, ResultsTable> tables = new LinkedHashMap<String, ResultsTable>();
        for (DirectionResult direction : directions) {
            for (DistanceMode mode : DistanceMode.values()) {
                for (int rank = 1; ; rank++) {
                    List<Double> values = values(direction, mode, rank);
                    if (values.isEmpty()) break;
                    double[] sorted = sorted(values);
                    ResultsTable table = histogram(
                            sorted, binCount, measurementUnit(direction, mode),
                            direction, mode, rank);
                    putUnique(
                            tables,
                            directionKey(direction) + "__"
                                    + safe(mode.getColumnName()) + "__NN" + rank,
                            table);
                }
            }
        }
        return tables;
    }

    static Map<String, ResultsTable> ecdfs(List<DirectionResult> directions) {
        Map<String, ResultsTable> tables = new LinkedHashMap<String, ResultsTable>();
        for (DirectionResult direction : directions) {
            for (DistanceMode mode : DistanceMode.values()) {
                for (int rank = 1; ; rank++) {
                    List<Double> values = values(direction, mode, rank);
                    if (values.isEmpty()) break;
                    double[] sorted = sorted(values);
                    ResultsTable table = new ResultsTable();
                    for (int i = 0; i < sorted.length; i++) {
                        table.incrementCounter();
                        table.addValue("Value", sorted[i]);
                        table.addValue("ECDF", (i + 1.0) / sorted.length);
                        table.addValue("Unit", measurementUnit(direction, mode));
                        addDistributionIdentity(
                                table, direction, mode, rank);
                    }
                    putUnique(
                            tables,
                            directionKey(direction) + "__"
                                    + safe(mode.getColumnName()) + "__NN" + rank,
                            table);
                }
            }
        }
        return tables;
    }

    static Map<String, ResultsTable> curves(List<PatternResult> patterns) {
        Map<String, ResultsTable> tables = new LinkedHashMap<String, ResultsTable>();
        for (PatternResult pattern : patterns) {
            MonteCarloResult result = pattern.getStatistics();
            double[] radii = result.getRadii();
            double[] observed = result.getObserved();
            double[] expected = result.getExpected();
            double[] lower = result.getLower();
            double[] upper = result.getUpper();
            int[] envelopeCounts = result.getEnvelopeSampleCounts();
            ResultsTable table = new ResultsTable();
            for (int i = 0; i < radii.length; i++) {
                table.incrementCounter();
                table.addValue("Radius", radii[i]);
                table.addValue("Observed", observed[i]);
                table.addValue("CSR_Expectation", expected[i]);
                table.addValue("Envelope_Lower", lower[i]);
                table.addValue("Envelope_Upper", upper[i]);
                table.addValue("Envelope_N", envelopeCounts[i]);
                table.addValue("Envelope_Level", result.getEnvelopeLevel());
                table.addValue("Envelope_Rank", result.getEnvelopeRank());
                table.addValue(
                        "Envelope_Status",
                        envelopeStatus(
                                envelopeCounts[i],
                                result.getSimulations()));
                table.addValue("Radius_Unit", pattern.getUnit());
                table.addValue("Value_Unit", pattern.getValueUnit());
                table.addValue("Simulations", result.getSimulations());
                table.addValue("Seed", Long.toString(result.getSeed()));
                table.addValue("Global_P", result.getGlobalPValue());
                table.addValue("Status", result.getStatus().name());
                table.addValue("Global_Rank_N", result.getRankSampleCount());
                table.addValue("Source_Channel", pattern.getSourceChannel());
                table.addValue("Target_Channel",
                        pattern.isBivariate()
                                ? pattern.getTargetChannel()
                                : "");
                table.addValue("Function", result.getFunction().name());
            }
            putUnique(tables, patternKey(pattern), table);
        }
        return tables;
    }

    static ResultsTable patternSummary(List<PatternResult> patterns,
                                       OPAParameters parameters,
                                       List<ChannelGeometry> channels) {
        ResultsTable table = new ResultsTable();
        for (PatternResult pattern : patterns) {
            MonteCarloResult result = pattern.getStatistics();
            table.incrementCounter();
            table.addValue("Source_Channel", pattern.getSourceChannel());
            table.addValue("Target_Channel",
                    pattern.isBivariate() ? pattern.getTargetChannel() : "");
            table.addValue("Function", result.getFunction().name());
            table.addValue("Status", result.getStatus().name());
            table.addValue(
                    "Envelope_Status",
                    result.hasCompletePointwiseEnvelope()
                            ? "OK"
                            : "INCOMPLETE_POINTWISE_ENVELOPE");
            table.addValue("Maximum_Absolute_Deviation",
                    result.getMaximumDeviation());
            table.addValue("Radius_At_Maximum_Deviation",
                    result.getMaximumDeviationRadius());
            table.addValue("Global_P", result.getGlobalPValue());
            table.addValue("Simulations", result.getSimulations());
            table.addValue("Minimum_Achievable_P",
                    result.getMinimumAchievablePValue());
            table.addValue("Global_Rank_N", result.getRankSampleCount());
            table.addValue("Seed", Long.toString(result.getSeed()));
            table.addValue("Radius_Unit", pattern.getUnit());
            table.addValue("Value_Unit", pattern.getValueUnit());
            int[] envelopeCounts = result.getEnvelopeSampleCounts();
            int minimumEnvelopeCount = envelopeCounts.length == 0
                    ? 0
                    : envelopeCounts[0];
            int completeEnvelopeRadii = 0;
            for (int count : envelopeCounts) {
                if (count < minimumEnvelopeCount) {
                    minimumEnvelopeCount = count;
                }
                if (count == result.getSimulations()) {
                    completeEnvelopeRadii++;
                }
            }
            table.addValue("Minimum_Envelope_N", minimumEnvelopeCount);
            table.addValue(
                    "Complete_Envelope_Radii", completeEnvelopeRadii);
            table.addValue(
                    "Requested_Radii_Count", envelopeCounts.length);
        }
        if (parameters.isRunPattern() && channels.size() < 2) {
            ChannelGeometry channel = channels.get(0);
            for (PatternFunction function
                    : parameters.getPatternFunctions()) {
                if (!function.isBivariate()) continue;
                table.incrementCounter();
                table.addValue("Source_Channel", channel.getName());
                table.addValue("Target_Channel", "");
                table.addValue("Function", function.name());
                table.addValue("Status",
                        "NOT_APPLICABLE_REQUIRES_TWO_CHANNELS");
                table.addValue("Envelope_Status", "NOT_APPLICABLE");
                table.addValue(
                        "Maximum_Absolute_Deviation", Double.NaN);
                table.addValue(
                        "Radius_At_Maximum_Deviation", Double.NaN);
                table.addValue("Global_P", Double.NaN);
                table.addValue(
                        "Simulations", parameters.getSimulations());
                table.addValue(
                        "Minimum_Achievable_P",
                        1.0 / (parameters.getSimulations() + 1.0));
                table.addValue("Global_Rank_N", 0);
                table.addValue(
                        "Seed", Long.toString(parameters.getSeed()));
                table.addValue(
                        "Radius_Unit",
                        channel.getCalibration().getUnit());
                table.addValue(
                        "Value_Unit",
                        patternValueUnit(
                                function,
                                channel.getCalibration().getUnit()));
                table.addValue("Minimum_Envelope_N", 0);
                table.addValue("Complete_Envelope_Radii", 0);
                table.addValue("Requested_Radii_Count", 0);
            }
        }
        return table;
    }

    private static String envelopeStatus(int count, int simulations) {
        if (count == simulations) return "OK";
        return count == 0
                ? "NO_VALID_SIMULATIONS"
                : "INCOMPLETE_POINTWISE_ENVELOPE";
    }

    private static String patternValueUnit(PatternFunction function,
                                           String radiusUnit) {
        switch (function) {
            case K:
            case CROSS_K:
                return radiusUnit + "^2";
            case L:
            case L_MINUS_R:
            case CROSS_L:
                return radiusUnit;
            case G:
            case CROSS_G:
            case PAIR_CORRELATION:
            case CROSS_PAIR_CORRELATION:
                return "dimensionless";
            default:
                throw new IllegalArgumentException(
                        "Unsupported pattern function: " + function);
        }
    }

    private static ResultsTable histogram(double[] values,
                                          int binCount,
                                          String unit,
                                          DirectionResult direction,
                                          DistanceMode mode,
                                          int rank) {
        ResultsTable table = new ResultsTable();
        double minimum = values[0];
        double maximum = values[values.length - 1];
        if (minimum == maximum) {
            table.incrementCounter();
            table.addValue("Bin_Lower", minimum);
            table.addValue("Bin_Upper", maximum);
            table.addValue("Bin_Centre", minimum);
            table.addValue("Count", values.length);
            table.addValue("Fraction", 1.0);
            table.addValue("Unit", unit);
            addDistributionIdentity(table, direction, mode, rank);
            return table;
        }
        double width = (maximum - minimum) / binCount;
        int[] counts = new int[binCount];
        for (double value : values) {
            int bin = (int) ((value - minimum) / width);
            if (bin >= binCount) bin = binCount - 1;
            counts[bin]++;
        }
        for (int bin = 0; bin < binCount; bin++) {
            double lower = minimum + bin * width;
            double upper = bin == binCount - 1 ? maximum : lower + width;
            table.incrementCounter();
            table.addValue("Bin_Lower", lower);
            table.addValue("Bin_Upper", upper);
            table.addValue("Bin_Centre", (lower + upper) * 0.5);
            table.addValue("Count", counts[bin]);
            table.addValue("Fraction", counts[bin] / (double) values.length);
            table.addValue("Unit", unit);
            addDistributionIdentity(table, direction, mode, rank);
        }
        return table;
    }

    private static void addDirectionIdentity(ResultsTable table,
                                             DirectionResult direction) {
        table.addValue("Source_Channel", direction.getSourceChannel());
        table.addValue("Target_Channel", direction.getTargetChannel());
    }

    private static void addDistributionIdentity(ResultsTable table,
                                                DirectionResult direction,
                                                DistanceMode mode,
                                                int rank) {
        addDirectionIdentity(table, direction);
        table.addValue("Mode", mode.getColumnName());
        table.addValue("Rank", rank);
    }

    private static List<Double> values(DirectionResult direction,
                                       DistanceMode mode,
                                       int rank) {
        List<Double> values = new ArrayList<Double>();
        for (ObjectMeasurement object : direction.getMeasurements()) {
            List<NeighborMeasurement> neighbors = object.getNeighbors(mode);
            if (neighbors.size() < rank) continue;
            double value = neighbors.get(rank - 1).getValue();
            if (Double.isFinite(value)) values.add(value);
        }
        return values;
    }

    private static double fractionWithinContact(DirectionResult direction,
                                                DistanceMode mode,
                                                int rank) {
        int total = 0;
        int within = 0;
        for (ObjectMeasurement object : direction.getMeasurements()) {
            List<NeighborMeasurement> neighbors = object.getNeighbors(mode);
            if (neighbors.size() < rank) continue;
            total++;
            if (neighbors.get(rank - 1).isWithinContactDistance()) within++;
        }
        return total == 0 ? Double.NaN : within / (double) total;
    }

    private static double[] sorted(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        Arrays.sort(result);
        return result;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    private static double median(double[] sorted) {
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0
                ? (sorted[middle - 1] + sorted[middle]) * 0.5
                : sorted[middle];
    }

    private static double standardDeviation(double[] values) {
        if (values.length < 2) return Double.NaN;
        double mean = mean(values);
        double sumSquares = 0.0;
        for (double value : values) {
            double difference = value - mean;
            sumSquares += difference * difference;
        }
        return Math.sqrt(sumSquares / (values.length - 1));
    }

    private static String directionKey(DirectionResult direction) {
        String readable = safe(direction.getSourceChannel()) + "_to_"
                + safe(direction.getTargetChannel());
        return readable + "__" + identityHash(
                "DIRECTION",
                direction.getSourceChannel(),
                direction.getTargetChannel());
    }

    private static String patternKey(PatternResult result) {
        String channels = safe(result.getSourceChannel());
        if (result.isBivariate()) {
            channels += "_to_" + safe(result.getTargetChannel());
        }
        return channels + "__" + result.getStatistics().getFunction().name()
                + "__" + identityHash(
                        "PATTERN",
                        result.getSourceChannel(),
                        result.isBivariate() ? result.getTargetChannel() : "",
                        result.getStatistics().getFunction().name());
    }

    private static String measurementUnit(DirectionResult direction,
                                          DistanceMode mode) {
        if (mode.isDistance()) return direction.getUnit();
        return direction.getSurfaceMeasureUnit();
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "Channel";
        return value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String identityHash(String... fields) {
        StringBuilder identity = new StringBuilder();
        for (String field : fields) {
            String value = field == null ? "" : field;
            identity.append(value.length()).append(':').append(value);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    identity.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                text.append(String.format("%02x", item & 0xff));
            }
            return text.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "This Java runtime does not provide SHA-256.", exception);
        }
    }

    private static void putUnique(Map<String, ResultsTable> tables,
                                  String preferredKey,
                                  ResultsTable table) {
        String key = preferredKey;
        int suffix = 2;
        while (tables.containsKey(key)) {
            key = preferredKey + "__" + suffix++;
        }
        tables.put(key, table);
    }
}
