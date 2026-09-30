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
import ij.plugin.frame.Recorder;
import sc.fiji.opa.core.spatial.EdgeCorrection;
import sc.fiji.opa.core.spatial.PatternFunction;
import sc.fiji.opa.core.DistanceMode;

import java.io.File;
import java.util.EnumSet;
import java.util.Map;

/**
 * First-class folder batch entry with a grouping preview before execution.
 */
public final class OPA_Batch implements PlugIn {

    private static final String TITLE = "Object Proximity Analysis Batch";

    /**
     * Case-insensitive, so {@code .TIF} files, as many microscopes write
     * them, are grouped too. Recorded macros keep the pattern they recorded.
     */
    static final String DEFAULT_REGEX = "(?i)(.+)_([^_]+)\\.(tif|tiff)$";

    @Override
    public void run(String argument) {
        Settings settings = Settings.defaults();
        try {
            while (true) {
                settings = showSettings(settings);
                if (settings == null) return;
                // Escape is a sticky global flag in ImageJ. Clearing it here
                // stops a keypress left over from an earlier command
                // cancelling this batch before any group has run.
                IJ.resetEscape();
                OPABatchParameters parameters = settings.toParameters();
                String preview = OPABatchRunner.preview(parameters);
                if (Macro.getOptions() == null) {
                    GenericDialog confirmation = new GenericDialog(
                            "Object Proximity Analysis Batch Preview");
                    confirmation.addMessage(
                            "Review the groups below. Runnable groups satisfy both "
                                    + "the filename grouping and selected analysis options.\n"
                                    + "Press Esc during the run to stop it.");
                    confirmation.addTextAreas(preview, null, 20, 80);
                    confirmation.enableYesNoCancel("Run batch", "Back");
                    confirmation.showDialog();
                    if (confirmation.wasCanceled()) {
                        forgetRecordedCommand();
                        return;
                    }
                    if (!confirmation.wasOKed()) {
                        // Back: reopen the settings with the values just
                        // entered, and forget the options recorded for the
                        // abandoned attempt.
                        if (Recorder.record) Recorder.resetCommandOptions();
                        continue;
                    }
                } else {
                    IJ.log(preview);
                }

                OPABatchResult result = OPABatchRunner.run(parameters);
                if (result.isCancelled()) {
                    // The runner has already logged the cancel with its counts.
                    IJ.showStatus("OPA batch cancelled");
                } else {
                    IJ.log("OPA batch complete: " + result.getProcessedGroups()
                            + " processed, " + result.getSkippedGroups()
                            + " skipped, " + result.getErrorGroups() + " errors.");
                }
                if (!settings.hideDisplay) show(result);
                return;
            }
        } catch (Exception exception) {
            forgetRecordedCommand();
            UserErrors.report(TITLE, exception);
        } catch (OutOfMemoryError error) {
            forgetRecordedCommand();
            UserErrors.report(TITLE, error);
        }
    }

    /**
     * ImageJ records a command when it returns; a batch that failed or was
     * cancelled at the preview must not leave a line in the Macro Recorder.
     */
    private static void forgetRecordedCommand() {
        Object_Proximity_Analysis.forgetRecordedCommand();
    }

    /** Shows the settings dialog filled from {@code previous}; null when cancelled. */
    private static Settings showSettings(Settings previous) {
        GenericDialog dialog = new FittingDialog(TITLE);
        DialogFields.addDirectory(dialog, "Input_folder", previous.input);
        dialog.addStringField("Filename_regex", previous.regex, 42);
        // Pairs and checkbox grids keep the dialog on a laptop screen; the add
        // order of each field type, and so every macro key, is unchanged.
        dialog.addNumericField("Channel_capture_group", previous.channelGroup,
                DialogNumbers.digits(previous.channelGroup, 0));
        dialog.addToSameRow();
        dialog.addCheckbox("Recursive", previous.recursive);
        dialog.addMessage("Distances");
        dialog.addCheckboxGroup(1, 2,
                new String[]{"Run_distances", "Include_self_distances"},
                new boolean[]{previous.distances, previous.self});
        dialog.addNumericField("K_nearest_neighbours", previous.neighbors,
                DialogNumbers.digits(previous.neighbors, 0));
        dialog.addToSameRow();
        dialog.addNumericField("Contact_distance", previous.contact,
                DialogNumbers.digits(previous.contact, 3));
        DistanceMode[] modes = DistanceMode.values();
        String[] modeLabels = new String[modes.length];
        boolean[] modeStates = new boolean[modes.length];
        for (int i = 0; i < modes.length; i++) {
            modeLabels[i] = modes[i].getColumnName().replace('-', '_');
            modeStates[i] = previous.distanceModes.contains(modes[i]);
        }
        dialog.addCheckboxGroup(Object_Proximity_Analysis.rows(modes.length, 3), 3,
                modeLabels, modeStates);
        dialog.addMessage("2D point-pattern analysis");
        dialog.addCheckbox("Run_pattern_analysis", previous.pattern);
        PatternFunction[] functions = PatternFunction.values();
        String[] functionLabels = new String[functions.length];
        boolean[] functionStates = new boolean[functions.length];
        for (int i = 0; i < functions.length; i++) {
            functionLabels[i] = "Function_" + functions[i].name();
            functionStates[i] = previous.patternFunctions.contains(functions[i]);
        }
        dialog.addCheckboxGroup(
                Object_Proximity_Analysis.rows(functions.length, 3), 3,
                functionLabels, functionStates);
        dialog.addNumericField("Maximum_radius_0_is_auto", previous.maximumRadius,
                DialogNumbers.digits(previous.maximumRadius, 3));
        dialog.addToSameRow();
        dialog.addNumericField("Radius_bins", previous.radiusBins,
                DialogNumbers.digits(previous.radiusBins, 0));
        dialog.addNumericField("Monte_Carlo_simulations", previous.simulations,
                DialogNumbers.digits(previous.simulations, 0));
        dialog.addToSameRow();
        dialog.addStringField("Random_seed", previous.seed, 12);
        dialog.addChoice(
                "Edge_correction",
                new String[]{
                        EdgeCorrection.TRANSLATION.name(),
                        EdgeCorrection.BORDER.name(),
                        EdgeCorrection.NONE.name()
                },
                previous.correction.name());
        dialog.addToSameRow();
        dialog.addCheckbox("Project_3D_centroids_to_XY", previous.project3D);
        dialog.addMessage("Output");
        dialog.addNumericField("Histogram_bins", previous.histogramBins,
                DialogNumbers.digits(previous.histogramBins, 0));
        DialogFields.addDirectory(dialog, "Output_directory", previous.output);
        dialog.addMessage("Leave the output directory blank to save into the input folder.");
        dialog.addCheckboxGroup(1, 2,
                new String[]{"Auto_save", "Hide_display"},
                new boolean[]{previous.autoSave, previous.hideDisplay});
        dialog.showDialog();
        if (dialog.wasCanceled()) return null;

        Settings next = new Settings();
        next.input = dialog.getNextString().trim();
        next.regex = dialog.getNextString();
        next.channelGroup = dialog.getNextNumber();
        next.recursive = dialog.getNextBoolean();
        next.distances = dialog.getNextBoolean();
        next.self = dialog.getNextBoolean();
        next.neighbors = dialog.getNextNumber();
        next.contact = dialog.getNextNumber();
        next.distanceModes = EnumSet.noneOf(DistanceMode.class);
        for (DistanceMode mode : DistanceMode.values()) {
            if (dialog.getNextBoolean()) next.distanceModes.add(mode);
        }
        next.pattern = dialog.getNextBoolean();
        next.patternFunctions = EnumSet.noneOf(PatternFunction.class);
        for (PatternFunction function : PatternFunction.values()) {
            if (dialog.getNextBoolean()) next.patternFunctions.add(function);
        }
        next.maximumRadius = dialog.getNextNumber();
        next.radiusBins = dialog.getNextNumber();
        next.simulations = dialog.getNextNumber();
        next.seed = dialog.getNextString().trim();
        next.correction = EdgeCorrection.valueOf(
                DialogFields.nextChoice(dialog, "Edge_correction"));
        next.project3D = dialog.getNextBoolean();
        next.histogramBins = dialog.getNextNumber();
        next.autoSave = dialog.getNextBoolean();
        next.output = dialog.getNextString().trim();
        next.hideDisplay = dialog.getNextBoolean();
        return next;
    }

    /**
     * The output folder named in the dialog, or {@code null} for a blank field,
     * which the batch runner resolves to the input folder. A blank field must
     * not become {@code new File("")}: that resolves against the working
     * directory and would write output beside the Fiji installation.
     */
    static File outputDirectory(String field) {
        String trimmed = field == null ? "" : field.trim();
        return trimmed.isEmpty() ? null : new File(trimmed);
    }

    private static void show(OPABatchResult result) {
        result.getGroupManifest().show("OPA Batch Group Manifest");
        // A cancelled batch shows the manifest, which marks every group that
        // did not run, and not a window per partial aggregate.
        if (result.isCancelled()) return;
        if (result.getDistanceSummary().size() > 0) {
            result.getDistanceSummary().show("OPA Batch Distance Summary");
        }
        if (result.getPatternSummary().size() > 0) {
            result.getPatternSummary().show("OPA Batch Pattern Summary");
        }
        for (Map.Entry<String, ResultsTable> entry : WindowTitles.titled(
                "OPA Batch Mean Curve - ", result.getMeanCurveTables()).entrySet()) {
            entry.getValue().show(entry.getKey());
        }
        for (Map.Entry<String, ResultsTable> entry : WindowTitles.titled(
                "OPA Batch Mean ECDF - ", result.getMeanEcdfTables()).entrySet()) {
            entry.getValue().show(entry.getKey());
        }
    }

    /** Everything the settings dialog holds, so Back can reopen it unchanged. */
    static final class Settings {
        String input;
        String regex;
        double channelGroup;
        boolean recursive;
        boolean distances;
        boolean self;
        double neighbors;
        double contact;
        EnumSet<DistanceMode> distanceModes;
        boolean pattern;
        EnumSet<PatternFunction> patternFunctions;
        double maximumRadius;
        double radiusBins;
        double simulations;
        String seed;
        EdgeCorrection correction;
        boolean project3D;
        double histogramBins;
        boolean autoSave;
        String output;
        boolean hideDisplay;

        static Settings defaults() {
            Settings settings = new Settings();
            String home = IJ.getDirectory("home");
            settings.input = home == null ? "" : home;
            settings.regex = DEFAULT_REGEX;
            settings.channelGroup = 2;
            settings.recursive = true;
            settings.distances = true;
            settings.self = true;
            settings.neighbors = 1;
            settings.contact = 0.0;
            settings.distanceModes = EnumSet.noneOf(DistanceMode.class);
            for (DistanceMode mode : DistanceMode.values()) {
                if (OPAParameters.isDefaultDistanceMode(mode)) {
                    settings.distanceModes.add(mode);
                }
            }
            settings.pattern = true;
            settings.patternFunctions = EnumSet.noneOf(PatternFunction.class);
            for (PatternFunction function : PatternFunction.values()) {
                if (OPAParameters.isDefaultPatternFunction(function)) {
                    settings.patternFunctions.add(function);
                }
            }
            settings.maximumRadius = 0.0;
            settings.radiusBins = 50;
            settings.simulations = OPAParameters.DEFAULT_SIMULATIONS;
            settings.seed = Long.toString(OPAParameters.DEFAULT_SEED);
            settings.correction = EdgeCorrection.TRANSLATION;
            settings.project3D = false;
            settings.histogramBins = 20;
            settings.autoSave = true;
            settings.output = "";
            settings.hideDisplay = false;
            return settings;
        }

        OPABatchParameters toParameters() {
            int group = DialogNumbers.wholeNumber(
                    channelGroup, "Channel capture group");
            long parsedSeed;
            try {
                parsedSeed = Long.parseLong(seed.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Random seed must be a whole number.");
            }
            OPAParameters options = OPAParameters.builder()
                    .runDistances(distances)
                    .runPattern(pattern)
                    .includeSelfDistances(self)
                    .distanceModes(distanceModes)
                    .neighborCount(DialogNumbers.wholeNumber(
                            neighbors, "Nearest-neighbour count"))
                    .contactDistance(contact)
                    .patternFunctions(patternFunctions)
                    .maximumRadius(maximumRadius)
                    .radiusBins(DialogNumbers.wholeNumber(
                            radiusBins, "Radius bin count"))
                    .simulations(DialogNumbers.wholeNumber(
                            simulations, "Monte Carlo simulation count"))
                    .seed(parsedSeed)
                    .edgeCorrection(correction)
                    .project3DToXY(project3D)
                    .histogramBins(DialogNumbers.wholeNumber(
                            histogramBins, "Histogram bin count"))
                    .build();
            return OPABatchParameters.builder(new File(input), regex, group)
                    .recursive(recursive)
                    .analysisTemplate(options)
                    .autoSave(autoSave)
                    .outputDirectory(outputDirectory(output))
                    .build();
        }
    }
}
