/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

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
            assertTrue(new File(
                    new File(root, "Objects"),
                    "sample__Distance_Summary.csv").isFile());
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
