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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static final char UNIT_SEPARATOR = '\u001f';

    private OPABatchRunner() {
    }

    public static String preview(OPABatchParameters parameters) {
        Compiled compiled = compile(parameters);
        return preview(scan(parameters, compiled.pattern));
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
            if (group.isRunnable()) validGroups++;
        }
        int processed = 0;
        int skipped = 0;
        int errors = 0;
        List<String> errorMessages = new ArrayList<String>();
        ResultsTable distanceSummary = new ResultsTable();
        ResultsTable patternSummary = new ResultsTable();
        CurveAccumulator curves = new CurveAccumulator();
        EcdfAccumulator ecdfs = new EcdfAccumulator();
        File output = parameters.getOutputDirectory() == null
                ? parameters.getInputFolder()
                : parameters.getOutputDirectory();

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            if (IJ.escapePressed()) {
                IJ.resetEscape();
                skipped += groups.size() - groupIndex;
                break;
            }
            Group group = groups.get(groupIndex);
            if (!group.isRunnable()) {
                skipped++;
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
                OPAParameters analysis = OPAParameters.builderFrom(
                                parameters.getAnalysisTemplate())
                        .images(images)
                        .channelNames(channelNames)
                        .build();
                OPAResult result = OPA.run(analysis);
                appendTable(distanceSummary, result.getDistanceSummaryTable(),
                        group.relativeFolder, group.displayName());
                appendTable(patternSummary, result.getPatternSummaryTable(),
                        group.relativeFolder, group.displayName());
                curves.add(result.getCurveTables());
                ecdfs.add(result.getEcdfTables());
                if (parameters.isAutoSave()) {
                    OPAOutput.save(
                            result, output, group.outputPrefix());
                }
                processed++;
            } catch (Exception exception) {
                errors++;
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
        }

        Map<String, ResultsTable> meanCurves = curves.tables();
        Map<String, ResultsTable> meanEcdfs = ecdfs.tables();
        if (parameters.isAutoSave()) {
            try {
                saveAggregates(
                        output,
                        distanceSummary,
                        patternSummary,
                        meanCurves,
                        meanEcdfs,
                        errorMessages);
            } catch (IOException exception) {
                errors++;
                errorMessages.add("Saving batch aggregates: "
                        + exception.getMessage());
            }
        }
        IJ.showProgress(1.0);
        return new OPABatchResult(
                groups.size(),
                validGroups,
                processed,
                skipped,
                errors,
                output,
                distanceSummary,
                patternSummary,
                meanCurves,
                meanEcdfs,
                errorMessages);
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
            String key = child.getName().substring(0, matcher.start(channelGroup))
                    + "*" + child.getName().substring(matcher.end(channelGroup));
            Group group = local.get(key);
            if (group == null) {
                group = new Group(relative, key);
                local.put(key, group);
            }
            group.files.add(new BatchFile(
                    child, matcher.group(channelGroup)));
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

    private static String preview(List<Group> groups) {
        if (groups.isEmpty()) return "No matching files found.";
        int runnable = 0;
        int fileCount = 0;
        StringBuilder text = new StringBuilder();
        for (Group group : groups) {
            if (group.isRunnable()) runnable++;
            fileCount += group.files.size();
        }
        text.append(groups.size()).append(" group(s), ")
                .append(runnable).append(" runnable, ")
                .append(fileCount).append(" file(s)\n\n");
        for (Group group : groups) {
            text.append(group.relativeFolder.isEmpty()
                            ? "(root)/"
                            : group.relativeFolder + "/")
                    .append(group.key)
                    .append("  (")
                    .append(group.files.size())
                    .append(group.isRunnable() ? "" : " - SKIP")
                    .append(")\n");
            for (BatchFile file : group.files) {
                text.append("  [").append(file.channel).append("] ")
                        .append(file.file.getName()).append("\n");
            }
        }
        return text.toString();
    }

    private static void appendTable(ResultsTable target,
                                    ResultsTable source,
                                    String folder,
                                    String group) {
        if (source == null) return;
        String[] headings = source.getHeadings();
        for (int row = 0; row < source.size(); row++) {
            target.incrementCounter();
            target.addValue("Folder", folder);
            target.addValue("Group", group);
            for (String heading : headings) {
                if (heading == null || heading.trim().isEmpty()) continue;
                String value = source.getStringValue(heading, row);
                if ("Seed".equals(heading)
                        || "Source_Channel".equals(heading)
                        || "Target_Channel".equals(heading)
                        || "Mode".equals(heading)
                        || "Unit".equals(heading)
                        || "Function".equals(heading)
                        || "Radius_Unit".equals(heading)
                        || "Value_Unit".equals(heading)
                        || "Status".equals(heading)) {
                    target.addValue(heading, value);
                    continue;
                }
                try {
                    target.addValue(heading, Double.parseDouble(value));
                } catch (NumberFormatException ignored) {
                    target.addValue(heading, value);
                }
            }
        }
    }

    private static void saveAggregates(
            File parent,
            ResultsTable distanceSummary,
            ResultsTable patternSummary,
            Map<String, ResultsTable> curves,
            Map<String, ResultsTable> ecdfs,
            List<String> errors) throws IOException {
        File folder = new File(
                new File(parent, "Object Proximity Analysis"), "Folder");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException(
                    "Could not create batch folder: " + folder.getAbsolutePath());
        }
        distanceSummary.saveAs(new File(
                folder, "OPA_Batch_Distance_Summary.csv").getAbsolutePath());
        patternSummary.saveAs(new File(
                folder, "OPA_Batch_Pattern_Summary.csv").getAbsolutePath());
        for (Map.Entry<String, ResultsTable> entry : curves.entrySet()) {
            entry.getValue().saveAs(new File(
                    folder,
                    "OPA_Batch_Mean_Curve__" + safe(entry.getKey()) + ".csv")
                    .getAbsolutePath());
        }
        for (Map.Entry<String, ResultsTable> entry : ecdfs.entrySet()) {
            entry.getValue().saveAs(new File(
                    folder,
                    "OPA_Batch_Mean_ECDF__" + safe(entry.getKey()) + ".csv")
                    .getAbsolutePath());
        }
        Writer readme = new FileWriter(new File(folder, "README.txt"));
        try {
            readme.write("Folder-batch scalar summaries, mean point-pattern curves, "
                    + "and mean ECDFs. Spread columns are between-group sample SD.");
            readme.write(System.lineSeparator());
            if (!errors.isEmpty()) {
                readme.write(System.lineSeparator());
                readme.write("Errors:");
                readme.write(System.lineSeparator());
                for (String error : errors) {
                    readme.write("- " + error + System.lineSeparator());
                }
            }
        } finally {
            readme.close();
        }
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String aggregateKey(String base,
                                       String firstUnit,
                                       String secondUnit) {
        return base + UNIT_SEPARATOR + firstUnit
                + UNIT_SEPARATOR + secondUnit;
    }

    private static String[] unitsFromAggregateKey(String key) {
        int last = key.lastIndexOf(UNIT_SEPARATOR);
        int first = last < 0 ? -1 : key.lastIndexOf(UNIT_SEPARATOR, last - 1);
        if (first < 0 || last < 0) return new String[]{"", ""};
        return new String[]{
                key.substring(first + 1, last),
                key.substring(last + 1)
        };
    }

    private static String displayAggregateKey(String key) {
        int first = key.indexOf(UNIT_SEPARATOR);
        String base = first < 0 ? key : key.substring(0, first);
        String[] units = unitsFromAggregateKey(key);
        return base + "__" + units[0] + "__" + units[1];
    }

    private static final class Compiled {
        private final Pattern pattern;

        private Compiled(Pattern pattern) {
            this.pattern = pattern;
        }
    }

    private static final class BatchFile {
        private final File file;
        private final String channel;

        private BatchFile(File file, String channel) {
            this.file = file;
            this.channel = channel;
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

        private boolean isRunnable() {
            if (files.isEmpty() || files.size() > OPAParameters.MAX_IMAGES) return false;
            Set<String> channels = new HashSet<String>();
            for (BatchFile file : files) {
                if (file.channel == null || file.channel.trim().isEmpty()
                        || !channels.add(file.channel)) {
                    return false;
                }
            }
            return true;
        }

        private String displayName() {
            String name = key.replace("*", "")
                    .replaceAll("^[_\\-./\\\\]+|[_\\-./\\\\]+$", "")
                    .replaceAll("\\.[^.]+$", "");
            return name.isEmpty() ? "batch" : name;
        }

        private String outputPrefix() {
            return relativeFolder.isEmpty()
                    ? displayName()
                    : relativeFolder.replace('/', '_') + "__" + displayName();
        }
    }

    private static final class CurveAccumulator {
        private final Map<String, TreeMap<Double, List<Double>>> values =
                new LinkedHashMap<String, TreeMap<Double, List<Double>>>();

        private void add(Map<String, ResultsTable> tables) {
            for (Map.Entry<String, ResultsTable> entry : tables.entrySet()) {
                ResultsTable table = entry.getValue();
                if (table.size() == 0) continue;
                String radiusUnit = table.getStringValue("Radius_Unit", 0);
                String valueUnit = table.getStringValue("Value_Unit", 0);
                String aggregateKey = aggregateKey(
                        entry.getKey(), radiusUnit, valueUnit);
                TreeMap<Double, List<Double>> byRadius = values.get(aggregateKey);
                if (byRadius == null) {
                    byRadius = new TreeMap<Double, List<Double>>();
                    values.put(aggregateKey, byRadius);
                }
                for (int row = 0; row < table.size(); row++) {
                    double radius = table.getValue("Radius", row);
                    double observed = table.getValue("Observed", row);
                    if (!Double.isFinite(radius) || !Double.isFinite(observed)) continue;
                    List<Double> samples = byRadius.get(radius);
                    if (samples == null) {
                        samples = new ArrayList<Double>();
                        byRadius.put(radius, samples);
                    }
                    samples.add(observed);
                }
            }
        }

        private Map<String, ResultsTable> tables() {
            Map<String, ResultsTable> result =
                    new LinkedHashMap<String, ResultsTable>();
            for (Map.Entry<String, TreeMap<Double, List<Double>>> entry
                    : values.entrySet()) {
                ResultsTable table = new ResultsTable();
                for (Map.Entry<Double, List<Double>> radius
                        : entry.getValue().entrySet()) {
                    Stats stats = Stats.of(radius.getValue());
                    table.incrementCounter();
                    table.addValue("Radius", radius.getKey());
                    table.addValue("Mean_Observed", stats.mean);
                    table.addValue("SD_Observed", stats.sd);
                    table.addValue("Mean_Minus_SD", stats.mean - stats.sd);
                    table.addValue("Mean_Plus_SD", stats.mean + stats.sd);
                    table.addValue("Group_N", stats.count);
                    String[] units = unitsFromAggregateKey(entry.getKey());
                    table.addValue("Radius_Unit", units[0]);
                    table.addValue("Value_Unit", units[1]);
                }
                result.put(displayAggregateKey(entry.getKey()), table);
            }
            return result;
        }
    }

    private static final class EcdfAccumulator {
        private final Map<String, List<double[]>> samples =
                new LinkedHashMap<String, List<double[]>>();

        private void add(Map<String, ResultsTable> tables) {
            for (Map.Entry<String, ResultsTable> entry : tables.entrySet()) {
                if (entry.getValue().size() == 0) continue;
                double[] values = new double[entry.getValue().size()];
                for (int row = 0; row < values.length; row++) {
                    values[row] = entry.getValue().getValue("Value", row);
                }
                Arrays.sort(values);
                String unit = entry.getValue().getStringValue("Unit", 0);
                String aggregateKey = aggregateKey(
                        entry.getKey(), unit, "ECDF");
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
                    result.put(displayAggregateKey(entry.getKey()), table);
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
                    String[] units = unitsFromAggregateKey(entry.getKey());
                    table.addValue("Value_Unit", units[0]);
                    table.addValue("ECDF_Unit", "dimensionless");
                }
                result.put(displayAggregateKey(entry.getKey()), table);
            }
            return result;
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
                    ? 0.0
                    : Math.sqrt(sumSquares / (values.size() - 1));
            return new Stats(values.size(), mean, sd);
        }
    }
}
