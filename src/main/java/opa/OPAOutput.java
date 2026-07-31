/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.measure.ResultsTable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit file-output facade. The core {@link OPA#run(OPAParameters)} method
 * never calls this class.
 */
public final class OPAOutput {

    private OPAOutput() {
    }

    public static File save(OPAResult result, File parent, String prefix)
            throws IOException {
        if (result == null) {
            throw new IllegalArgumentException("OPA result must not be null.");
        }
        if (parent == null) {
            throw new IllegalArgumentException("Output parent folder must not be null.");
        }
        String safePrefix = prefixIdentity(
                prefix == null ? "Analysis" : prefix,
                result);
        File root = new File(parent, "Object Proximity Analysis");
        File objects = directory(root, "Objects");
        File distributions = directory(root, "Distributions");
        File curves = directory(root, "Curves");
        File folder = directory(root, "Folder");
        Map<File, ResultsTable> tables =
                new LinkedHashMap<File, ResultsTable>();

        for (Map.Entry<String, ResultsTable> entry
                : result.getCentroidTables().entrySet()) {
            tables.put(new File(
                    objects, csvName(
                            safePrefix + "__" + safe(entry.getKey()))),
                    entry.getValue());
        }
        tables.put(new File(
                        objects, csvName(safePrefix + "__Provenance")),
                result.getProvenanceTable());
        for (Map.Entry<String, ResultsTable> entry
                : result.getPerObjectTables().entrySet()) {
            tables.put(new File(
                    objects, csvName(
                            safePrefix + "__" + safe(entry.getKey()))),
                    entry.getValue());
        }
        tables.put(new File(
                        objects, csvName(safePrefix + "__Distance_Summary")),
                result.getDistanceSummaryTable());

        for (Map.Entry<String, ResultsTable> entry
                : result.getHistogramTables().entrySet()) {
            tables.put(new File(
                    distributions,
                    csvName(safePrefix + "__" + safe(entry.getKey())
                            + "__Histogram")),
                    entry.getValue());
        }
        for (Map.Entry<String, ResultsTable> entry
                : result.getEcdfTables().entrySet()) {
            tables.put(new File(
                    distributions,
                    csvName(safePrefix + "__" + safe(entry.getKey())
                            + "__ECDF")),
                    entry.getValue());
        }

        for (Map.Entry<String, ResultsTable> entry
                : result.getCurveTables().entrySet()) {
            tables.put(new File(
                    curves, csvName(
                            safePrefix + "__" + safe(entry.getKey()))),
                    entry.getValue());
        }
        tables.put(new File(
                        curves, csvName(safePrefix + "__Pattern_Summary")),
                result.getPatternSummaryTable());
        saveTablesAtomically(tables);

        writeReadme(objects,
                "Filtered centroid inputs, analysis provenance, per-object proximity "
                        + "tables and nearest-neighbour summary statistics.");
        writeReadme(distributions,
                "Nearest-neighbour histograms and empirical cumulative distributions.");
        writeReadme(curves,
                "Observed point-pattern curves, complete-spatial-randomness expectations, "
                        + "Monte Carlo envelopes, seeds and global p-values.");
        writeReadme(folder,
                "Folder-batch summaries and mean curves are stored in "
                        + "identity-named Batch__ subfolders.");
        return root;
    }

    private static File directory(File parent, String name) throws IOException {
        File directory = new File(parent, name);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create output folder: "
                    + directory.getAbsolutePath());
        }
        return directory;
    }

    static void saveTable(ResultsTable table, File file)
            throws IOException {
        Map<File, ResultsTable> tables =
                new LinkedHashMap<File, ResultsTable>();
        tables.put(file, table);
        saveTablesAtomically(tables);
    }

    static void saveTablesAtomically(Map<File, ResultsTable> tables)
            throws IOException {
        if (tables == null) {
            throw new IllegalArgumentException("Output tables must not be null.");
        }
        List<StagedFile> staged = new ArrayList<StagedFile>();
        try {
            for (Map.Entry<File, ResultsTable> entry : tables.entrySet()) {
                if (entry.getValue() == null) continue;
                File target = entry.getKey();
                validateTarget(target);
                File temporary = File.createTempFile(
                        "opa-", ".tmp", target.getParentFile());
                staged.add(new StagedFile(temporary, target));
                writeTable(entry.getValue(), temporary);
            }
            for (StagedFile file : staged) {
                replaceAtomically(file.temporary, file.target);
            }
        } finally {
            for (StagedFile file : staged) {
                Files.deleteIfExists(file.temporary.toPath());
            }
        }
    }

    private static void writeTable(ResultsTable table, File file)
            throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8));
        try {
            String[] headings = table.getHeadings();
            writeCsvRow(writer, headings);
            for (int row = 0; row < table.size(); row++) {
                String[] values = new String[headings.length];
                for (int column = 0; column < headings.length; column++) {
                    double number = table.getValue(headings[column], row);
                    values[column] = Double.isNaN(number)
                            ? table.getStringValue(headings[column], row)
                            : Double.toString(number);
                }
                writeCsvRow(writer, values);
            }
        } finally {
            writer.close();
        }
    }

    private static void writeCsvRow(Writer writer, String[] values)
            throws IOException {
        if (values.length == 0) return;
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(',');
            writer.write(csvValue(values[i]));
        }
        writer.write(System.lineSeparator());
    }

    private static String csvValue(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') < 0
                && text.indexOf('"') < 0
                && text.indexOf('\r') < 0
                && text.indexOf('\n') < 0) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static void writeReadme(File directory, String text)
            throws IOException {
        writeTextAtomically(
                new File(directory, "README.txt"),
                text + System.lineSeparator());
    }

    static void writeTextAtomically(File file, String text)
            throws IOException {
        validateTarget(file);
        File temporary = File.createTempFile(
                "opa-", ".tmp", file.getParentFile());
        try {
            Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(temporary), StandardCharsets.UTF_8));
            try {
                writer.write(text == null ? "" : text);
            } finally {
                writer.close();
            }
            replaceAtomically(temporary, file);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    private static void validateTarget(File file) throws IOException {
        if (file == null || file.getParentFile() == null
                || !file.getParentFile().isDirectory()) {
            throw new IOException("Output file must have an existing parent folder.");
        }
        if (file.exists() && !file.isFile()) {
            throw new IOException(
                    "Output path is not a file: " + file.getAbsolutePath());
        }
    }

    private static void replaceAtomically(File temporary, File target)
            throws IOException {
        try {
            Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException(
                    "Atomic output replacement is unavailable for "
                            + target.getAbsolutePath()
                            + "; existing output was left unchanged.",
                    exception);
        }
    }

    private static final class StagedFile {
        private final File temporary;
        private final File target;

        private StagedFile(File temporary, File target) {
            this.temporary = temporary;
            this.target = target;
        }
    }

    private static String safe(String value) {
        String clean = value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return clean.isEmpty() ? "Analysis" : clean;
    }

    private static String prefixIdentity(String rawPrefix,
                                         OPAResult result) {
        String clean = safe(rawPrefix);
        String readable = clean.length() > 80
                ? clean.substring(0, 80)
                : clean;
        return readable + "__" + sha256(
                rawPrefix + "\u0000" + outputShapeIdentity(result));
    }

    private static String outputShapeIdentity(OPAResult result) {
        StringBuilder identity = new StringBuilder();
        appendKeys(identity, "CENTROIDS", result.getCentroidTables());
        appendKeys(identity, "OBJECTS", result.getPerObjectTables());
        appendKeys(identity, "HISTOGRAMS", result.getHistogramTables());
        appendKeys(identity, "ECDFS", result.getEcdfTables());
        appendKeys(identity, "CURVES", result.getCurveTables());
        identity.append("DISTANCE_SUMMARY_ROWS:")
                .append(result.getDistanceSummaryTable().size())
                .append('\u0000');
        identity.append("PATTERN_SUMMARY_ROWS:")
                .append(result.getPatternSummaryTable().size())
                .append('\u0000');
        return identity.toString();
    }

    private static void appendKeys(StringBuilder identity,
                                   String kind,
                                   Map<String, ResultsTable> tables) {
        identity.append(kind).append(':');
        for (String key : tables.keySet()) {
            identity.append(key.length()).append(':').append(key);
        }
        identity.append('\u0000');
    }

    private static String csvName(String identity) {
        String clean = safe(identity);
        if (clean.length() <= 180) return clean + ".csv";
        return clean.substring(0, 100) + "__" + sha256(identity) + ".csv";
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
}
