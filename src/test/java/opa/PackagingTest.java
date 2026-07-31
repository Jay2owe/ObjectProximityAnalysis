/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package opa;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class PackagingTest {

    @Test
    public void binaryResourcesIncludeTheBsdLicence() throws Exception {
        File classes = new File(OPA.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        File file = new File(classes, "META-INF/LICENSE");
        assertTrue(file.isFile());
        String licence = new String(
                Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(licence.contains(
                "Redistributions in binary form must reproduce"));
        assertTrue(licence.contains(
                "THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS"));
    }
}
