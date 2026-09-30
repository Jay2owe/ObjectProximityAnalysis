/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import sc.fiji.opa.core.spatial.EdgeCorrection;
import sc.fiji.opa.core.spatial.MonteCarloResult;
import sc.fiji.opa.core.spatial.PatternFunction;
import sc.fiji.opa.core.spatial.RectangularWindow;
import sc.fiji.opa.core.AnalysisCancelledException;
import sc.fiji.opa.core.CalibrationInfo;
import sc.fiji.opa.core.DistanceMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ImageJ 1.x menu and macro entry point.
 */
public final class Object_Proximity_Analysis implements PlugIn {

    private static final String TITLE = "Object Proximity Analysis";
    private static final String NONE = "[None]";
    private static final String LABEL_INPUT = "Open label images";
    private static final String ROI_INPUT = "ROI .zip/.roi sets";
    static final String SIMULATION_HINT = "The smallest attainable Monte Carlo "
            + "p-value is 1/(simulations+1). "
            + OPAParameters.DEFAULT_SIMULATIONS
            + " simulations give an exact 5% envelope.";

    @Override
    public void run(String argument) {
        int[] imageIds = WindowManager.getIDList();
        if (imageIds == null || imageIds.length == 0) {
            IJ.error("Object Proximity Analysis",
                    "Open at least one label or reference image first.");
            forgetRecordedCommand();
            return;
        }
        try {
            String[] imageChoices = imageChoices(imageIds);
            GenericDialog dialog = buildDialog(imageIds, imageChoices);
            dialog.showDialog();
            if (dialog.wasCanceled()) {
                forgetRecordedCommand();
                return;
            }
            DialogValues values = readDialog(dialog, imageIds, imageChoices);
            // Escape is a sticky global flag in ImageJ. Clearing it here stops
            // a keypress left over from an earlier command cancelling this run
            // before any work has been done.
            IJ.resetEscape();
            OPAResult result = OPA.run(values.parameters);
            if (!result.hasPhysicalCalibration()) {
                IJ.log("WARNING: Object Proximity Analysis input is uncalibrated. "
                        + "All distances and radii are reported in pixels.");
            }
            warnAboutSaturatedRadii(result);
            if (!values.hideDisplay) show(result);
            if (values.autoSave) {
                File saved = OPAOutput.save(
                        result,
                        new File(values.outputDirectory),
                        values.outputPrefix);
                IJ.log("Object Proximity Analysis saved to: "
                        + saved.getAbsolutePath());
            }
            IJ.showStatus("Object Proximity Analysis complete");
        } catch (AnalysisCancelledException exception) {
            IJ.resetEscape();
            forgetRecordedCommand();
            // The run stopped part-way; without this the status-bar progress
            // bar stayed drawn part-filled.
            IJ.showProgress(1.0);
            IJ.log("Object Proximity Analysis cancelled.");
            IJ.showStatus("Object Proximity Analysis cancelled");
        } catch (Exception exception) {
            forgetRecordedCommand();
            IJ.showProgress(1.0);
            UserErrors.report(TITLE, exception);
        } catch (OutOfMemoryError error) {
            forgetRecordedCommand();
            IJ.showProgress(1.0);
            UserErrors.report(TITLE, error);
        }
    }

    /**
     * ImageJ records a menu command when it returns. A run that was
     * cancelled, rejected or never started must not leave a line in the
     * Macro Recorder that would replay it.
     */
    static void forgetRecordedCommand() {
        if (Recorder.record) Recorder.setCommand(null);
    }

    private static GenericDialog buildDialog(int[] imageIds,
                                             String[] imageChoices) {
        // Laid out in pairs and checkbox grids so the whole dialog, with its
        // OK button, fits a 1080p laptop screen at 125% scaling. The add
        // order of each field type is unchanged, so every macro key and the
        // read order in readDialog are as before.
        GenericDialog dialog = new FittingDialog("Object Proximity Analysis");
        dialog.addChoice("Input_mode",
                new String[]{LABEL_INPUT, ROI_INPUT}, LABEL_INPUT);
        dialog.addToSameRow();
        dialog.addNumericField("Channel_count", Math.min(2, imageIds.length), 0);
        dialog.addChoice("ROI_reference_image", imageChoices,
                imageChoices.length > 1 ? imageChoices[1] : imageChoices[0]);
        for (int i = 0; i < OPAParameters.MAX_IMAGES; i++) {
            String defaultImage = i + 1 < imageChoices.length
                    ? imageChoices[i + 1]
                    : NONE;
            dialog.addChoice("Label_image_" + (i + 1),
                    imageChoices, defaultImage);
            dialog.addToSameRow();
            DialogFields.addFile(dialog, "ROI_set_" + (i + 1), "");
        }
        DialogFields.addFile(dialog, "Observation_region_ROI", "");
        dialog.addMessage(calibrationSummary(imageIds));

        // The Run_ checkboxes head their sections; separate headings would
        // push the OK button off a laptop screen.
        dialog.addCheckboxGroup(1, 2,
                new String[]{"Run_distances", "Include_self_distances"},
                new boolean[]{true, true});
        dialog.addNumericField("K_nearest_neighbours", 1, 0);
        dialog.addToSameRow();
        dialog.addNumericField("Contact_distance", 0.0, 3);
        DistanceMode[] modes = DistanceMode.values();
        String[] modeLabels = new String[modes.length];
        boolean[] modeDefaults = new boolean[modes.length];
        for (int i = 0; i < modes.length; i++) {
            modeLabels[i] = modes[i].getColumnName().replace('-', '_');
            modeDefaults[i] = OPAParameters.isDefaultDistanceMode(modes[i]);
        }
        dialog.addCheckboxGroup(rows(modes.length, 3), 3, modeLabels, modeDefaults);

        dialog.addCheckbox("Run_pattern_analysis", true);
        PatternFunction[] functions = PatternFunction.values();
        String[] functionLabels = new String[functions.length];
        boolean[] functionDefaults = new boolean[functions.length];
        for (int i = 0; i < functions.length; i++) {
            functionLabels[i] = "Function_" + functions[i].name();
            functionDefaults[i] =
                    OPAParameters.isDefaultPatternFunction(functions[i]);
        }
        dialog.addCheckboxGroup(rows(functions.length, 3), 3,
                functionLabels, functionDefaults);
        dialog.addNumericField("Maximum_radius_0_is_auto", 0.0, 3);
        dialog.addToSameRow();
        dialog.addNumericField("Radius_bins", 50, 0);
        dialog.addNumericField("Monte_Carlo_simulations",
                OPAParameters.DEFAULT_SIMULATIONS, 0);
        dialog.addToSameRow();
        dialog.addStringField("Random_seed",
                Long.toString(OPAParameters.DEFAULT_SEED), 12);
        dialog.addChoice("Edge_correction",
                new String[]{
                        EdgeCorrection.TRANSLATION.name(),
                        EdgeCorrection.BORDER.name(),
                        EdgeCorrection.NONE.name()
                },
                EdgeCorrection.TRANSLATION.name());
        dialog.addToSameRow();
        dialog.addCheckbox("Project_3D_centroids_to_XY", false);
        dialog.addMessage(SIMULATION_HINT);

        DialogFields.addDirectory(dialog, "Output_directory",
                IJ.getDirectory("home"));
        dialog.addStringField("Output_prefix", "Analysis", 16);
        dialog.addNumericField("Histogram_bins", 20, 0);
        dialog.addCheckboxGroup(1, 2,
                new String[]{"Auto_save", "Hide_display"},
                new boolean[]{false, false});
        dialog.addHelp("https://github.com/Jay2owe/ObjectProximityAnalysis");
        return dialog;
    }

    /** Rows needed to lay out {@code count} checkboxes in {@code columns}. */
    static int rows(int count, int columns) {
        return (count + columns - 1) / columns;
    }

    private static DialogValues readDialog(GenericDialog dialog,
                                           int[] imageIds,
                                           String[] imageChoices)
            throws Exception {
        String inputMode = DialogFields.nextChoice(dialog, "Input_mode");
        int channelCount = DialogNumbers.wholeNumber(
                dialog.getNextNumber(), "Channel count");
        String referenceChoice = DialogFields.nextChoice(
                dialog, "ROI_reference_image");
        if (channelCount < 1 || channelCount > OPAParameters.MAX_IMAGES) {
            throw new IllegalArgumentException("Channel count must be between 1 and 5.");
        }

        String[] labelChoices = new String[OPAParameters.MAX_IMAGES];
        String[] roiPaths = new String[OPAParameters.MAX_IMAGES];
        for (int i = 0; i < OPAParameters.MAX_IMAGES; i++) {
            labelChoices[i] = DialogFields.nextChoice(
                    dialog, "Label_image_" + (i + 1));
            roiPaths[i] = dialog.getNextString().trim();
        }
        String observationRoiPath = dialog.getNextString().trim();

        List<ImagePlus> images = new ArrayList<ImagePlus>();
        List<String> names = new ArrayList<String>();
        if (ROI_INPUT.equals(inputMode)) {
            checkNoUnusedRoiSets(roiPaths, channelCount);
            ImagePlus reference = selectedImage(
                    referenceChoice, imageIds, imageChoices);
            // Each call builds a fresh ImagePlus, so a repeated path would not
            // be caught by the identity check the label branch relies on. Two
            // channels loaded from one ROI set would report every distance as
            // zero without any warning.
            Set<String> usedRoiPaths = new HashSet<String>();
            for (int i = 0; i < channelCount; i++) {
                if (roiPaths[i].isEmpty()) {
                    throw new IllegalArgumentException(
                            "ROI set " + (i + 1) + " is required.");
                }
                if (!usedRoiPaths.add(canonicalPath(roiPaths[i]))) {
                    throw new IllegalArgumentException(
                            "Each label channel must use a different ROI set; "
                                    + roiPaths[i] + " is used more than once.");
                }
                ImagePlus labels = OPALabelImages.fromRoiSet(reference, roiPaths[i]);
                images.add(labels);
                names.add(labels.getTitle());
            }
        } else {
            for (int i = 0; i < channelCount; i++) {
                ImagePlus image = selectedImage(
                        labelChoices[i], imageIds, imageChoices);
                if (images.contains(image)) {
                    throw new IllegalArgumentException(
                            "Each label channel must use a different open image.");
                }
                images.add(image);
                names.add(labelChoices[i]);
            }
        }

        boolean runDistances = dialog.getNextBoolean();
        boolean selfDistances = dialog.getNextBoolean();
        int neighborCount = DialogNumbers.wholeNumber(
                dialog.getNextNumber(), "Nearest-neighbour count");
        double contactDistance = dialog.getNextNumber();
        EnumSet<DistanceMode> distanceModes =
                EnumSet.noneOf(DistanceMode.class);
        for (DistanceMode mode : DistanceMode.values()) {
            if (dialog.getNextBoolean()) distanceModes.add(mode);
        }

        boolean runPattern = dialog.getNextBoolean();
        EnumSet<PatternFunction> patternFunctions =
                EnumSet.noneOf(PatternFunction.class);
        for (PatternFunction function : PatternFunction.values()) {
            if (dialog.getNextBoolean()) patternFunctions.add(function);
        }
        double maximumRadius = dialog.getNextNumber();
        int radiusBins = DialogNumbers.wholeNumber(
                dialog.getNextNumber(), "Radius bin count");
        int simulations = DialogNumbers.wholeNumber(
                dialog.getNextNumber(), "Monte Carlo simulation count");
        long seed;
        try {
            seed = Long.parseLong(dialog.getNextString().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Random seed must be a whole number.");
        }
        EdgeCorrection correction = EdgeCorrection.valueOf(
                DialogFields.nextChoice(dialog, "Edge_correction"));
        boolean project3D = dialog.getNextBoolean();

        int histogramBins = DialogNumbers.wholeNumber(
                dialog.getNextNumber(), "Histogram bin count");
        boolean autoSave = dialog.getNextBoolean();
        String outputDirectory = dialog.getNextString().trim();
        String outputPrefix = dialog.getNextString().trim();
        boolean hideDisplay = dialog.getNextBoolean();

        OPAParameters.Builder builder = OPAParameters.builder(images)
                .channelNames(names)
                .runDistances(runDistances)
                .runPattern(runPattern)
                .includeSelfDistances(selfDistances)
                .distanceModes(distanceModes)
                .neighborCount(neighborCount)
                .contactDistance(contactDistance)
                .patternFunctions(patternFunctions)
                .maximumRadius(maximumRadius)
                .radiusBins(radiusBins)
                .simulations(simulations)
                .seed(seed)
                .edgeCorrection(correction)
                .project3DToXY(project3D)
                .histogramBins(histogramBins)
                .progressListener(new OPAProgressListener() {
                    @Override
                    public void onProgress(double fraction, String message) {
                        IJ.showProgress(fraction);
                        IJ.showStatus("Object Proximity Analysis: " + message);
                    }
                });
        if (!observationRoiPath.isEmpty()) {
            RectangularWindow window = LabelUtils.boundingWindow(
                    images.get(0), LabelUtils.loadRoiSet(observationRoiPath));
            builder.observationWindow(window);
        }
        if (autoSave && outputDirectory.isEmpty()) {
            throw new IllegalArgumentException(
                    "Choose an output directory when auto-save is enabled.");
        }
        return new DialogValues(
                builder.build(),
                autoSave,
                outputDirectory,
                outputPrefix,
                hideDisplay);
    }

    /**
     * A ROI set beyond the channel count would be ignored. ROI fields start
     * empty, so a filled one is the user's choice: with only the reference
     * image open the channel count defaults to 1, and a second ROI set was
     * silently left out of the analysis.
     */
    static void checkNoUnusedRoiSets(String[] roiPaths, int channelCount) {
        for (int i = channelCount; i < roiPaths.length; i++) {
            if (roiPaths[i] != null && !roiPaths[i].trim().isEmpty()) {
                throw new IllegalArgumentException("ROI set " + (i + 1)
                        + " is filled in but Channel count is " + channelCount
                        + ". Set Channel count to " + (i + 1)
                        + " or clear ROI set " + (i + 1) + ".");
            }
        }
    }

    /**
     * Identity of a path on disk, so the same ROI set written two different
     * ways is still recognised as a repeat.
     */
    private static String canonicalPath(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (java.io.IOException exception) {
            return new File(path).getAbsolutePath();
        }
    }

    private static void show(OPAResult result) {
        // Centroid tables are built and saved for every run, so a
        // distances-only run displays them too. Windows get readable titles;
        // the saved files keep their identity-hashed names.
        showAll("OPA Centroids - ", result.getCentroidTables());
        result.getProvenanceTable().show("OPA Analysis Provenance");
        showAll("OPA Objects - ", result.getPerObjectTables());
        if (result.getDistanceSummaryTable().size() > 0) {
            result.getDistanceSummaryTable().show("OPA Distance Summary");
        }
        if (result.getPatternSummaryTable().size() > 0) {
            result.getPatternSummaryTable().show("OPA Pattern Summary");
        }
        showAll("OPA Histogram - ", result.getHistogramTables());
        showAll("OPA ECDF - ", result.getEcdfTables());
        showAll("OPA Curve - ", result.getCurveTables());
        for (Plot plot : OPAPlots.lMinusRPlots(result)) plot.show();
    }

    private static void showAll(String prefix,
                                Map<String, ij.measure.ResultsTable> tables) {
        for (Map.Entry<String, ij.measure.ResultsTable> entry
                : WindowTitles.titled(prefix, tables).entrySet()) {
            entry.getValue().show(entry.getKey());
        }
    }

    private static String[] imageChoices(int[] imageIds) {
        String[] titles = new String[imageIds.length];
        for (int i = 0; i < imageIds.length; i++) {
            ImagePlus image = WindowManager.getImage(imageIds[i]);
            titles[i] = image == null
                    ? "Image " + imageIds[i]
                    : image.getTitle();
        }
        return disambiguateImageTitles(titles);
    }

    static String[] disambiguateImageTitles(String[] titles) {
        Map<String, Integer> totals = new HashMap<String, Integer>();
        for (String title : titles) {
            Integer count = totals.get(title);
            totals.put(title, count == null ? 1 : count + 1);
        }
        Map<String, Integer> occurrences = new HashMap<String, Integer>();
        Set<String> used = new HashSet<String>();
        used.add(NONE);
        String[] choices = new String[titles.length + 1];
        choices[0] = NONE;
        for (int i = 0; i < titles.length; i++) {
            String title = titles[i];
            Integer previous = occurrences.get(title);
            int occurrence = previous == null ? 1 : previous + 1;
            occurrences.put(title, occurrence);
            String choice = title;
            if (totals.get(title) > 1 || used.contains(choice)) {
                choice = title + " [window " + occurrence + "]";
            }
            while (used.contains(choice)) {
                choice += "_";
            }
            choices[i + 1] = choice;
            used.add(choice);
        }
        return choices;
    }

    private static ImagePlus selectedImage(String choice,
                                           int[] imageIds,
                                           String[] imageChoices) {
        for (int i = 1; i < imageChoices.length; i++) {
            if (imageChoices[i].equals(choice)) {
                ImagePlus image = WindowManager.getImage(imageIds[i - 1]);
                if (image != null) return image;
            }
        }
        throw new IllegalArgumentException(
                "Choose an open image instead of " + NONE + ".");
    }

    private static String calibrationSummary(int[] imageIds) {
        Map<String, List<String>> byCalibration =
                new java.util.LinkedHashMap<String, List<String>>();
        for (int imageId : imageIds) {
            ImagePlus image = WindowManager.getImage(imageId);
            if (image == null) continue;
            CalibrationInfo calibration = CalibrationInfo.from(image);
            String size = calibration.getPixelWidth() + " x "
                    + calibration.getPixelHeight()
                    + (image.getNSlices() > 1
                            ? " x " + calibration.getPixelDepth()
                            : "")
                    + " " + calibration.getUnit();
            List<String> titles = byCalibration.get(size);
            if (titles == null) {
                titles = new ArrayList<String>();
                byCalibration.put(size, titles);
            }
            titles.add(image.getTitle());
        }
        return calibrationSummary(byCalibration);
    }

    /**
     * One line per distinct voxel size rather than one per open image, so
     * the dialog does not grow off the screen when many images are open.
     */
    static String calibrationSummary(Map<String, List<String>> byCalibration) {
        StringBuilder text = new StringBuilder(
                "Detected calibration (uncalibrated inputs remain in pixels):");
        int shown = 0;
        for (Map.Entry<String, List<String>> entry : byCalibration.entrySet()) {
            if (shown == MAX_CALIBRATION_LINES) {
                text.append("\n... and ")
                        .append(byCalibration.size() - shown)
                        .append(" more voxel sizes");
                break;
            }
            List<String> titles = entry.getValue();
            text.append("\n").append(entry.getKey()).append(": ")
                    .append(titles.get(0));
            if (titles.size() > 1) {
                text.append(" and ").append(titles.size() - 1)
                        .append(titles.size() == 2 ? " other" : " others");
            }
            shown++;
        }
        return text.toString();
    }

    private static final int MAX_CALIBRATION_LINES = 3;

    private static final class DialogValues {
        private final OPAParameters parameters;
        private final boolean autoSave;
        private final String outputDirectory;
        private final String outputPrefix;
        private final boolean hideDisplay;

        private DialogValues(OPAParameters parameters,
                             boolean autoSave,
                             String outputDirectory,
                             String outputPrefix,
                             boolean hideDisplay) {
            this.parameters = parameters;
            this.autoSave = autoSave;
            this.outputDirectory = outputDirectory;
            this.outputPrefix = outputPrefix;
            this.hideDisplay = hideDisplay;
        }
    }

    /**
     * Tell the user when nearest-neighbour radii are past the point of being
     * informative, without changing what they asked for.
     *
     * <p>G climbs to 1 and stops. Past saturation every simulated curve takes
     * the same value there, the envelope collapses to a point and nothing can
     * escape it, so those radii produce a flat band that looks like a result
     * and is not one. The radii are still computed and still reported; this
     * only says what they can and cannot show.</p>
     */
    private static void warnAboutSaturatedRadii(OPAResult result) {
        for (PatternResult pattern : result.getPatternResults()) {
            MonteCarloResult statistics = pattern.getStatistics();
            if (!statistics.hasSaturatedRadii()) continue;
            String channels = pattern.getSourceChannel();
            if (pattern.isBivariate()) {
                channels += " to " + pattern.getTargetChannel();
            }
            IJ.log(String.format(
                    java.util.Locale.ROOT,
                    "WARNING: %s (%s) - %d of %d radii are at or beyond %.3g %s,"
                            + " where the curve has already reached 99%% of its"
                            + " maximum. The envelope there collapses and cannot"
                            + " be escaped, so those radii cannot show"
                            + " clustering or dispersion. They are still"
                            + " reported; interpret them as empty rather than"
                            + " as agreement with randomness.",
                    statistics.getFunction().name(),
                    channels,
                    statistics.getSaturatedRadiusCount(),
                    statistics.getRadii().length,
                    statistics.getSaturationRadius(),
                    pattern.getUnit()));
        }
    }
}
