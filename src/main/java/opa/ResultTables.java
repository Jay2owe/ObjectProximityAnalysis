/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.measure.ResultsTable;
import opa.geometry.DirectionResult;
import opa.geometry.NeighborMeasurement;
import opa.geometry.ObjectMeasurement;
import opa.spatial.MonteCarloResult;

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
            tables.put(directionKey(direction), table);
        }
        return tables;
    }

    static ResultsTable distanceSummary(List<DirectionResult> directions) {
        ResultsTable table = new ResultsTable();
        for (DirectionResult direction : directions) {
            for (DistanceMode mode : DistanceMode.values()) {
                List<Double> values = values(direction, mode, 1);
                if (values.isEmpty()) continue;
                double[] sorted = sorted(values);
                table.incrementCounter();
                table.addValue("Source_Channel", direction.getSourceChannel());
                table.addValue("Target_Channel", direction.getTargetChannel());
                table.addValue("Self_Comparison",
                        direction.isSelfComparison() ? 1 : 0);
                table.addValue("Mode", mode.getColumnName());
                table.addValue("Rank", 1);
                table.addValue("Unit", measurementUnit(direction, mode));
                table.addValue("N", sorted.length);
                table.addValue("Mean", mean(sorted));
                table.addValue("Median", median(sorted));
                table.addValue("SD", standardDeviation(sorted));
                table.addValue("Min", sorted[0]);
                table.addValue("Max", sorted[sorted.length - 1]);
                table.addValue("Fraction_Within_Contact",
                        fractionWithinContact(direction, mode, 1));
            }
        }
        return table;
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
                            sorted, binCount, measurementUnit(direction, mode));
                    tables.put(directionKey(direction) + "__"
                            + safe(mode.getColumnName()) + "__NN" + rank, table);
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
                    }
                    tables.put(directionKey(direction) + "__"
                            + safe(mode.getColumnName()) + "__NN" + rank, table);
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
            ResultsTable table = new ResultsTable();
            for (int i = 0; i < radii.length; i++) {
                table.incrementCounter();
                table.addValue("Radius", radii[i]);
                table.addValue("Observed", observed[i]);
                table.addValue("CSR_Expectation", expected[i]);
                table.addValue("Envelope_Lower", lower[i]);
                table.addValue("Envelope_Upper", upper[i]);
                table.addValue("Radius_Unit", pattern.getUnit());
                table.addValue("Simulations", result.getSimulations());
                table.addValue("Seed", Long.toString(result.getSeed()));
                table.addValue("Global_P", result.getGlobalPValue());
            }
            tables.put(patternKey(pattern), table);
        }
        return tables;
    }

    static ResultsTable patternSummary(List<PatternResult> patterns) {
        ResultsTable table = new ResultsTable();
        for (PatternResult pattern : patterns) {
            MonteCarloResult result = pattern.getStatistics();
            table.incrementCounter();
            table.addValue("Source_Channel", pattern.getSourceChannel());
            table.addValue("Target_Channel",
                    pattern.isBivariate() ? pattern.getTargetChannel() : "");
            table.addValue("Function", result.getFunction().name());
            table.addValue("Maximum_Absolute_Deviation",
                    result.getMaximumDeviation());
            table.addValue("Radius_At_Maximum_Deviation",
                    result.getMaximumDeviationRadius());
            table.addValue("Global_P", result.getGlobalPValue());
            table.addValue("Simulations", result.getSimulations());
            table.addValue("Minimum_Achievable_P",
                    1.0 / (result.getSimulations() + 1.0));
            table.addValue("Seed", Long.toString(result.getSeed()));
            table.addValue("Radius_Unit", pattern.getUnit());
        }
        return table;
    }

    private static ResultsTable histogram(double[] values,
                                          int binCount,
                                          String unit) {
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
        }
        return table;
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
        if (values.length < 2) return 0.0;
        double mean = mean(values);
        double sumSquares = 0.0;
        for (double value : values) {
            double difference = value - mean;
            sumSquares += difference * difference;
        }
        return Math.sqrt(sumSquares / (values.length - 1));
    }

    private static String directionKey(DirectionResult direction) {
        return safe(direction.getSourceChannel()) + "_to_"
                + safe(direction.getTargetChannel());
    }

    private static String patternKey(PatternResult result) {
        String channels = safe(result.getSourceChannel());
        if (result.isBivariate()) {
            channels += "_to_" + safe(result.getTargetChannel());
        }
        return channels + "__" + result.getStatistics().getFunction().name();
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
}
