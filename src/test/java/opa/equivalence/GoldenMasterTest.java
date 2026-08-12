/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa.equivalence;

import ij.IJ;
import ij.ImagePlus;
import opa.OPA;
import opa.OPAParameters;
import opa.OPAResult;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pre-extraction golden master for the whole public analysis surface.
 *
 * <p>Every corpus case is run under every configuration and the complete
 * result — all eight table families, or the rejection message — is written to
 * {@code golden/pre-extraction/}. On any later run the output is compared
 * byte-for-byte against the recorded golden.</p>
 *
 * <p><strong>The goldens are immutable.</strong> There is deliberately no
 * regeneration switch. A golden is written only when it does not yet exist, so
 * making a diff go away requires deleting a file by hand, which is visible.
 * If a golden is ever found to be wrong that is a bug report against the
 * shipped plugin and is fixed as its own change with its own release note —
 * never by re-recording.</p>
 *
 * <p>Tier contract, declared before the first run: <strong>every</strong>
 * field of every table is Tier 1, bit-identical. Extraction moves code between
 * compilation units; it changes no arithmetic, no traversal order and no
 * tie-break, so there is no field for which a tolerance could be justified.
 * There is no Tier 2 and no Tier 3 in this migration. Any difference at all is
 * a failure to be traced to its cause, not measured.</p>
 */
public class GoldenMasterTest {

    private static final String PARALLELISM_PROPERTY = "opa.parallelism";

    @Test
    public void everyCaseAndConfigurationMatchesThePreExtractionGolden()
            throws IOException {
        File root = goldenRoot();
        List<GoldenCorpus.Case> cases = GoldenCorpus.cases();
        List<GoldenConfigurations.Configuration> configurations =
                GoldenConfigurations.configurations();
        assertTrue("corpus is empty", cases.size() > 0);
        assertTrue("configuration sweep is empty", configurations.size() > 0);

        Set<String> produced = new HashSet<String>();
        List<String> differences = new ArrayList<String>();
        int captured = 0;
        int compared = 0;
        int rejections = 0;

        for (GoldenCorpus.Case corpusCase : cases) {
            for (GoldenConfigurations.Configuration configuration
                    : configurations) {
                String relative = corpusCase.getName() + "/"
                        + configuration.getName() + ".txt";
                produced.add(relative);
                String actual = run(corpusCase, configuration);
                if (actual.startsWith("STATUS REJECTED")) rejections++;

                File golden = new File(root, relative);
                if (!golden.isFile()) {
                    write(golden, actual);
                    captured++;
                    continue;
                }
                compared++;
                String expected = read(golden);
                if (!expected.equals(actual)) {
                    differences.add(relative + "\n"
                            + firstDifference(expected, actual));
                }
            }
        }

        if (!differences.isEmpty()) {
            StringBuilder report = new StringBuilder();
            report.append(differences.size())
                    .append(" golden(s) moved. Extraction is a refactor: ")
                    .append("outputs must not move.\n");
            for (int i = 0; i < differences.size() && i < 10; i++) {
                report.append("---- ").append(differences.get(i)).append('\n');
            }
            fail(report.toString());
        }

        assertEquals(cases.size() * configurations.size(), produced.size());
        assertTrue("no rejection case exercised", rejections > 0);
        assertOrphanFree(root, produced);
        System.out.println("[golden] captured=" + captured
                + " compared=" + compared
                + " rejections=" + rejections
                + " total=" + produced.size());
    }

    private static String run(GoldenCorpus.Case corpusCase,
                              GoldenConfigurations.Configuration configuration) {
        String previous = System.getProperty(PARALLELISM_PROPERTY);
        System.setProperty(PARALLELISM_PROPERTY,
                Integer.toString(configuration.getParallelism()));
        IJ.resetEscape();
        try {
            List<ImagePlus> images = corpusCase.images();
            OPAParameters.Builder builder = OPAParameters.builder(images)
                    .channelNames(corpusCase.getChannelNames());
            configuration.apply(builder, images);
            OPAResult result = OPA.run(builder.build());
            return GoldenDump.of(result);
        } catch (RuntimeException rejected) {
            return GoldenDump.ofRejection(rejected);
        } finally {
            if (previous == null) {
                System.clearProperty(PARALLELISM_PROPERTY);
            } else {
                System.setProperty(PARALLELISM_PROPERTY, previous);
            }
        }
    }

    private static void assertOrphanFree(File root, Set<String> produced) {
        List<String> orphans = new ArrayList<String>();
        collect(root, "", produced, orphans);
        if (!orphans.isEmpty()) {
            fail("golden files exist that this run no longer produces, so a "
                    + "case or configuration was dropped: " + orphans);
        }
    }

    private static void collect(File directory,
                                String prefix,
                                Set<String> produced,
                                List<String> orphans) {
        File[] entries = directory.listFiles();
        if (entries == null) return;
        Arrays.sort(entries);
        for (File entry : entries) {
            String relative = prefix + entry.getName();
            if (entry.isDirectory()) {
                collect(entry, relative + "/", produced, orphans);
            } else if (relative.endsWith(".txt")
                    && !relative.startsWith("_")
                    && !produced.contains(relative)) {
                // Names starting with "_" belong to the sibling engine-contract
                // golden, which this sweep does not produce.
                orphans.add(relative);
            }
        }
    }

    private static String firstDifference(String expected, String actual) {
        String[] expectedLines = expected.split("\n", -1);
        String[] actualLines = actual.split("\n", -1);
        int limit = Math.min(expectedLines.length, actualLines.length);
        for (int i = 0; i < limit; i++) {
            if (!expectedLines[i].equals(actualLines[i])) {
                return "line " + (i + 1)
                        + "\n  golden: " + truncate(expectedLines[i])
                        + "\n  actual: " + truncate(actualLines[i]);
            }
        }
        return "length " + expectedLines.length + " -> " + actualLines.length;
    }

    private static String truncate(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    static File goldenRoot() {
        String basedir = System.getProperty("basedir");
        File project = new File(basedir == null
                ? System.getProperty("user.dir")
                : basedir);
        return new File(new File(project, "golden"), "pre-extraction");
    }

    static void write(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    static String read(File file) throws IOException {
        return new String(
                Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
