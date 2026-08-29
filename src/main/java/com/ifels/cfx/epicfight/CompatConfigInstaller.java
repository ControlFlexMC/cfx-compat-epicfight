package com.ifels.cfx.epicfight;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-1 gate and leftover-file delete for plugin-owned compat JSON in
 * {@code config/controlflex/compat/mods/}. Does not call ControlFlex install
 * and does not touch {@code compat/user/}.
 */
public final class CompatConfigInstaller {

    public static final String COMPAT_FILE_NAME = "epicfight.json";
    public static final String LEGACY_FILE_NAME = "epicfight_keys.json";
    public static final String MODS_RELATIVE = "config/controlflex/compat/mods";

    private CompatConfigInstaller() {}

    /**
     * SHA-1 of {@code data} as 40-char lowercase hex, or {@code null} if SHA-1
     * is unavailable.
     */
    public static String sha1Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * @return {@code false} if {@code bundled} is null (caller must not install);
     *         {@code true} if dest is missing, not a readable file, hash cannot
     *         be computed, or SHA-1 differs from bundled.
     */
    public static boolean needsOverwrite(byte[] bundled, Path dest) {
        if (bundled == null) {
            return false;
        }
        String expected = sha1Hex(bundled);
        if (expected == null) {
            return true;
        }
        if (!Files.isRegularFile(dest)) {
            return true;
        }
        try {
            String actual = sha1Hex(Files.readAllBytes(dest));
            if (actual == null) {
                return true;
            }
            return !expected.equals(actual);
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Deletes {@code epicfight_keys.json} in {@code modsDir} only.
     *
     * @return {@code true} if the file is gone (deleted or was absent);
     *         {@code false} on I/O error
     */
    public static boolean deleteLegacyFile(Path modsDir) {
        Path legacy = modsDir.resolve(LEGACY_FILE_NAME);
        try {
            Files.deleteIfExists(legacy);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
