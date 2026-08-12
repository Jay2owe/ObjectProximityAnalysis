/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.equivalence;

import ij.measure.ResultsTable;
import opa.OPAResult;

import java.util.Map;

/**
 * Canonical text rendering of a complete {@link OPAResult}.
 *
 * <p>Numeric cells are written as the raw IEEE-754 bit pattern, not as decimal
 * text. The gate is bit identity, and a decimal rendering hides the last bits
 * and cannot distinguish the several NaN payloads a table may carry. The
 * formatted string form is written alongside it, because that is what a user
 * actually sees in the Results window.</p>
 */
final class GoldenDump {

    private GoldenDump() {
    }

    static String of(OPAResult result) {
        StringBuilder text = new StringBuilder();
        text.append("STATUS OK\n");
        text.append("channels=").append(result.getChannels().size()).append('\n');
        text.append("directions=")
                .append(result.getDirectionResults().size()).append('\n');
        text.append("patterns=")
                .append(result.getPatternResults().size()).append('\n');
        text.append("physicalCalibration=")
                .append(result.hasPhysicalCalibration()).append('\n');

        table(text, "provenance", result.getProvenanceTable());
        table(text, "distanceSummary", result.getDistanceSummaryTable());
        table(text, "patternSummary", result.getPatternSummaryTable());
        tables(text, "centroids", result.getCentroidTables());
        tables(text, "perObject", result.getPerObjectTables());
        tables(text, "histograms", result.getHistogramTables());
        tables(text, "ecdfs", result.getEcdfTables());
        tables(text, "curves", result.getCurveTables());
        return text.toString();
    }

    static String ofRejection(Throwable failure) {
        StringBuilder text = new StringBuilder();
        text.append("STATUS REJECTED\n");
        text.append("type=").append(failure.getClass().getName()).append('\n');
        text.append("message=").append(escape(failure.getMessage())).append('\n');
        return text.toString();
    }

    private static void tables(StringBuilder text,
                               String group,
                               Map<String, ResultsTable> tables) {
        text.append("GROUP ").append(group)
                .append(" count=").append(tables.size()).append('\n');
        for (Map.Entry<String, ResultsTable> entry : tables.entrySet()) {
            table(text, group + "/" + entry.getKey(), entry.getValue());
        }
    }

    private static void table(StringBuilder text, String key, ResultsTable table) {
        int lastColumn = table.getLastColumn();
        StringBuilder headings = new StringBuilder();
        int columnCount = 0;
        for (int column = 0; column <= lastColumn; column++) {
            if (!table.columnExists(column)) continue;
            if (columnCount > 0) headings.append('|');
            headings.append(escape(table.getColumnHeading(column)));
            columnCount++;
        }
        text.append("TABLE ").append(escape(key))
                .append(" rows=").append(table.size())
                .append(" cols=").append(columnCount).append('\n');
        text.append("H ").append(headings).append('\n');
        for (int row = 0; row < table.size(); row++) {
            StringBuilder bits = new StringBuilder();
            StringBuilder strings = new StringBuilder();
            boolean first = true;
            for (int column = 0; column <= lastColumn; column++) {
                if (!table.columnExists(column)) continue;
                if (!first) {
                    bits.append(',');
                    strings.append('|');
                }
                first = false;
                bits.append(hex(table.getValueAsDouble(column, row)));
                strings.append(escape(table.getStringValue(column, row)));
            }
            text.append("D ").append(bits).append('\n');
            text.append("S ").append(strings).append('\n');
        }
    }

    static String hex(double value) {
        String text = Long.toHexString(Double.doubleToRawLongBits(value));
        StringBuilder padded = new StringBuilder();
        for (int i = text.length(); i < 16; i++) padded.append('0');
        return padded.append(text).toString();
    }

    static String escape(String value) {
        if (value == null) return "<null>";
        StringBuilder text = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\\') {
                text.append("\\\\");
            } else if (character == '|') {
                text.append("\\p");
            } else if (character == ',') {
                text.append("\\c");
            } else if (character == '\n') {
                text.append("\\n");
            } else if (character == '\r') {
                text.append("\\r");
            } else {
                text.append(character);
            }
        }
        return text.toString();
    }
}
