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
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
                prefix == null ? "Analysis" : prefix);
        File root = new File(parent, "Object Proximity Analysis");
        File objects = directory(root, "Objects");
        File distributions = directory(root, "Distributions");
        File curves = directory(root, "Curves");
        File folder = directory(root, "Folder");

        for (Map.Entry<String, ResultsTable> entry
                : result.getCentroidTables().entrySet()) {
            saveTable(entry.getValue(), new File(
                    objects, csvName(
                            safePrefix + "__" + safe(entry.getKey()))));
        }
        saveTable(result.getProvenanceTable(), new File(
                objects, csvName(safePrefix + "__Provenance")));
        for (Map.Entry<String, ResultsTable> entry
                : result.getPerObjectTables().entrySet()) {
            saveTable(entry.getValue(), new File(
                    objects, csvName(
                            safePrefix + "__" + safe(entry.getKey()))));
        }
        saveTable(result.getDistanceSummaryTable(), new File(
                objects, csvName(safePrefix + "__Distance_Summary")));

        for (Map.Entry<String, ResultsTable> entry
                : result.getHistogramTables().entrySet()) {
            saveTable(entry.getValue(), new File(
                    distributions,
                    csvName(safePrefix + "__" + safe(entry.getKey())
                            + "__Histogram")));
        }
        for (Map.Entry<String, ResultsTable> entry
                : result.getEcdfTables().entrySet()) {
            saveTable(entry.getValue(), new File(
                    distributions,
                    csvName(safePrefix + "__" + safe(entry.getKey())
                            + "__ECDF")));
        }

        for (Map.Entry<String, ResultsTable> entry
                : result.getCurveTables().entrySet()) {
            saveTable(entry.getValue(), new File(
                    curves, csvName(
                            safePrefix + "__" + safe(entry.getKey()))));
        }
        saveTable(result.getPatternSummaryTable(), new File(
                curves, csvName(safePrefix + "__Pattern_Summary")));

        writeReadme(objects,
                "Filtered centroid inputs, analysis provenance, per-object proximity "
                        + "tables and nearest-neighbour summary statistics.");
        writeReadme(distributions,
                "Nearest-neighbour histograms and empirical cumulative distributions.");
        writeReadme(curves,
                "Observed point-pattern curves, complete-spatial-randomness expectations, "
                        + "Monte Carlo envelopes, seeds and global p-values.");
        writeReadme(folder,
                "Reserved for folder-batch aggregate tables and mean curves.");
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
        if (table == null) return;
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
        Writer writer = new FileWriter(new File(directory, "README.txt"));
        try {
            writer.write(text);
            writer.write(System.lineSeparator());
        } finally {
            writer.close();
        }
    }

    private static String safe(String value) {
        String clean = value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return clean.isEmpty() ? "Analysis" : clean;
    }

    private static String prefixIdentity(String rawPrefix) {
        String clean = safe(rawPrefix);
        String readable = clean.length() > 80
                ? clean.substring(0, 80)
                : clean;
        return readable + "__" + sha256(rawPrefix);
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
