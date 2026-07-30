/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import opa.spatial.PatternFunction;
import opa.spatial.RectangularWindow;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OPAOutputTest {

    @Test
    public void writesTheDocumentedOutputTree() throws Exception {
        ImagePlus image = new ImagePlus(
                "labels", new ByteProcessor(6, 6));
        image.getProcessor().set(2, 2, 1);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .build());
        File parent = Files.createTempDirectory("opa-output").toFile();
        try {
            File root = OPAOutput.save(result, parent, "sample");
            for (String child : new String[]{
                    "Objects", "Distributions", "Curves", "Folder"}) {
                File directory = new File(root, child);
                assertTrue(directory.isDirectory());
                assertTrue(new File(directory, "README.txt").isFile());
            }
            File[] summaries = new File(root, "Objects").listFiles(
                    (directory, name) ->
                            name.endsWith("__Distance_Summary.csv"));
            assertTrue(summaries != null);
            assertEquals(1, summaries.length);
        } finally {
            delete(parent);
        }
    }

    @Test
    public void duplicateChannelTitlesRemainDistinctInSavedOutputs()
            throws Exception {
        ImagePlus first = new ImagePlus(
                "Labels", new ByteProcessor(6, 6));
        ImagePlus second = new ImagePlus(
                "Labels", new ByteProcessor(6, 6));
        first.getProcessor().set(2, 2, 1);
        second.getProcessor().set(4, 4, 1);
        OPAResult result = OPA.run(OPAParameters.builder(first, second)
                .channelNames(Arrays.asList("Labels", "Labels"))
                .runPattern(false)
                .distanceModes(EnumSet.of(DistanceMode.CENTRE_TO_CENTRE))
                .build());
        File parent = Files.createTempDirectory(
                "opa-output-duplicate-names").toFile();
        try {
            File root = OPAOutput.save(result, parent, "sample");
            File objects = new File(root, "Objects");
            File[] tables = objects.listFiles((directory, name) ->
                    name.endsWith(".csv")
                            && !name.endsWith("__Distance_Summary.csv")
                            && !name.contains("__Centroids__")
                            && !name.endsWith("__Provenance.csv"));
            assertTrue(tables != null);
            assertEquals(4, tables.length);
            File[] summaries = objects.listFiles((directory, name) ->
                    name.endsWith("__Distance_Summary.csv"));
            assertTrue(summaries != null);
            assertEquals(1, summaries.length);
            String summary = new String(Files.readAllBytes(
                            summaries[0].toPath()),
                    StandardCharsets.UTF_8);
            assertTrue(summary.contains("Labels [channel 2]"));
        } finally {
            delete(parent);
        }
    }

    @Test
    public void patternOnlySaveIncludesFilteredCentroidsAndProvenance()
            throws Exception {
        ImagePlus image = new ImagePlus(
                "pattern-labels", new ByteProcessor(10, 10));
        image.getProcessor().set(2, 2, 1);
        image.getProcessor().set(8, 8, 2);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runDistances(false)
                .patternFunctions(EnumSet.of(PatternFunction.K))
                .observationWindow(
                        new RectangularWindow(0.0, 0.0, 5.0, 5.0))
                .radii(new double[]{1.0})
                .simulations(1)
                .build());

        assertTrue(result.getPerObjectTables().isEmpty());
        assertEquals(1, result.getCentroidTables().size());
        assertEquals(1, result.getCentroidTables()
                .values().iterator().next().size());
        assertEquals(5.0, result.getProvenanceTable()
                .getValue("Window_Max_X", 0), 0.0);
        assertEquals("TRANSLATION", result.getProvenanceTable()
                .getStringValue("Edge_Correction", 0));

        File parent = Files.createTempDirectory(
                "opa-output-pattern-provenance").toFile();
        try {
            File root = OPAOutput.save(result, parent, "pattern");
            File objects = new File(root, "Objects");
            File[] centroids = objects.listFiles((directory, name) ->
                    name.contains("__Centroids__")
                            && name.endsWith(".csv"));
            assertTrue(centroids != null);
            assertEquals(1, centroids.length);
            File[] provenance = objects.listFiles((directory, name) ->
                    name.endsWith("__Provenance.csv"));
            assertTrue(provenance != null);
            assertEquals(1, provenance.length);
        } finally {
            delete(parent);
        }
    }

    @Test
    public void rawPrefixesThatSanitizeTheSameDoNotOverwriteOutputs()
            throws Exception {
        ImagePlus image = new ImagePlus(
                "labels", new ByteProcessor(6, 6));
        image.getProcessor().set(1, 1, 1);
        image.getProcessor().set(4, 4, 2);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .build());
        File parent = Files.createTempDirectory(
                "opa-output-prefix-collision").toFile();
        try {
            File root = OPAOutput.save(result, parent, "sample:a");
            OPAOutput.save(result, parent, "sample?a");
            File objects = new File(root, "Objects");
            File[] summaries = objects.listFiles((directory, name) ->
                    name.endsWith("__Distance_Summary.csv"));
            assertTrue(summaries != null);
            assertEquals(2, summaries.length);
            assertTrue(!summaries[0].getName().equals(
                    summaries[1].getName()));
        } finally {
            delete(parent);
        }
    }

    @Test
    public void caseOnlyPrefixesDoNotOverwriteOnWindows()
            throws Exception {
        ImagePlus image = new ImagePlus(
                "labels", new ByteProcessor(6, 6));
        image.getProcessor().set(1, 1, 1);
        image.getProcessor().set(4, 4, 2);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .build());
        File parent = Files.createTempDirectory(
                "opa-output-case-collision").toFile();
        try {
            File root = OPAOutput.save(result, parent, "Sample");
            OPAOutput.save(result, parent, "sample");
            File[] summaries = new File(root, "Objects").listFiles(
                    (directory, name) ->
                            name.endsWith("__Distance_Summary.csv"));
            assertTrue(summaries != null);
            assertEquals(2, summaries.length);
            assertTrue(!summaries[0].getName().equalsIgnoreCase(
                    summaries[1].getName()));
        } finally {
            delete(parent);
        }
    }

    @Test
    public void savedCsvPreservesFullDoublePrecision() throws Exception {
        ImagePlus image = new ImagePlus(
                "diagonal", new ByteProcessor(5, 5));
        image.getProcessor().set(1, 1, 1);
        image.getProcessor().set(2, 2, 2);
        OPAResult result = OPA.run(OPAParameters.builder(image)
                .runPattern(false)
                .distanceModes(EnumSet.of(
                        DistanceMode.CENTRE_TO_CENTRE))
                .build());
        File parent = Files.createTempDirectory(
                "opa-output-precision").toFile();
        try {
            File root = OPAOutput.save(result, parent, "precision");
            File[] summaries = new File(root, "Objects").listFiles(
                    (directory, name) ->
                            name.endsWith("__Distance_Summary.csv"));
            assertTrue(summaries != null);
            assertEquals(1, summaries.length);
            String csv = new String(
                    Files.readAllBytes(summaries[0].toPath()),
                    StandardCharsets.UTF_8);
            assertTrue(csv.contains(
                    Double.toString(Math.sqrt(2.0))));
        } finally {
            delete(parent);
        }
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) delete(child);
            }
        }
        file.delete();
    }
}
