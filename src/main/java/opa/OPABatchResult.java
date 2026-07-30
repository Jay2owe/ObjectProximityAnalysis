/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.measure.ResultsTable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folder-batch status plus aggregate scalar, distribution and curve tables.
 */
public final class OPABatchResult {

    private final int totalGroups;
    private final int validGroups;
    private final int processedGroups;
    private final int skippedGroups;
    private final int errorGroups;
    private final File outputDirectory;
    private final ResultsTable distanceSummary;
    private final ResultsTable patternSummary;
    private final Map<String, ResultsTable> meanCurveTables;
    private final Map<String, ResultsTable> meanEcdfTables;
    private final List<String> errors;

    OPABatchResult(int totalGroups,
                   int validGroups,
                   int processedGroups,
                   int skippedGroups,
                   int errorGroups,
                   File outputDirectory,
                   ResultsTable distanceSummary,
                   ResultsTable patternSummary,
                   Map<String, ResultsTable> meanCurveTables,
                   Map<String, ResultsTable> meanEcdfTables,
                   List<String> errors) {
        this.totalGroups = totalGroups;
        this.validGroups = validGroups;
        this.processedGroups = processedGroups;
        this.skippedGroups = skippedGroups;
        this.errorGroups = errorGroups;
        this.outputDirectory = outputDirectory;
        this.distanceSummary = distanceSummary;
        this.patternSummary = patternSummary;
        this.meanCurveTables = Collections.unmodifiableMap(
                new LinkedHashMap<String, ResultsTable>(meanCurveTables));
        this.meanEcdfTables = Collections.unmodifiableMap(
                new LinkedHashMap<String, ResultsTable>(meanEcdfTables));
        this.errors = Collections.unmodifiableList(
                new ArrayList<String>(errors));
    }

    public int getTotalGroups() {
        return totalGroups;
    }

    public int getValidGroups() {
        return validGroups;
    }

    public int getProcessedGroups() {
        return processedGroups;
    }

    public int getSkippedGroups() {
        return skippedGroups;
    }

    public int getErrorGroups() {
        return errorGroups;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public ResultsTable getDistanceSummary() {
        return distanceSummary;
    }

    public ResultsTable getPatternSummary() {
        return patternSummary;
    }

    public Map<String, ResultsTable> getMeanCurveTables() {
        return meanCurveTables;
    }

    public Map<String, ResultsTable> getMeanEcdfTables() {
        return meanEcdfTables;
    }

    public List<String> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
