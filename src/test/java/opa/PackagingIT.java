/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PackagingIT {

    @Test
    public void packagedJarHasExactLicenceAndCurrentCommit()
            throws Exception {
        File project = new File(requiredProperty(
                "opa.project.basedir"));
        File jarPath = new File(requiredProperty("opa.project.jar"));
        assertTrue(jarPath.isFile());

        byte[] expectedLicence = Files.readAllBytes(
                new File(project, "LICENSE").toPath());
        JarFile jar = new JarFile(jarPath);
        try {
            JarEntry licenceEntry = jar.getJarEntry("META-INF/LICENSE");
            assertNotNull(licenceEntry);
            byte[] packagedLicence = read(jar.getInputStream(licenceEntry));
            assertArrayEquals(expectedLicence, packagedLicence);

            Attributes attributes =
                    jar.getManifest().getMainAttributes();
            String implementationBuild = attributes.getValue(
                    "Implementation-Build");
            assertEquals(gitHead(project), implementationBuild);

            // Both cores are present, each under its own relocated root.
            assertNotNull(jar.getJarEntry(
                    "opa/internal/core/io/RegexGroupDiscovery.class"));
            assertNotNull(jar.getJarEntry(
                    "opa/internal/engine/spatial/SpatialStatistics.class"));
            assertNotNull(jar.getJarEntry(
                    "opa/internal/engine/geometry/ProximityEngine.class"));

            // Nothing unrelocated leaks. Two jars carrying the same
            // fully-qualified class name is a silent-wrong-answer bug under
            // Fiji's flat classloader, so this is the assertion that matters.
            assertNoEntriesUnder(jar, "sc/fiji/");

            // ij is what Fiji already is; bundling it would shadow the host.
            assertNoEntriesUnder(jar, "ij/");

            // The plugin's own public API is never relocated: plugins.config
            // and every documented Java caller name these.
            assertNotNull(jar.getJarEntry("opa/OPA.class"));
            assertNotNull(jar.getJarEntry("opa/OPAParameters.class"));
            assertNotNull(jar.getJarEntry("opa/OPAResult.class"));
            assertNotNull(jar.getJarEntry(
                    "opa/Object_Proximity_Analysis.class"));
            assertNotNull(jar.getJarEntry("opa/OPA_Batch.class"));
        } finally {
            jar.close();
        }
    }

    private static void assertNoEntriesUnder(JarFile jar, String prefix) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            assertTrue("packaged jar leaks " + name,
                    !name.startsWith(prefix));
        }
    }

    @Test
    public void packagedJarRunsDiscoveryWithoutAnExternalCoreJar()
            throws Exception {
        File jarPath = new File(requiredProperty("opa.project.jar"));
        File imageJ = new File(ij.IJ.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        File input = Files.createTempDirectory("opa-packaged-discovery").toFile();
        URLClassLoader loader = new URLClassLoader(
                new URL[]{jarPath.toURI().toURL(), imageJ.toURI().toURL()},
                null);
        try {
            assertTrue(new File(input, "sample_A.tif").createNewFile());
            assertTrue(new File(input, "sample_B.tif").createNewFile());
            Class<?> parametersClass = loader.loadClass(
                    "opa.OPABatchParameters");
            Object builder = parametersClass.getMethod(
                            "builder", File.class, String.class, Integer.TYPE)
                    .invoke(null, input, "(sample)_([AB])\\.tif", 2);
            builder.getClass().getMethod("recursive", Boolean.TYPE)
                    .invoke(builder, false);
            builder.getClass().getMethod("autoSave", Boolean.TYPE)
                    .invoke(builder, false);
            Object parameters = builder.getClass().getMethod("build")
                    .invoke(builder);
            Class<?> runnerClass = loader.loadClass("opa.OPABatchRunner");
            String preview = (String) runnerClass.getMethod(
                            "preview", parametersClass)
                    .invoke(null, parameters);

            assertTrue(preview.contains("1 group(s)"));
            assertTrue(preview.contains("[A] sample_A.tif"));
            assertNotNull(loader.loadClass(
                    "opa.internal.core.io.RegexGroupDiscovery"));

            // The engine resolves from this jar alone, under its relocated
            // name, with nothing on the classpath but ij.
            assertNotNull(loader.loadClass(
                    "opa.internal.engine.spatial.SpatialStatistics"));
            assertNotNull(loader.loadClass(
                    "opa.internal.engine.spatial.MonteCarloAnalyzer"));
            assertNotNull(loader.loadClass(
                    "opa.internal.engine.geometry.ProximityEngine"));
            assertNotLoadable(loader, "sc.fiji.opa.core.spatial.SpatialStatistics");
            assertNotLoadable(loader, "sc.fiji.oc3d.core.io.RegexGroupDiscovery");
        } finally {
            loader.close();
            File[] files = input.listFiles();
            if (files != null) {
                for (File file : files) Files.deleteIfExists(file.toPath());
            }
            Files.deleteIfExists(input.toPath());
        }
    }

    private static void assertNotLoadable(URLClassLoader loader, String name) {
        try {
            loader.loadClass(name);
            throw new AssertionError(
                    "packaged jar still exposes the unrelocated " + name);
        } catch (ClassNotFoundException expected) {
            // The relocated copy is the only one, which is the point.
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Required test property is missing: " + name);
        }
        return value;
    }

    private static String gitHead(File project) throws Exception {
        Process process = new ProcessBuilder(
                "git", "rev-parse", "HEAD")
                .directory(project)
                .redirectErrorStream(true)
                .start();
        String output;
        try {
            output = new String(read(process.getInputStream()), "UTF-8").trim();
        } finally {
            process.getInputStream().close();
        }
        assertEquals(0, process.waitFor());
        return output;
    }

    private static byte[] read(InputStream stream) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            stream.close();
        }
    }
}
