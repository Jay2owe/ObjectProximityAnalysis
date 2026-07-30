/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.measure.ResultsTable;
import opa.geometry.ChannelGeometry;
import opa.geometry.DirectionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete headless result bundle. Creating this object opens no windows and
 * writes no files.
 */
public final class OPAResult {

    private final OPAParameters parameters;
    private final List<ChannelGeometry> channels;
    private final List<DirectionResult> directionResults;
    private final List<PatternResult> patternResults;
    private final Map<String, ResultsTable> centroidTables;
    private final ResultsTable provenanceTable;
    private final Map<String, ResultsTable> perObjectTables;
    private final ResultsTable distanceSummaryTable;
    private final Map<String, ResultsTable> histogramTables;
    private final Map<String, ResultsTable> ecdfTables;
    private final Map<String, ResultsTable> curveTables;
    private final ResultsTable patternSummaryTable;

    OPAResult(OPAParameters parameters,
              List<ChannelGeometry> channels,
              List<DirectionResult> directionResults,
              List<PatternResult> patternResults) {
        this.parameters = parameters;
        this.channels = immutableList(channels);
        this.directionResults = immutableList(directionResults);
        this.patternResults = immutableList(patternResults);
        this.centroidTables = immutableMap(ResultTables.centroids(channels));
        this.provenanceTable = ResultTables.provenance(
                parameters, channels, patternResults);
        this.perObjectTables = immutableMap(
                ResultTables.perObject(directionResults));
        this.distanceSummaryTable = ResultTables.distanceSummary(
                directionResults,
                parameters.getDistanceModes(),
                parameters.getNeighborCount());
        this.histogramTables = immutableMap(ResultTables.histograms(
                directionResults, parameters.getHistogramBins()));
        this.ecdfTables = immutableMap(ResultTables.ecdfs(directionResults));
        this.curveTables = immutableMap(ResultTables.curves(patternResults));
        this.patternSummaryTable = ResultTables.patternSummary(patternResults);
    }

    public OPAParameters getParameters() {
        return parameters;
    }

    public List<ChannelGeometry> getChannels() {
        return channels;
    }

    public List<DirectionResult> getDirectionResults() {
        return directionResults;
    }

    public List<PatternResult> getPatternResults() {
        return patternResults;
    }

    public Map<String, ResultsTable> getCentroidTables() {
        return centroidTables;
    }

    public ResultsTable getProvenanceTable() {
        return provenanceTable;
    }

    public Map<String, ResultsTable> getPerObjectTables() {
        return perObjectTables;
    }

    public ResultsTable getDistanceSummaryTable() {
        return distanceSummaryTable;
    }

    public Map<String, ResultsTable> getHistogramTables() {
        return histogramTables;
    }

    public Map<String, ResultsTable> getEcdfTables() {
        return ecdfTables;
    }

    public Map<String, ResultsTable> getCurveTables() {
        return curveTables;
    }

    public ResultsTable getPatternSummaryTable() {
        return patternSummaryTable;
    }

    public boolean hasPhysicalCalibration() {
        return !channels.isEmpty()
                && channels.get(0).getCalibration().hasPhysicalUnits();
    }

    private static <T> List<T> immutableList(List<T> input) {
        return Collections.unmodifiableList(new ArrayList<T>(input));
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> input) {
        return Collections.unmodifiableMap(new LinkedHashMap<String, T>(input));
    }
}
