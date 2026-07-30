/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.IJ;
import ij.Macro;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import opa.spatial.EdgeCorrection;
import opa.spatial.PatternFunction;

import java.io.File;
import java.util.EnumSet;
import java.util.Map;

/**
 * First-class folder batch entry with a grouping preview before execution.
 */
public final class OPA_Batch implements PlugIn {

    @Override
    public void run(String argument) {
        GenericDialog dialog = new GenericDialog(
                "Object Proximity Analysis Batch");
        dialog.addDirectoryField("Input_folder", IJ.getDirectory("home"));
        dialog.addStringField(
                "Filename_regex",
                "(.+)_([^_]+)\\.(tif|tiff)$",
                42);
        dialog.addNumericField("Channel_capture_group", 2, 0);
        dialog.addCheckbox("Recursive", true);
        dialog.addMessage("Distances");
        dialog.addCheckbox("Run_distances", true);
        dialog.addCheckbox("Include_self_distances", true);
        dialog.addNumericField("K_nearest_neighbours", 1, 0);
        dialog.addNumericField("Contact_distance", 0.0, 3);
        for (DistanceMode mode : DistanceMode.values()) {
            dialog.addCheckbox(
                    mode.getColumnName().replace('-', '_'), true);
        }
        dialog.addMessage("2D point-pattern analysis");
        dialog.addCheckbox("Run_pattern_analysis", true);
        for (PatternFunction function : PatternFunction.values()) {
            dialog.addCheckbox("Function_" + function.name(), true);
        }
        dialog.addNumericField("Maximum_radius_0_is_auto", 0.0, 3);
        dialog.addNumericField("Radius_bins", 50, 0);
        dialog.addNumericField("Monte_Carlo_simulations", 99, 0);
        dialog.addStringField(
                "Random_seed",
                Long.toString(OPAParameters.DEFAULT_SEED),
                18);
        dialog.addChoice(
                "Edge_correction",
                new String[]{
                        EdgeCorrection.TRANSLATION.name(),
                        EdgeCorrection.BORDER.name(),
                        EdgeCorrection.NONE.name()
                },
                EdgeCorrection.TRANSLATION.name());
        dialog.addCheckbox("Project_3D_centroids_to_XY", false);
        dialog.addMessage("Output");
        dialog.addNumericField("Histogram_bins", 20, 0);
        dialog.addCheckbox("Auto_save", true);
        dialog.addDirectoryField("Output_directory", IJ.getDirectory("home"));
        dialog.addCheckbox("Hide_display", false);
        dialog.showDialog();
        if (dialog.wasCanceled()) return;

        try {
            File input = new File(dialog.getNextString().trim());
            String regex = dialog.getNextString();
            int channelGroup = (int) dialog.getNextNumber();
            boolean recursive = dialog.getNextBoolean();
            boolean distances = dialog.getNextBoolean();
            boolean self = dialog.getNextBoolean();
            int neighbors = (int) dialog.getNextNumber();
            double contact = dialog.getNextNumber();
            EnumSet<DistanceMode> distanceModes =
                    EnumSet.noneOf(DistanceMode.class);
            for (DistanceMode mode : DistanceMode.values()) {
                if (dialog.getNextBoolean()) distanceModes.add(mode);
            }
            boolean pattern = dialog.getNextBoolean();
            EnumSet<PatternFunction> patternFunctions =
                    EnumSet.noneOf(PatternFunction.class);
            for (PatternFunction function : PatternFunction.values()) {
                if (dialog.getNextBoolean()) {
                    patternFunctions.add(function);
                }
            }
            double maximumRadius = dialog.getNextNumber();
            int radiusBins = (int) dialog.getNextNumber();
            int simulations = (int) dialog.getNextNumber();
            long seed;
            try {
                seed = Long.parseLong(dialog.getNextString().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Random seed must be a whole number.");
            }
            EdgeCorrection correction =
                    EdgeCorrection.valueOf(dialog.getNextChoice());
            boolean project3D = dialog.getNextBoolean();
            int histogramBins = (int) dialog.getNextNumber();
            boolean autoSave = dialog.getNextBoolean();
            File output = new File(dialog.getNextString().trim());
            boolean hideDisplay = dialog.getNextBoolean();

            OPAParameters options = OPAParameters.builder()
                    .runDistances(distances)
                    .runPattern(pattern)
                    .includeSelfDistances(self)
                    .distanceModes(distanceModes)
                    .neighborCount(neighbors)
                    .contactDistance(contact)
                    .patternFunctions(patternFunctions)
                    .maximumRadius(maximumRadius)
                    .radiusBins(radiusBins)
                    .simulations(simulations)
                    .seed(seed)
                    .edgeCorrection(correction)
                    .project3DToXY(project3D)
                    .histogramBins(histogramBins)
                    .build();
            OPABatchParameters parameters = OPABatchParameters.builder(
                            input, regex, channelGroup)
                    .recursive(recursive)
                    .analysisTemplate(options)
                    .autoSave(autoSave)
                    .outputDirectory(output)
                    .build();

            String preview = OPABatchRunner.preview(parameters);
            if (Macro.getOptions() == null) {
                GenericDialog confirmation = new GenericDialog(
                        "Object Proximity Analysis Batch Preview");
                confirmation.addMessage(
                        "Review the groups below. Runnable groups satisfy both "
                                + "the filename grouping and selected analysis options.");
                confirmation.addTextAreas(preview, null, 24, 80);
                confirmation.enableYesNoCancel("Run batch", "Back");
                confirmation.showDialog();
                if (confirmation.wasCanceled() || !confirmation.wasOKed()) return;
            } else {
                IJ.log(preview);
            }

            OPABatchResult result = OPABatchRunner.run(parameters);
            IJ.log("OPA batch "
                    + (result.isCancelled() ? "cancelled" : "complete")
                    + ": " + result.getProcessedGroups()
                    + " processed, " + result.getSkippedGroups()
                    + " skipped, " + result.getErrorGroups() + " errors.");
            if (!hideDisplay) show(result);
        } catch (Exception exception) {
            IJ.handleException(exception);
        }
    }

    private static void show(OPABatchResult result) {
        result.getGroupManifest().show("OPA Batch Group Manifest");
        if (result.getDistanceSummary().size() > 0) {
            result.getDistanceSummary().show("OPA Batch Distance Summary");
        }
        if (result.getPatternSummary().size() > 0) {
            result.getPatternSummary().show("OPA Batch Pattern Summary");
        }
        for (Map.Entry<String, ResultsTable> entry
                : result.getMeanCurveTables().entrySet()) {
            entry.getValue().show("OPA Batch Mean Curve - " + entry.getKey());
        }
        for (Map.Entry<String, ResultsTable> entry
                : result.getMeanEcdfTables().entrySet()) {
            entry.getValue().show("OPA Batch Mean ECDF - " + entry.getKey());
        }
    }
}
