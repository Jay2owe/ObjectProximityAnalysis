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
import java.nio.file.Files;
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
        } finally {
            jar.close();
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
