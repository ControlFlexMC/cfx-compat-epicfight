package com.ifels.cfx.epicfight;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatConfigInstallerTest {

    private static final byte[] ABC = "abc".getBytes(StandardCharsets.US_ASCII);
    private static final String ABC_SHA1 = "a9993e364706816aba3e25717850c26c9cd0d89d";

    @TempDir
    Path modsDir;

    @Test
    void sha1Hex_abc_matchesFipsVector() {
        String hex = CompatConfigInstaller.sha1Hex(ABC);
        assertEquals(ABC_SHA1, hex);
        assertEquals(40, hex.length());
    }

    @Test
    void needsOverwrite_missingDest_isTrue() {
        assertTrue(CompatConfigInstaller.needsOverwrite(ABC, modsDir.resolve("epicfight.json")));
    }

    @Test
    void needsOverwrite_identicalBytes_isFalse() throws Exception {
        Path dest = modsDir.resolve("epicfight.json");
        Files.write(dest, ABC);
        assertFalse(CompatConfigInstaller.needsOverwrite(ABC, dest));
    }

    @Test
    void needsOverwrite_differentBytes_isTrue() throws Exception {
        Path dest = modsDir.resolve("epicfight.json");
        Files.write(dest, "xyz".getBytes(StandardCharsets.US_ASCII));
        assertTrue(CompatConfigInstaller.needsOverwrite(ABC, dest));
    }

    @Test
    void needsOverwrite_nullBundled_isFalse() {
        assertFalse(CompatConfigInstaller.needsOverwrite(null, modsDir.resolve("epicfight.json")));
    }

    @Test
    void needsOverwrite_destIsDirectory_isTrue() throws Exception {
        Path dest = modsDir.resolve("epicfight.json");
        Files.createDirectory(dest);
        assertTrue(CompatConfigInstaller.needsOverwrite(ABC, dest));
    }

    @Test
    void deleteLegacyFile_removesKeysJson() throws Exception {
        Path keys = modsDir.resolve("epicfight_keys.json");
        Files.write(keys, ABC);
        assertTrue(CompatConfigInstaller.deleteLegacyFile(modsDir));
        assertFalse(Files.exists(keys));
    }

    @Test
    void deleteLegacyFile_absent_isNoOp() {
        assertTrue(CompatConfigInstaller.deleteLegacyFile(modsDir));
    }

    @Test
    void deleteLegacyFile_doesNotTouchUserDir() throws Exception {
        Path userDir = modsDir.getParent().resolve("user");
        Files.createDirectories(userDir);
        Path userKeys = userDir.resolve("epicfight_keys.json");
        Files.write(userKeys, ABC);
        assertTrue(CompatConfigInstaller.deleteLegacyFile(modsDir));
        assertTrue(Files.exists(userKeys));
    }
}
