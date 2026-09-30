/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.measure.ResultsTable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Readable result-window titles. Result tables are keyed by the identity
 * names their CSV files use, which carry a 64-character hash so no two files
 * can collide. A window title needs to be read, not to be a file name.
 */
final class WindowTitles {

    private static final String HASH = "__[0-9a-f]{64}";

    private WindowTitles() {
    }

    /** "A.tif_to_B.tif__<hash>__Centre-Centre__NN1" becomes "A.tif to B.tif, Centre-Centre, NN1". */
    static String readable(String key) {
        String text = key == null ? "" : key;
        text = text.replaceAll(HASH, "");
        text = text.replace("__Centroids", "");
        text = text.replace("_to_", " to ");
        text = text.replace("__", ", ");
        return text.trim();
    }

    /** The tables keyed by "{@code prefix}readable name", made unique with " (2)", " (3)"... */
    static Map<String, ResultsTable> titled(String prefix,
                                            Map<String, ResultsTable> tables) {
        Map<String, ResultsTable> titled = new LinkedHashMap<String, ResultsTable>();
        Set<String> used = new HashSet<String>();
        for (Map.Entry<String, ResultsTable> entry : tables.entrySet()) {
            String base = prefix + readable(entry.getKey());
            String title = base;
            int suffix = 2;
            while (!used.add(title)) {
                title = base + " (" + suffix++ + ")";
            }
            titled.put(title, entry.getValue());
        }
        return titled;
    }
}
