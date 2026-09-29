/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import net.imagej.patcher.LegacyEnvironment;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Runs both commands from a macro inside ImageJ as headless Fiji patches it,
 * where GenericDialog is replaced by a text-only version.
 *
 * <p>Regression: that replacement ignores {@code addFileField} and
 * {@code addDirectoryField} but still answers {@code getNextString}, so every
 * later text field read the wrong value and the last one threw. Neither
 * command could run from a headless macro.</p>
 */
public class HeadlessMacroTest {

    @Test(timeout = 120000)
    public void bothCommandsRunFromAHeadlessMacro() throws Exception {
        File work = Files.createTempDirectory("opa-headless").toFile();
        try {
            File batch = new File(work, "batch");
            assertTrue(batch.mkdirs());
            File done = new File(work, "done.txt");
            String dir = work.getAbsolutePath().replace('\\', '/') + "/";

            StringBuilder macro = new StringBuilder();
            macro.append("setBatchMode(true);\n");
            macro.append(labelImage("A", 2, 20));
            macro.append(labelImage("B", 10, 4));
            macro.append("selectImage(\"A\"); saveAs(\"Tiff\", \"" + dir + "batch/s1_A.tif\"); rename(\"A\");\n");
            macro.append("selectImage(\"B\"); saveAs(\"Tiff\", \"" + dir + "batch/s1_B.tif\"); rename(\"B\");\n");
            macro.append("run(\"Object Proximity Analysis\", \"input_mode=[Open label images] "
                    + "channel_count=2 label_image_1=A label_image_2=B run_distances "
                    + "include_self_distances k_nearest_neighbours=1 contact_distance=0 "
                    + "centre_centre histogram_bins=20 auto_save output_directory=["
                    + dir + "single] output_prefix=Headless hide_display\");\n");
            macro.append("run(\"Object Proximity Analysis Batch...\", \"input_folder=["
                    + dir + "batch] filename_regex=(s\\\\d+)_(A|B)\\\\.tif channel_capture_group=2 "
                    + "run_distances include_self_distances k_nearest_neighbours=1 "
                    + "contact_distance=0 centre_centre histogram_bins=20 auto_save "
                    + "hide_display\");\n");
            macro.append("File.saveString(\"done\", \"" + dir + "done.txt\");\n");

            LegacyEnvironment ij = new LegacyEnvironment(dependenciesOnly(), true);
            ij.disableIJ1PluginDirs();
            ij.addPluginClasspath(new File("target/classes"));
            ij.runMacro(macro.toString(), "");

            assertTrue("macro finished", done.isFile());
            assertTrue("single run saved its outputs", new File(work,
                    "single/Object Proximity Analysis/Objects").isDirectory());
            assertTrue("batch saved into the input folder", new File(batch,
                    "Object Proximity Analysis/Folder").isDirectory());
        } finally {
            delete(work);
        }
    }

    private static String labelImage(String title, int x, int y) {
        return "newImage(\"" + title + "\", \"16-bit black\", 32, 32, 1);\n"
                + "run(\"Properties...\", \"unit=micron pixel_width=0.5 "
                + "pixel_height=0.5 voxel_depth=1\");\n"
                + "setColor(1); fillRect(" + x + ", " + y + ", 3, 3);\n"
                + "setColor(2); fillRect(" + (x + 12) + ", " + (y + 6) + ", 3, 3);\n";
    }

    /**
     * The test class path without this project's own classes, so the plugin
     * is loaded by the patched environment and links to its patched ImageJ.
     */
    private static ClassLoader dependenciesOnly() throws Exception {
        List<URL> urls = new ArrayList<URL>();
        String own = new File("target").getCanonicalPath();
        for (String entry : System.getProperty("java.class.path")
                .split(File.pathSeparator)) {
            File file = new File(entry);
            if (file.getCanonicalPath().startsWith(own)) continue;
            urls.add(file.toURI().toURL());
        }
        return new URLClassLoader(urls.toArray(new URL[urls.size()]),
                ClassLoader.getSystemClassLoader().getParent());
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) delete(child);
        }
        file.delete();
    }

    static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8);
    }
}
