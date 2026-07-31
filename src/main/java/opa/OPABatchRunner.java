/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Dialog-free folder batch facade with the same grouping preview used by the
 * ImageJ batch entry point.
 */
public final class OPABatchRunner {

    private static final char IDENTITY_SEPARATOR = '\u001e';
    private static final char UNIT_SEPARATOR = '\u001f';

    private OPABatchRunner() {
    }

    public static String preview(OPABatchParameters parameters) {
        Compiled compiled = compile(parameters);
        return preview(
                scan(parameters, compiled.pattern),
                parameters.getAnalysisTemplate());
    }

    public static OPABatchResult run(OPABatchParameters parameters) {
        Compiled compiled = compile(parameters);
        List<Group> groups = scan(parameters, compiled.pattern);
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("No matching files found in: "
                    + parameters.getInputFolder());
        }

        int validGroups = 0;
        for (Group group : groups) {
            if (group.isRunnable(parameters.getAnalysisTemplate())) validGroups++;
        }
        int processed = 0;
        int skipped = 0;
        int errors = 0;
        boolean cancelled = false;
        List<String> errorMessages = new ArrayList<String>();
        ResultsTable distanceSummary = new ResultsTable();
        ResultsTable patternSummary = new ResultsTable();
        ResultsTable groupManifest = groupManifest(
                groups,
                parameters.getAnalysisTemplate(),
                parameters.getInputFolder());
        CurveAccumulator curves = new CurveAccumulator();
        EcdfAccumulator ecdfs = new EcdfAccumulator();
        File output = parameters.getOutputDirectory() == null
                ? parameters.getInputFolder()
                : parameters.getOutputDirectory();
        final OPAProgressListener callerProgress =
                parameters.getAnalysisTemplate().getProgressListener();

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            if (IJ.escapePressed()) {
                IJ.resetEscape();
                skipped += groups.size() - groupIndex;
                cancelled = true;
                markCancelled(groupManifest, groupIndex, groups.size());
                break;
            }
            Group group = groups.get(groupIndex);
            if (!group.isRunnable(parameters.getAnalysisTemplate())) {
                skipped++;
                reportCallerProgress(
                        callerProgress,
                        (groupIndex + 1.0) / groups.size(),
                        "Batch group " + (groupIndex + 1)
                                + " of " + groups.size()
                                + " (" + group.displayName()
                                + "): skipped invalid group");
                IJ.showProgress(groupIndex + 1, groups.size());
                if (IJ.escapePressed()) {
                    IJ.resetEscape();
                    cancelled = true;
                    int remaining = groups.size() - groupIndex - 1;
                    skipped += remaining;
                    markCancelled(
                            groupManifest, groupIndex + 1, groups.size());
                    break;
                }
                continue;
            }
            IJ.showStatus("OPA batch: " + group.displayName()
                    + " (" + (groupIndex + 1) + "/" + groups.size() + ")");
            IJ.showProgress(groupIndex, groups.size());
            List<ImagePlus> images = new ArrayList<ImagePlus>();
            try {
                List<String> channelNames = new ArrayList<String>();
                for (BatchFile batchFile : group.files) {
                    ImagePlus image = IJ.openImage(
                            batchFile.file.getAbsolutePath());
                    if (image == null) {
                        throw new IOException(
                                "Could not open " + batchFile.file.getAbsolutePath());
                    }
                    images.add(image);
                    channelNames.add(batchFile.channel);
                }
                final int progressGroupIndex = groupIndex;
                final int progressGroupCount = groups.size();
                final String progressGroupName = group.displayName();
                OPAParameters analysis = OPAParameters.builderFrom(
                                parameters.getAnalysisTemplate())
                        .images(images)
                        .channelNames(channelNames)
                        .progressListener(new OPAProgressListener() {
                            @Override
                            public void onProgress(
                                    double fraction, String message) {
                                double batchFraction =
                                        (progressGroupIndex + fraction)
                                                / progressGroupCount;
                                String batchMessage =
                                        "Batch group "
                                                + (progressGroupIndex + 1)
                                                + " of " + progressGroupCount
                                                + " (" + progressGroupName + "): "
                                                + message;
                                reportCallerProgress(
                                        callerProgress,
                                        batchFraction,
                                        batchMessage);
                                IJ.showProgress(
                                        batchFraction);
                                IJ.showStatus(
                                        "OPA batch: " + progressGroupName
                                                + ": " + message);
                            }
                        })
                        .build();
                OPAResult result = OPA.run(analysis);
                String calibrationWarning = result.hasPhysicalCalibration()
                        ? ""
                        : "UNCALIBRATED_INPUT: distances and radii are in pixels.";
                String analysisWarnings = analysisWarnings(result);
                groupManifest.setValue(
                        "Calibration_Warning", groupIndex, calibrationWarning);
                groupManifest.setValue(
                        "Analysis_Warnings", groupIndex, analysisWarnings);
                if (!calibrationWarning.isEmpty()) {
                    IJ.log("WARNING: OPA batch group " + group.displayName()
                            + " is uncalibrated; distances and radii are in pixels.");
                }
                if (!analysisWarnings.isEmpty()) {
                    IJ.log("WARNING: OPA batch group " + group.displayName()
                            + " completed with " + analysisWarnings + ".");
                }
                if (parameters.isAutoSave()) {
                    OPAOutput.save(
                            result,
                            output,
                            group.outputPrefix(parameters.getInputFolder()));
                }
                appendTable(distanceSummary, result.getDistanceSummaryTable(),
                        group, parameters.getInputFolder());
                appendTable(patternSummary, result.getPatternSummaryTable(),
                        group, parameters.getInputFolder());
                curves.add(result.getCurveTables());
                ecdfs.add(result.getEcdfTables());
                processed++;
                groupManifest.setValue("Outcome", groupIndex, "PROCESSED");
            } catch (AnalysisCancelledException exception) {
                IJ.resetEscape();
                cancelled = true;
                skipped += groups.size() - groupIndex;
                markCancelled(groupManifest, groupIndex, groups.size());
            } catch (Exception exception) {
                errors++;
                groupManifest.setValue("Outcome", groupIndex, "ERROR");
                groupManifest.setValue(
                        "Error_Message", groupIndex, exception.getMessage());
                errorMessages.add(group.displayName() + ": "
                        + exception.getMessage());
                IJ.log("OPA batch error - " + group.displayName()
                        + ": " + exception.getMessage());
            } finally {
                for (ImagePlus image : images) {
                    image.changes = false;
                    image.close();
                }
            }
            if (cancelled) break;
        }

        Map<String, ResultsTable> meanCurves = curves.tables();
        Map<String, ResultsTable> meanEcdfs = ecdfs.tables();
        if (!cancelled) {
            reportCallerProgress(
                    callerProgress,
                    1.0,
                    "Batch complete: " + processed + " processed, "
                            + skipped + " skipped, " + errors
                            + " group errors");
            if (IJ.escapePressed()) {
                IJ.resetEscape();
                cancelled = true;
            }
        }
        if (parameters.isAutoSave()) {
            try {
                saveAggregates(
                        parameters,
                        output,
                        distanceSummary,
                        patternSummary,
                        groupManifest,
                        meanCurves,
                        meanEcdfs,
                        errorMessages,
                        cancelled,
                        processed,
                        skipped,
                        errors);
            } catch (IOException exception) {
                errorMessages.add("Saving batch aggregates: "
                        + exception.getMessage());
            }
        }
        if (cancelled) {
            IJ.log("OPA batch cancelled: " + processed + " processed, "
                    + skipped + " skipped, " + errors + " errors.");
            IJ.showStatus("OPA batch cancelled");
        }
        IJ.showProgress(1.0);
        return new OPABatchResult(
                groups.size(),
                validGroups,
                processed,
                skipped,
                errors,
                cancelled,
                output,
                distanceSummary,
                patternSummary,
                groupManifest,
                meanCurves,
                meanEcdfs,
                errorMessages);
    }

    private static void reportCallerProgress(
            OPAProgressListener listener,
            double fraction,
            String message) {
        if (listener != null) {
            listener.onProgress(
                    Math.max(0.0, Math.min(1.0, fraction)),
                    message);
        }
    }

    private static Compiled compile(OPABatchParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException(
                    "OPA batch parameters must not be null.");
        }
        if (parameters.getInputFolder() == null
                || !parameters.getInputFolder().isDirectory()) {
            throw new IllegalArgumentException(
                    "Input folder does not exist: " + parameters.getInputFolder());
        }
        if (parameters.getFilenameRegex() == null
                || parameters.getFilenameRegex().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Filename regular expression must not be empty.");
        }
        if (parameters.getChannelCaptureGroup() < 1) {
            throw new IllegalArgumentException(
                    "Channel capture group must be 1 or greater.");
        }
        if (parameters.getAnalysisTemplate() == null) {
            throw new IllegalArgumentException(
                    "Analysis template must not be null.");
        }
        if (parameters.isAutoSave()
                && parameters.getOutputDirectory() != null
                && parameters.getOutputDirectory().exists()
                && !parameters.getOutputDirectory().isDirectory()) {
            throw new IllegalArgumentException(
                    "Output path is not a directory: "
                            + parameters.getOutputDirectory());
        }
        try {
            Pattern pattern = Pattern.compile(parameters.getFilenameRegex());
            if (pattern.matcher("").groupCount()
                    < parameters.getChannelCaptureGroup()) {
                throw new IllegalArgumentException(
                        "Channel capture group "
                                + parameters.getChannelCaptureGroup()
                                + " does not exist in the filename expression.");
            }
            return new Compiled(pattern);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(
                    "Invalid filename regular expression: "
                            + exception.getMessage(), exception);
        }
    }

    private static List<Group> scan(OPABatchParameters parameters,
                                    Pattern pattern) {
        List<Group> groups = new ArrayList<Group>();
        scanDirectory(
                parameters.getInputFolder(),
                parameters.getInputFolder(),
                "",
                pattern,
                parameters.getChannelCaptureGroup(),
                parameters.isRecursive(),
                groups);
        Collections.sort(groups, new Comparator<Group>() {
            @Override
            public int compare(Group first, Group second) {
                int folder = first.relativeFolder.compareTo(second.relativeFolder);
                return folder != 0 ? folder : first.key.compareTo(second.key);
            }
        });
        return groups;
    }

    private static void scanDirectory(File root,
                                      File directory,
                                      String relative,
                                      Pattern pattern,
                                      int channelGroup,
                                      boolean recursive,
                                      List<Group> output) {
        Map<String, Group> local = new LinkedHashMap<String, Group>();
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File child : children) {
            if (!child.isFile()) continue;
            Matcher matcher = pattern.matcher(child.getName());
            if (!matcher.matches()) continue;
            int channelStart = matcher.start(channelGroup);
            int channelEnd = matcher.end(channelGroup);
            String key = channelStart < 0
                    ? child.getName() + "*[unmatched-channel-capture]"
                    : child.getName().substring(0, channelStart)
                            + "*"
                            + child.getName().substring(channelEnd);
            Group group = local.get(key);
            if (group == null) {
                group = new Group(relative, key);
                local.put(key, group);
            }
            group.files.add(new BatchFile(
                    child,
                    channelStart < 0 ? null : matcher.group(channelGroup)));
        }
        for (Group group : local.values()) {
            Collections.sort(group.files, new Comparator<BatchFile>() {
                @Override
                public int compare(BatchFile first, BatchFile second) {
                    int channel = first.channel.compareTo(second.channel);
                    return channel != 0
                            ? channel
                            : first.file.getName().compareTo(second.file.getName());
                }
            });
            output.add(group);
        }
        if (!recursive) return;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String childRelative = relative.isEmpty()
                    ? child.getName()
                    : relative + "/" + child.getName();
            scanDirectory(
                    root,
                    child,
                    childRelative,
                    pattern,
                    channelGroup,
                    true,
                    output);
        }
    }

    private static String preview(List<Group> groups,
                                  OPAParameters analysisTemplate) {
        if (groups.isEmpty()) return "No matching files found.";
        int runnable = 0;
        int fileCount = 0;
        StringBuilder text = new StringBuilder();
        for (Group group : groups) {
            if (group.isRunnable(analysisTemplate)) runnable++;
            fileCount += group.files.size();
        }
        text.append(groups.size()).append(" group(s), ")
                .append(runnable).append(" runnable, ")
                .append(fileCount).append(" file(s)\n\n");
        for (Group group : groups) {
            String rejection = group.rejectionReason(analysisTemplate);
            text.append(group.relativeFolder.isEmpty()
                            ? "(root)/"
                            : group.relativeFolder + "/")
                    .append(group.key)
                    .append("  (")
                    .append(group.files.size())
                    .append(rejection == null
                            ? ""
                            : " - SKIP: " + rejection)
                    .append(")\n");
            for (BatchFile file : group.files) {
                text.append("  [").append(file.channel).append("] ")
                        .append(file.file.getName());
                if (!file.originalChannel.equals(file.channel)) {
                    text.append("  (captured as [")
                            .append(file.originalChannel)
                            .append("])");
                }
                text.append("\n");
            }
        }
        return text.toString();
    }

    private static ResultsTable groupManifest(
            List<Group> groups,
            OPAParameters analysisTemplate,
            File inputRoot) {
        ResultsTable table = new ResultsTable();
        for (Group group : groups) {
            String rejection = group.rejectionReason(analysisTemplate);
            table.incrementCounter();
            table.addValue(
                    "Input_Root", inputRoot.getAbsolutePath());
            table.addValue("Folder", group.relativeFolder);
            table.addValue("Group", group.displayName());
            table.addValue("Group_Identity", group.identity());
            table.addValue("Input_Files", group.inputFiles());
            table.addValue("Channel_Captures", group.channelCaptures());
            table.addValue("Runnable", rejection == null ? 1 : 0);
            table.addValue("Rejection_Reason",
                    rejection == null ? "" : rejection);
            table.addValue("Outcome",
                    rejection == null ? "PENDING" : "SKIPPED_INVALID");
            table.addValue("Calibration_Warning", "");
            table.addValue("Analysis_Warnings", "");
            table.addValue("Error_Message", "");
        }
        return table;
    }

    private static String analysisWarnings(OPAResult result) {
        List<String> warnings = new ArrayList<String>();
        collectWarnings(
                warnings, "DISTANCE", result.getDistanceSummaryTable());
        collectWarnings(
                warnings, "PATTERN", result.getPatternSummaryTable());
        collectWarnings(
                warnings,
                "PATTERN_ENVELOPE",
                result.getPatternSummaryTable(),
                "Envelope_Status");
        StringBuilder text = new StringBuilder();
        for (String warning : warnings) {
            if (text.length() > 0) text.append(';');
            text.append(warning);
        }
        return text.toString();
    }

    private static void collectWarnings(List<String> warnings,
                                        String kind,
                                        ResultsTable table) {
        collectWarnings(warnings, kind, table, "Status");
    }

    private static void collectWarnings(List<String> warnings,
                                        String kind,
                                        ResultsTable table,
                                        String statusHeading) {
        if (table == null || !hasHeading(table, statusHeading)) return;
        for (int row = 0; row < table.size(); row++) {
            String status = table.getStringValue(statusHeading, row);
            if (status == null || status.isEmpty() || "OK".equals(status)) {
                continue;
            }
            String warning = kind + ":" + status;
            if (!warnings.contains(warning)) warnings.add(warning);
        }
    }

    private static boolean hasHeading(ResultsTable table, String expected) {
        for (String heading : table.getHeadings()) {
            if (expected.equals(heading)) return true;
        }
        return false;
    }

    private static void markCancelled(ResultsTable manifest,
                                      int firstRow,
                                      int rowCount) {
        for (int row = firstRow; row < rowCount; row++) {
            if ("PENDING".equals(
                    manifest.getStringValue("Outcome", row))) {
                manifest.setValue("Outcome", row, "CANCELLED");
            }
        }
    }

    private static void appendTable(ResultsTable target,
                                    ResultsTable source,
                                    Group group,
                                    File inputRoot) {
        if (source == null) return;
        String[] headings = source.getHeadings();
        for (int row = 0; row < source.size(); row++) {
            target.incrementCounter();
            target.addValue(
                    "Input_Root", inputRoot.getAbsolutePath());
            target.addValue("Folder", group.relativeFolder);
            target.addValue("Group", group.displayName());
            target.addValue("Group_Identity", group.identity());
            target.addValue("Input_Files", group.inputFiles());
            target.addValue("Channel_Captures", group.channelCaptures());
            for (String heading : headings) {
                if (heading == null || heading.trim().isEmpty()) continue;
                if ("Seed".equals(heading)
                        || "Source_Channel".equals(heading)
                        || "Target_Channel".equals(heading)
                        || "Mode".equals(heading)
                        || "Unit".equals(heading)
                        || "Function".equals(heading)
                        || "Radius_Unit".equals(heading)
                        || "Value_Unit".equals(heading)
                        || "Status".equals(heading)
                        || "Envelope_Status".equals(heading)) {
                    target.addValue(
                            heading, source.getStringValue(heading, row));
                    continue;
                }
                target.addValue(heading, source.getValue(heading, row));
            }
        }
    }

    private static void saveAggregates(
            OPABatchParameters parameters,
            File parent,
            ResultsTable distanceSummary,
            ResultsTable patternSummary,
            ResultsTable groupManifest,
            Map<String, ResultsTable> curves,
            Map<String, ResultsTable> ecdfs,
            List<String> errors,
            boolean cancelled,
            int processed,
            int skipped,
            int groupErrors) throws IOException {
        File batchRoot = new File(
                new File(parent, "Object Proximity Analysis"), "Folder");
        if (!batchRoot.isDirectory() && !batchRoot.mkdirs()) {
            throw new IOException(
                    "Could not create batch root folder: "
                            + batchRoot.getAbsolutePath());
        }
        OPAOutput.writeTextAtomically(
                new File(batchRoot, "README.txt"),
                "Each Batch__ subfolder is one input-root, settings, "
                        + "and output-shape identity."
                        + System.lineSeparator());
        File folder = new File(
                batchRoot,
                batchRunDirectory(
                        parameters,
                        distanceSummary,
                        patternSummary,
                        groupManifest,
                        curves,
                        ecdfs));
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException(
                    "Could not create batch folder: " + folder.getAbsolutePath());
        }
        Map<File, ResultsTable> outputTables =
                new LinkedHashMap<File, ResultsTable>();
        outputTables.put(new File(
                folder, "OPA_Batch_Distance_Summary.csv"), distanceSummary);
        outputTables.put(new File(
                folder, "OPA_Batch_Pattern_Summary.csv"), patternSummary);
        outputTables.put(new File(
                folder, "OPA_Batch_Group_Manifest.csv"), groupManifest);
        for (Map.Entry<String, ResultsTable> entry : curves.entrySet()) {
            outputTables.put(new File(
                    folder,
                    aggregateFilename(
                            "OPA_Batch_Mean_Curve__", entry.getKey())),
                    entry.getValue());
        }
        for (Map.Entry<String, ResultsTable> entry : ecdfs.entrySet()) {
            outputTables.put(new File(
                    folder,
                    aggregateFilename(
                            "OPA_Batch_Mean_ECDF__", entry.getKey())),
                    entry.getValue());
        }
        OPAOutput.saveTablesAtomically(outputTables);
        StringBuilder readme = new StringBuilder();
        readme.append(
                "Folder-batch scalar summaries, mean point-pattern curves, "
                        + "and mean ECDFs. Spread columns are between-group sample SD.");
        readme.append(System.lineSeparator());
        readme.append("Status: ")
                .append(cancelled ? "CANCELLED" : "COMPLETE")
                .append("; processed=").append(processed)
                .append("; skipped=").append(skipped)
                .append("; group_errors=").append(groupErrors).append(".");
        readme.append(System.lineSeparator());
        if (!errors.isEmpty()) {
            readme.append(System.lineSeparator());
            readme.append("Errors:");
            readme.append(System.lineSeparator());
            for (String error : errors) {
                readme.append("- ").append(error)
                        .append(System.lineSeparator());
            }
        }
        OPAOutput.writeTextAtomically(
                new File(folder, "README.txt"), readme.toString());
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String batchRunDirectory(
            OPABatchParameters parameters,
            ResultsTable distanceSummary,
            ResultsTable patternSummary,
            ResultsTable groupManifest,
            Map<String, ResultsTable> curves,
            Map<String, ResultsTable> ecdfs) {
        String readable = safe(parameters.getInputFolder().getName());
        if (readable.isEmpty()) readable = "Batch";
        if (readable.length() > 48) {
            readable = readable.substring(0, 48);
        }
        return "Batch__" + readable + "__"
                + sha256(
                        batchRunIdentity(parameters)
                                + batchOutputShapeIdentity(
                                        distanceSummary,
                                        patternSummary,
                                        groupManifest,
                                        curves,
                                        ecdfs));
    }

    private static String batchOutputShapeIdentity(
            ResultsTable distanceSummary,
            ResultsTable patternSummary,
            ResultsTable groupManifest,
            Map<String, ResultsTable> curves,
            Map<String, ResultsTable> ecdfs) {
        StringBuilder identity = new StringBuilder();
        appendIdentityField(
                identity,
                "DISTANCE_ROWS=" + distanceSummary.size());
        appendIdentityField(
                identity,
                "PATTERN_ROWS=" + patternSummary.size());
        for (int row = 0; row < groupManifest.size(); row++) {
            appendIdentityField(
                    identity,
                    groupManifest.getStringValue(
                            "Group_Identity", row));
        }
        for (String key : curves.keySet()) {
            appendIdentityField(identity, "CURVE=" + key);
        }
        for (String key : ecdfs.keySet()) {
            appendIdentityField(identity, "ECDF=" + key);
        }
        return identity.toString();
    }

    private static String batchRunIdentity(
            OPABatchParameters parameters) {
        OPAParameters analysis = parameters.getAnalysisTemplate();
        double[] radii = analysis.getRadii();
        String window = analysis.getObservationWindow() == null
                ? "FULL_IMAGE"
                : analysis.getObservationWindow().getMinX() + ","
                        + analysis.getObservationWindow().getMinY() + ","
                        + analysis.getObservationWindow().getMaxX() + ","
                        + analysis.getObservationWindow().getMaxY();
        return canonicalIdentity(
                "BATCH_RUN",
                parameters.getInputFolder().getAbsolutePath(),
                parameters.getFilenameRegex(),
                Integer.toString(parameters.getChannelCaptureGroup()),
                Boolean.toString(parameters.isRecursive()),
                Boolean.toString(analysis.isRunDistances()),
                Boolean.toString(analysis.isRunPattern()),
                Boolean.toString(analysis.isIncludeSelfDistances()),
                analysis.getDistanceModes().toString(),
                Integer.toString(analysis.getNeighborCount()),
                Double.toString(analysis.getContactDistance()),
                analysis.getPatternFunctions().toString(),
                radii == null ? "AUTO" : Arrays.toString(radii),
                Double.toString(analysis.getMaximumRadius()),
                Integer.toString(analysis.getRadiusBins()),
                Integer.toString(analysis.getSimulations()),
                Long.toString(analysis.getSeed()),
                String.valueOf(analysis.getEdgeCorrection()),
                Boolean.toString(analysis.isProject3DToXY()),
                Integer.toString(analysis.getHistogramBins()),
                Boolean.toString(
                        analysis.isRequirePhysicalCalibration()),
                window);
    }

    private static String aggregateFilename(String prefix, String identity) {
        String label = safe(identity);
        if (label.length() > 80) label = label.substring(0, 80);
        return prefix + label + "__" + sha256(identity) + ".csv";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8));
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

    private static String aggregateKey(String displayLabel,
                                       String rawIdentity,
                                       String firstUnit,
                                       String secondUnit) {
        return displayLabel + IDENTITY_SEPARATOR + rawIdentity
                + UNIT_SEPARATOR + encodeIdentityPart(firstUnit)
                + UNIT_SEPARATOR + encodeIdentityPart(secondUnit);
    }

    private static String[] unitsFromAggregateKey(String key) {
        int last = key.lastIndexOf(UNIT_SEPARATOR);
        int first = last < 0 ? -1 : key.lastIndexOf(UNIT_SEPARATOR, last - 1);
        if (first < 0 || last < 0) return new String[]{"", ""};
        return new String[]{
                decodeIdentityPart(key.substring(first + 1, last)),
                decodeIdentityPart(key.substring(last + 1))
        };
    }

    private static String displayAggregateKey(String key) {
        int first = key.indexOf(UNIT_SEPARATOR);
        String base = first < 0 ? key : key.substring(0, first);
        int identity = base.indexOf(IDENTITY_SEPARATOR);
        if (identity >= 0) base = base.substring(0, identity);
        String[] units = unitsFromAggregateKey(key);
        return base + "__" + units[0] + "__" + units[1];
    }

    private static String aggregateResultKey(String key) {
        return displayAggregateKey(key) + "__" + sha256(key);
    }

    private static String canonicalIdentity(String kind,
                                            String... fields) {
        StringBuilder identity = new StringBuilder();
        appendIdentityField(identity, kind);
        for (String field : fields) {
            appendIdentityField(identity, field == null ? "" : field);
        }
        return identity.toString();
    }

    private static void appendIdentityField(StringBuilder identity,
                                            String value) {
        identity.append(value.length()).append(':').append(value);
    }

    private static String encodeIdentityPart(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeIdentityPart(String value) {
        return new String(
                Base64.getUrlDecoder().decode(value),
                StandardCharsets.UTF_8);
    }

    private static String curveDisplayLabel(String source,
                                            String target,
                                            String function) {
        String channels = safe(source);
        if (target != null && !target.isEmpty()) {
            channels += "_to_" + safe(target);
        }
        return channels + "__" + safe(function);
    }

    private static String distributionDisplayLabel(String source,
                                                   String target,
                                                   String mode,
                                                   String rank) {
        return safe(source) + "_to_" + safe(target)
                + "__" + safe(mode) + "__NN" + rank;
    }

    private static final class Compiled {
        private final Pattern pattern;

        private Compiled(Pattern pattern) {
            this.pattern = pattern;
        }
    }

    private static final class BatchFile {
        private final File file;
        private final String originalChannel;
        private final String channel;

        private BatchFile(File file, String channel) {
            this.file = file;
            this.originalChannel = channel == null ? "" : channel;
            this.channel = this.originalChannel.trim();
        }
    }

    private static final class Group {
        private final String relativeFolder;
        private final String key;
        private final List<BatchFile> files = new ArrayList<BatchFile>();

        private Group(String relativeFolder, String key) {
            this.relativeFolder = relativeFolder;
            this.key = key;
        }

        private boolean isRunnable(OPAParameters analysisTemplate) {
            return rejectionReason(analysisTemplate) == null;
        }

        private String rejectionReason(OPAParameters analysisTemplate) {
            if (files.isEmpty()) return "no channel files";
            if (files.size() > OPAParameters.MAX_IMAGES) {
                return "more than " + OPAParameters.MAX_IMAGES + " channels";
            }
            Set<String> channels = new HashSet<String>();
            for (BatchFile file : files) {
                if (file.channel == null || file.channel.trim().isEmpty()
                        || !channels.add(file.channel)) {
                    return "empty or duplicate channel names";
                }
            }
            try {
                OPA.validateOptions(analysisTemplate, files.size());
            } catch (IllegalArgumentException exception) {
                return exception.getMessage();
            }
            return null;
        }

        private String displayName() {
            String name = key.replace("*", "")
                    .replaceAll("^[_\\-./\\\\]+|[_\\-./\\\\]+$", "")
                    .replaceAll("\\.[^.]+$", "");
            return name.isEmpty() ? "batch" : name;
        }

        private String identity() {
            return relativeFolder.isEmpty()
                    ? key
                    : relativeFolder + "/" + key;
        }

        private String inputFiles() {
            StringBuilder text = new StringBuilder();
            for (BatchFile file : files) {
                if (text.length() > 0) text.append("; ");
                text.append(file.file.getName());
            }
            return text.toString();
        }

        private String channelCaptures() {
            StringBuilder text = new StringBuilder();
            for (BatchFile file : files) {
                if (text.length() > 0) text.append("; ");
                text.append(file.channel)
                        .append(" <- [")
                        .append(file.originalChannel)
                        .append("]");
            }
            return text.toString();
        }

        private String outputPrefix(File inputRoot) {
            String tokenIdentity = inputRoot.getAbsolutePath()
                    + "\u0000" + relativeFolder + "\u0000" + key;
            String readable = safe(displayName());
            if (readable.length() > 48) {
                readable = readable.substring(0, 48);
            }
            return readable + "__" + sha256(tokenIdentity);
        }
    }

    private static final class CurveAccumulator {
        private final Map<String, List<TreeMap<Double, Double>>> values =
                new LinkedHashMap<String, List<TreeMap<Double, Double>>>();
        private final Map<String, String[]> identities =
                new LinkedHashMap<String, String[]>();

        private void add(Map<String, ResultsTable> tables) {
            for (Map.Entry<String, ResultsTable> entry : tables.entrySet()) {
                ResultsTable table = entry.getValue();
                if (table.size() == 0) continue;
                String radiusUnit = table.getStringValue("Radius_Unit", 0);
                String valueUnit = table.getStringValue("Value_Unit", 0);
                String source = table.getStringValue("Source_Channel", 0);
                String target = table.getStringValue("Target_Channel", 0);
                String function = table.getStringValue("Function", 0);
                String aggregateKey = aggregateKey(
                        curveDisplayLabel(source, target, function),
                        canonicalIdentity(
                                "CURVE",
                                source, target, function),
                        radiusUnit,
                        valueUnit);
                identities.put(aggregateKey,
                        new String[]{source, target, function});
                List<TreeMap<Double, Double>> curves = values.get(aggregateKey);
                if (curves == null) {
                    curves = new ArrayList<TreeMap<Double, Double>>();
                    values.put(aggregateKey, curves);
                }
                TreeMap<Double, Double> curve = new TreeMap<Double, Double>();
                for (int row = 0; row < table.size(); row++) {
                    double radius = table.getValue("Radius", row);
                    double observed = table.getValue("Observed", row);
                    if (!Double.isFinite(radius) || !Double.isFinite(observed)) continue;
                    curve.put(radius, observed);
                }
                if (!curve.isEmpty()) curves.add(curve);
            }
        }

        private Map<String, ResultsTable> tables() {
            Map<String, ResultsTable> result =
                    new LinkedHashMap<String, ResultsTable>();
            for (Map.Entry<String, List<TreeMap<Double, Double>>> entry
                    : values.entrySet()) {
                ResultsTable table = new ResultsTable();
                List<TreeMap<Double, Double>> curves = entry.getValue();
                double sharedMinimum = Double.NEGATIVE_INFINITY;
                double sharedMaximum = Double.POSITIVE_INFINITY;
                int gridCount = 0;
                for (TreeMap<Double, Double> curve : curves) {
                    sharedMinimum = Math.max(
                            sharedMinimum, curve.firstKey());
                    sharedMaximum = Math.min(
                            sharedMaximum, curve.lastKey());
                    gridCount = Math.max(gridCount, curve.size());
                }
                if (curves.isEmpty() || sharedMinimum > sharedMaximum) {
                    table.incrementCounter();
                    table.addValue("Radius", Double.NaN);
                    table.addValue("Mean_Observed", Double.NaN);
                    table.addValue("SD_Observed", Double.NaN);
                    table.addValue("Mean_Minus_SD", Double.NaN);
                    table.addValue("Mean_Plus_SD", Double.NaN);
                    table.addValue("Group_N", 0);
                    table.addValue("Aggregation_Status",
                            "NO_SHARED_RADIUS_RANGE");
                    addIdentity(table, entry.getKey());
                    String[] units = unitsFromAggregateKey(entry.getKey());
                    table.addValue("Radius_Unit", units[0]);
                    table.addValue("Value_Unit", units[1]);
                    result.put(aggregateResultKey(entry.getKey()), table);
                    continue;
                }
                if (sharedMinimum == sharedMaximum) gridCount = 1;
                for (int grid = 0; grid < gridCount; grid++) {
                    double radius = gridCount == 1
                            ? sharedMinimum
                            : sharedMinimum
                                    + (sharedMaximum - sharedMinimum)
                                    * grid / (gridCount - 1);
                    List<Double> samples = new ArrayList<Double>(curves.size());
                    for (TreeMap<Double, Double> curve : curves) {
                        samples.add(interpolate(curve, radius));
                    }
                    Stats stats = Stats.of(samples);
                    table.incrementCounter();
                    table.addValue("Radius", radius);
                    table.addValue("Mean_Observed", stats.mean);
                    table.addValue("SD_Observed", stats.sd);
                    table.addValue("Mean_Minus_SD", stats.mean - stats.sd);
                    table.addValue("Mean_Plus_SD", stats.mean + stats.sd);
                    table.addValue("Group_N", stats.count);
                    table.addValue("Aggregation_Status", "OK");
                    addIdentity(table, entry.getKey());
                    String[] units = unitsFromAggregateKey(entry.getKey());
                    table.addValue("Radius_Unit", units[0]);
                    table.addValue("Value_Unit", units[1]);
                }
                result.put(aggregateResultKey(entry.getKey()), table);
            }
            return result;
        }

        private void addIdentity(ResultsTable table, String key) {
            String[] identity = identities.get(key);
            table.addValue("Source_Channel", identity[0]);
            table.addValue("Target_Channel", identity[1]);
            table.addValue("Function", identity[2]);
        }

        private static double interpolate(TreeMap<Double, Double> curve,
                                          double radius) {
            Map.Entry<Double, Double> floor = curve.floorEntry(radius);
            Map.Entry<Double, Double> ceiling = curve.ceilingEntry(radius);
            if (floor == null || ceiling == null) return Double.NaN;
            if (floor.getKey().doubleValue() == ceiling.getKey().doubleValue()) {
                return floor.getValue();
            }
            double fraction = (radius - floor.getKey())
                    / (ceiling.getKey() - floor.getKey());
            return floor.getValue()
                    + fraction * (ceiling.getValue() - floor.getValue());
        }
    }

    private static final class EcdfAccumulator {
        private final Map<String, List<double[]>> samples =
                new LinkedHashMap<String, List<double[]>>();
        private final Map<String, String[]> identities =
                new LinkedHashMap<String, String[]>();

        private void add(Map<String, ResultsTable> tables) {
            for (Map.Entry<String, ResultsTable> entry : tables.entrySet()) {
                if (entry.getValue().size() == 0) continue;
                double[] values = new double[entry.getValue().size()];
                for (int row = 0; row < values.length; row++) {
                    values[row] = entry.getValue().getValue("Value", row);
                }
                Arrays.sort(values);
                String unit = entry.getValue().getStringValue("Unit", 0);
                String source = entry.getValue().getStringValue(
                        "Source_Channel", 0);
                String target = entry.getValue().getStringValue(
                        "Target_Channel", 0);
                String mode = entry.getValue().getStringValue("Mode", 0);
                String rank = Integer.toString((int) entry.getValue()
                        .getValue("Rank", 0));
                String aggregateKey = aggregateKey(
                        distributionDisplayLabel(
                                source, target, mode, rank),
                        canonicalIdentity(
                                "ECDF",
                                source, target, mode, rank),
                        unit,
                        "ECDF");
                identities.put(aggregateKey,
                        new String[]{source, target, mode, rank});
                List<double[]> groups = samples.get(aggregateKey);
                if (groups == null) {
                    groups = new ArrayList<double[]>();
                    samples.put(aggregateKey, groups);
                }
                groups.add(values);
            }
        }

        private Map<String, ResultsTable> tables() {
            Map<String, ResultsTable> result =
                    new LinkedHashMap<String, ResultsTable>();
            for (Map.Entry<String, List<double[]>> entry : samples.entrySet()) {
                List<double[]> groups = entry.getValue();
                double minimum = Double.POSITIVE_INFINITY;
                double maximum = Double.NEGATIVE_INFINITY;
                for (double[] group : groups) {
                    if (group.length == 0) continue;
                    minimum = Math.min(minimum, group[0]);
                    maximum = Math.max(maximum, group[group.length - 1]);
                }
                ResultsTable table = new ResultsTable();
                if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
                    result.put(aggregateResultKey(entry.getKey()), table);
                    continue;
                }
                int gridCount = minimum == maximum ? 1 : 101;
                for (int grid = 0; grid < gridCount; grid++) {
                    double value = gridCount == 1
                            ? minimum
                            : minimum + (maximum - minimum) * grid / (gridCount - 1);
                    List<Double> cdfs = new ArrayList<Double>();
                    for (double[] group : groups) {
                        if (group.length > 0) {
                            cdfs.add(ecdf(group, value));
                        }
                    }
                    Stats stats = Stats.of(cdfs);
                    table.incrementCounter();
                    table.addValue("Value", value);
                    table.addValue("Mean_ECDF", stats.mean);
                    table.addValue("SD_ECDF", stats.sd);
                    table.addValue("Mean_Minus_SD",
                            Math.max(0.0, stats.mean - stats.sd));
                    table.addValue("Mean_Plus_SD",
                            Math.min(1.0, stats.mean + stats.sd));
                    table.addValue("Group_N", stats.count);
                    addIdentity(table, entry.getKey());
                    String[] units = unitsFromAggregateKey(entry.getKey());
                    table.addValue("Value_Unit", units[0]);
                    table.addValue("ECDF_Unit", "dimensionless");
                }
                result.put(aggregateResultKey(entry.getKey()), table);
            }
            return result;
        }

        private void addIdentity(ResultsTable table, String key) {
            String[] identity = identities.get(key);
            table.addValue("Source_Channel", identity[0]);
            table.addValue("Target_Channel", identity[1]);
            table.addValue("Mode", identity[2]);
            table.addValue("Rank", Integer.parseInt(identity[3]));
        }

        private static double ecdf(double[] sorted, double value) {
            int low = 0;
            int high = sorted.length;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (sorted[middle] <= value) low = middle + 1;
                else high = middle;
            }
            return low / (double) sorted.length;
        }
    }

    private static final class Stats {
        private final int count;
        private final double mean;
        private final double sd;

        private Stats(int count, double mean, double sd) {
            this.count = count;
            this.mean = mean;
            this.sd = sd;
        }

        private static Stats of(List<Double> values) {
            if (values.isEmpty()) return new Stats(0, Double.NaN, Double.NaN);
            double sum = 0.0;
            for (double value : values) sum += value;
            double mean = sum / values.size();
            double sumSquares = 0.0;
            for (double value : values) {
                double difference = value - mean;
                sumSquares += difference * difference;
            }
            double sd = values.size() < 2
                    ? Double.NaN
                    : Math.sqrt(sumSquares / (values.size() - 1));
            return new Stats(values.size(), mean, sd);
        }
    }
}
