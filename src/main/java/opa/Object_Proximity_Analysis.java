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
import sc.fiji.opa.core.spatial.EdgeCorrection;
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

    private static final String NONE = "[None]";
    private static final String LABEL_INPUT = "Open label images";
    private static final String ROI_INPUT = "ROI .zip/.roi sets";

    @Override
    public void run(String argument) {
        int[] imageIds = WindowManager.getIDList();
        if (imageIds == null || imageIds.length == 0) {
            IJ.error("Object Proximity Analysis",
                    "Open at least one label or reference image first.");
            return;
        }
        try {
            String[] imageChoices = imageChoices(imageIds);
            GenericDialog dialog = buildDialog(imageIds, imageChoices);
            dialog.showDialog();
            if (dialog.wasCanceled()) return;
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
            IJ.log("Object Proximity Analysis cancelled.");
            IJ.showStatus("Object Proximity Analysis cancelled");
        } catch (Exception exception) {
            IJ.handleException(exception);
        }
    }

    private static GenericDialog buildDialog(int[] imageIds,
                                             String[] imageChoices) {
        GenericDialog dialog = new GenericDialog("Object Proximity Analysis");
        dialog.addMessage("Inputs");
        dialog.addChoice("Input_mode",
                new String[]{LABEL_INPUT, ROI_INPUT}, LABEL_INPUT);
        dialog.addNumericField("Channel_count", Math.min(2, imageIds.length), 0);
        dialog.addChoice("ROI_reference_image", imageChoices,
                imageChoices.length > 1 ? imageChoices[1] : imageChoices[0]);
        for (int i = 0; i < OPAParameters.MAX_IMAGES; i++) {
            String defaultImage = i + 1 < imageChoices.length
                    ? imageChoices[i + 1]
                    : NONE;
            dialog.addChoice("Label_image_" + (i + 1),
                    imageChoices, defaultImage);
            dialog.addFileField("ROI_set_" + (i + 1), "");
        }
        dialog.addFileField("Observation_region_ROI", "");
        dialog.addMessage(calibrationSummary(imageIds));

        dialog.addMessage("Distances");
        dialog.addCheckbox("Run_distances", true);
        dialog.addCheckbox("Include_self_distances", true);
        dialog.addNumericField("K_nearest_neighbours", 1, 0);
        dialog.addNumericField("Contact_distance", 0.0, 3);
        for (DistanceMode mode : DistanceMode.values()) {
            dialog.addCheckbox(mode.getColumnName().replace('-', '_'),
                    OPAParameters.isDefaultDistanceMode(mode));
        }

        dialog.addMessage("2D point-pattern analysis");
        dialog.addCheckbox("Run_pattern_analysis", true);
        for (PatternFunction function : PatternFunction.values()) {
            dialog.addCheckbox("Function_" + function.name(),
                    OPAParameters.isDefaultPatternFunction(function));
        }
        dialog.addNumericField("Maximum_radius_0_is_auto", 0.0, 3);
        dialog.addNumericField("Radius_bins", 50, 0);
        dialog.addNumericField("Monte_Carlo_simulations", 99, 0);
        dialog.addStringField("Random_seed",
                Long.toString(OPAParameters.DEFAULT_SEED), 18);
        dialog.addChoice("Edge_correction",
                new String[]{
                        EdgeCorrection.TRANSLATION.name(),
                        EdgeCorrection.BORDER.name(),
                        EdgeCorrection.NONE.name()
                },
                EdgeCorrection.TRANSLATION.name());
        dialog.addCheckbox("Project_3D_centroids_to_XY", false);
        dialog.addMessage("The smallest attainable Monte Carlo p-value is "
                + "1/(simulations+1); 99 simulations cannot report p<0.01.");

        dialog.addMessage("Output");
        dialog.addNumericField("Histogram_bins", 20, 0);
        dialog.addCheckbox("Auto_save", false);
        dialog.addDirectoryField("Output_directory", IJ.getDirectory("home"));
        dialog.addStringField("Output_prefix", "Analysis", 24);
        dialog.addCheckbox("Hide_display", false);
        dialog.addHelp("https://github.com/Jay2owe/ObjectProximityAnalysis");
        return dialog;
    }

    private static DialogValues readDialog(GenericDialog dialog,
                                           int[] imageIds,
                                           String[] imageChoices)
            throws Exception {
        String inputMode = dialog.getNextChoice();
        int channelCount = DialogNumbers.wholeNumber(
                dialog.getNextNumber(), "Channel count");
        String referenceChoice = dialog.getNextChoice();
        if (channelCount < 1 || channelCount > OPAParameters.MAX_IMAGES) {
            throw new IllegalArgumentException("Channel count must be between 1 and 5.");
        }

        String[] labelChoices = new String[OPAParameters.MAX_IMAGES];
        String[] roiPaths = new String[OPAParameters.MAX_IMAGES];
        for (int i = 0; i < OPAParameters.MAX_IMAGES; i++) {
            labelChoices[i] = dialog.getNextChoice();
            roiPaths[i] = dialog.getNextString().trim();
        }
        String observationRoiPath = dialog.getNextString().trim();

        List<ImagePlus> images = new ArrayList<ImagePlus>();
        List<String> names = new ArrayList<String>();
        if (ROI_INPUT.equals(inputMode)) {
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
        EdgeCorrection correction =
                EdgeCorrection.valueOf(dialog.getNextChoice());
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
        // distances-only run displays them too.
        for (Map.Entry<String, ij.measure.ResultsTable> entry
                : result.getCentroidTables().entrySet()) {
            entry.getValue().show("OPA Centroids - " + entry.getKey());
        }
        result.getProvenanceTable().show("OPA Analysis Provenance");
        for (Map.Entry<String, ij.measure.ResultsTable> entry
                : result.getPerObjectTables().entrySet()) {
            entry.getValue().show("OPA Objects - " + entry.getKey());
        }
        if (result.getDistanceSummaryTable().size() > 0) {
            result.getDistanceSummaryTable().show("OPA Distance Summary");
        }
        if (result.getPatternSummaryTable().size() > 0) {
            result.getPatternSummaryTable().show("OPA Pattern Summary");
        }
        for (Map.Entry<String, ij.measure.ResultsTable> entry
                : result.getHistogramTables().entrySet()) {
            entry.getValue().show("OPA Histogram - " + entry.getKey());
        }
        for (Map.Entry<String, ij.measure.ResultsTable> entry
                : result.getEcdfTables().entrySet()) {
            entry.getValue().show("OPA ECDF - " + entry.getKey());
        }
        for (Map.Entry<String, ij.measure.ResultsTable> entry
                : result.getCurveTables().entrySet()) {
            entry.getValue().show("OPA Curve - " + entry.getKey());
        }
        for (Plot plot : OPAPlots.lMinusRPlots(result)) plot.show();
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
        StringBuilder text = new StringBuilder(
                "Detected calibration (uncalibrated inputs remain in pixels):");
        for (int imageId : imageIds) {
            ImagePlus image = WindowManager.getImage(imageId);
            if (image == null) continue;
            CalibrationInfo calibration = CalibrationInfo.from(image);
            text.append("\n")
                    .append(image.getTitle())
                    .append(": ")
                    .append(calibration.getPixelWidth())
                    .append(" x ")
                    .append(calibration.getPixelHeight());
            if (image.getNSlices() > 1) {
                text.append(" x ").append(calibration.getPixelDepth());
            }
            text.append(" ").append(calibration.getUnit());
        }
        return text.toString();
    }

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
}
