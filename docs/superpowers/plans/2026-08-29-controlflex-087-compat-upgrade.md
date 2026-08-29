# ControlFlex 0.8.7 Compat Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Switch compile/runtime ControlFlex deps to 0.8.7, ship `epicfight.json`, and SHA-1-align `compat/mods/` while deleting leftover `epicfight_keys.json`.

**Architecture:** `CompatConfigInstaller` is a Minecraft-free helper (`sha1Hex`, `needsOverwrite`, `deleteLegacyFile`). `CfxEpicFightPlugin` reads the bundled resource, resolves `config/controlflex/compat/mods` from `Minecraft.gameDirectory` (fallback `FMLPaths.GAMEDIR`), calls `installer.install` only when overwrite is needed, then deletes the legacy keys file. `user/` is never touched.

**Tech Stack:** Java 17, Gradle (NeoForge legacyforge), JUnit 5, ControlFlex API 0.8.7 via JitPack/`mavenLocal`.

**Spec:** `docs/superpowers/specs/2026-08-29-controlflex-087-compat-upgrade-design.md`

---

## File map

| File | Responsibility |
|---|---|
| `src/main/java/com/ifels/cfx/epicfight/CompatConfigInstaller.java` | SHA-1 gate + legacy delete |
| `src/test/java/com/ifels/cfx/epicfight/CompatConfigInstallerTest.java` | Minecraft-free unit tests |
| `src/main/java/com/ifels/cfx/epicfight/CfxEpicFightPlugin.java` | Orchestrate install + resolve modsDir |
| `src/main/resources/assets/cfx_compat_epicfight/compat/epicfight.json` | Bundled 0.8.7 compat JSON |
| `src/main/resources/assets/cfx_compat_epicfight/compat/epicfight_keys.json` | Delete |
| `build.gradle` | JitPack, JUnit, drop libs fileTree |
| `gradle.properties` | `mod_version=0.8.7` |
| `src/main/resources/META-INF/mods.toml` | ControlFlex `[0.8.7,)` |
| `libs/controlflex-api-0.8.5.jar` | Delete |
| README / docs | Version, filename, `mods/` path |

---

### Task 1: JUnit 5 on Gradle

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Add test dependency and JUnit Platform**

In `build.gradle` `repositories`, keep existing entries. In `dependencies`, after the annotationProcessor line, add:

```gradle
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
```

After `tasks.withType(JavaCompile).configureEach { ... }`, add:

```gradle
tasks.named('test', Test).configure {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Commit**

```bash
git add build.gradle
git commit -m "build: add JUnit 5 for compat installer tests"
```

---

### Task 2: SHA-1 helper (TDD)

**Files:**
- Create: `src/test/java/com/ifels/cfx/epicfight/CompatConfigInstallerTest.java`
- Create: `src/main/java/com/ifels/cfx/epicfight/CompatConfigInstaller.java`

- [ ] **Step 1: Write the failing SHA-1 test**

Create `src/test/java/com/ifels/cfx/epicfight/CompatConfigInstallerTest.java`:

```java
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.ifels.cfx.epicfight.CompatConfigInstallerTest.sha1Hex_abc_matchesFipsVector`

Expected: FAIL (class `CompatConfigInstaller` does not exist).

- [ ] **Step 3: Write minimal `sha1Hex`**

Create `src/main/java/com/ifels/cfx/epicfight/CompatConfigInstaller.java`:

```java
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

    public static boolean needsOverwrite(byte[] bundled, Path dest) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static boolean deleteLegacyFile(Path modsDir) {
        throw new UnsupportedOperationException("not implemented");
    }
}
```

Leave `needsOverwrite` / `deleteLegacyFile` throwing so later tests fail for the right reason.

- [ ] **Step 4: Run SHA-1 test to verify it passes**

Run: `./gradlew test --tests com.ifels.cfx.epicfight.CompatConfigInstallerTest.sha1Hex_abc_matchesFipsVector`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ifels/cfx/epicfight/CompatConfigInstaller.java \
        src/test/java/com/ifels/cfx/epicfight/CompatConfigInstallerTest.java
git commit -m "feat: add SHA-1 helper for compat config align"
```

---

### Task 3: `needsOverwrite` (TDD)

**Files:**
- Modify: `src/test/java/com/ifels/cfx/epicfight/CompatConfigInstallerTest.java`
- Modify: `src/main/java/com/ifels/cfx/epicfight/CompatConfigInstaller.java`

- [ ] **Step 1: Add failing overwrite tests**

Append to `CompatConfigInstallerTest`:

```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests com.ifels.cfx.epicfight.CompatConfigInstallerTest`

Expected: FAIL with `UnsupportedOperationException` on the new tests. SHA-1 test still passes.

- [ ] **Step 3: Implement `needsOverwrite`**

Replace `needsOverwrite` in `CompatConfigInstaller.java`:

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests com.ifels.cfx.epicfight.CompatConfigInstallerTest`

Expected: all tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ifels/cfx/epicfight/CompatConfigInstaller.java \
        src/test/java/com/ifels/cfx/epicfight/CompatConfigInstallerTest.java
git commit -m "feat: SHA-1 overwrite gate for bundled compat JSON"
```

---

### Task 4: `deleteLegacyFile` (TDD)

**Files:**
- Modify: `src/test/java/com/ifels/cfx/epicfight/CompatConfigInstallerTest.java`
- Modify: `src/main/java/com/ifels/cfx/epicfight/CompatConfigInstaller.java`

- [ ] **Step 1: Add failing delete tests**

```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests com.ifels.cfx.epicfight.CompatConfigInstallerTest.deleteLegacyFile*`

Expected: FAIL with `UnsupportedOperationException`

- [ ] **Step 3: Implement `deleteLegacyFile`**

```java
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
```

- [ ] **Step 4: Run full helper tests**

Run: `./gradlew test --tests com.ifels.cfx.epicfight.CompatConfigInstallerTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/ifels/cfx/epicfight/CompatConfigInstaller.java \
        src/test/java/com/ifels/cfx/epicfight/CompatConfigInstallerTest.java
git commit -m "feat: delete leftover epicfight_keys.json in mods/"
```

---

### Task 5: Maven API 0.8.7, version, mods.toml

**Files:**
- Modify: `build.gradle`
- Modify: `gradle.properties`
- Modify: `src/main/resources/META-INF/mods.toml`
- Delete: `libs/controlflex-api-0.8.5.jar`

- [ ] **Step 1: Repositories and compileOnly**

In `build.gradle` `repositories`:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
    maven { url 'https://jitpack.io' }
    maven { url 'https://cursemaven.com' }
}
```

Replace the controlflex-api `fileTree` dependency and its comment with:

```gradle
    compileOnly 'com.github.ControlFlexMC:control-flex-api:0.8.7'
```

- [ ] **Step 2: Version and mods.toml**

`gradle.properties`: set `mod_version=0.8.7`.

`mods.toml` ControlFlex block: `versionRange="[0.8.7,)"`.

- [ ] **Step 3: Remove local API jar**

```bash
git rm libs/controlflex-api-0.8.5.jar
```

Keep `libs/.gitkeep`.

- [ ] **Step 4: Compile against JitPack/mavenLocal**

Run: `./gradlew compileJava test`

Expected: compile succeeds (API 0.8.7 resolved); tests PASS.

If resolve fails: `control-flex-api` `./gradlew publishToMavenLocal` with `api_version=0.8.7`, then retry. Do not copy jars back into `libs/`.

- [ ] **Step 5: Commit**

```bash
git add build.gradle gradle.properties src/main/resources/META-INF/mods.toml
git add -u libs/controlflex-api-0.8.5.jar
git commit -m "build: depend on ControlFlex API 0.8.7 via JitPack"
```

---

### Task 6: Bundled `epicfight.json` and plugin wiring

**Files:**
- Create: `src/main/resources/assets/cfx_compat_epicfight/compat/epicfight.json` (copy `/Users/ifels/tmp/epicfight.json` byte-for-byte)
- Delete: `src/main/resources/assets/cfx_compat_epicfight/compat/epicfight_keys.json`
- Modify: `src/main/java/com/ifels/cfx/epicfight/CfxEpicFightPlugin.java`

- [ ] **Step 1: Replace the resource file**

```bash
cp /Users/ifels/tmp/epicfight.json \
  src/main/resources/assets/cfx_compat_epicfight/compat/epicfight.json
git rm src/main/resources/assets/cfx_compat_epicfight/compat/epicfight_keys.json
```

- [ ] **Step 2: Wire `CfxEpicFightPlugin`**

Replace resource constants and `onInstallCompatConfigs` / `requireApiVersion`. Full class:

```java
package com.ifels.cfx.epicfight;

import com.ifels.controlflex.api.ControlFlexApi;
import com.ifels.controlflex.api.ICompatAssetInstaller;
import com.ifels.controlflex.api.IControlFlexPlugin;
import com.ifels.controlflex.api.IPlayerStateRegistry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * SPI plugin: registers EpicFight player state with ControlFlex when API is ready.
 */
public class CfxEpicFightPlugin implements IControlFlexPlugin {

    private static final Logger LOGGER = LogManager.getLogger("cfx-compat-epicfight");
    private static final String GUIDE_RESOURCE = "/assets/cfx_compat_epicfight/guides/epicfight_guid.json";
    private static final String GUIDE_FILE_NAME = "epicfight_guid.json";
    private static final String COMPAT_RESOURCE = "/assets/cfx_compat_epicfight/compat/epicfight.json";
    private static final String MIN_API_VERSION = "0.8.7";

    private static CfxEpicFightPlugin instance;

    private final EpicFightStateBridge stateBridge = new EpicFightStateBridge();

    public static CfxEpicFightPlugin getInstance() {
        return instance;
    }

    @Override
    public String getModId() {
        return CfxEpicFightMod.MOD_ID;
    }

    @Override
    public void onInstallCompatConfigs(ICompatAssetInstaller installer) {
        Path modsDir = resolveModsDir();
        byte[] bundled = readClasspathResource(COMPAT_RESOURCE);

        if (bundled == null) {
            LOGGER.warn("[cfx-compat-epicfight] Bundled compat resource missing: {}", COMPAT_RESOURCE);
        } else if (modsDir == null) {
            LOGGER.warn("[cfx-compat-epicfight] Game directory unavailable; installing compat without SHA-1 gate");
            if (!installer.install(COMPAT_RESOURCE, CompatConfigInstaller.COMPAT_FILE_NAME)) {
                LOGGER.warn("[cfx-compat-epicfight] Failed to install {}", CompatConfigInstaller.COMPAT_FILE_NAME);
            }
        } else if (CompatConfigInstaller.needsOverwrite(bundled, modsDir.resolve(CompatConfigInstaller.COMPAT_FILE_NAME))) {
            if (installer.install(COMPAT_RESOURCE, CompatConfigInstaller.COMPAT_FILE_NAME)) {
                LOGGER.info("[cfx-compat-epicfight] Installed {} (SHA-1 differed or file missing)",
                        CompatConfigInstaller.COMPAT_FILE_NAME);
            } else {
                LOGGER.warn("[cfx-compat-epicfight] Failed to install {}", CompatConfigInstaller.COMPAT_FILE_NAME);
            }
        }

        if (modsDir != null) {
            Path legacy = modsDir.resolve(CompatConfigInstaller.LEGACY_FILE_NAME);
            boolean existed = java.nio.file.Files.isRegularFile(legacy);
            if (!CompatConfigInstaller.deleteLegacyFile(modsDir)) {
                LOGGER.warn("[cfx-compat-epicfight] Failed to delete leftover {}",
                        CompatConfigInstaller.LEGACY_FILE_NAME);
            } else if (existed) {
                LOGGER.info("[cfx-compat-epicfight] Deleted leftover {}",
                        CompatConfigInstaller.LEGACY_FILE_NAME);
            }
        }
    }

    @Override
    public void onInstallGuideAssets(ICompatAssetInstaller installer) {
        installer.install(GUIDE_RESOURCE, GUIDE_FILE_NAME);
    }

    @Override
    public void onControlFlexReady() {
        instance = this;

        if (!requireApiVersion(MIN_API_VERSION)) {
            LOGGER.warn("[cfx-compat-epicfight] ControlFlex API {}+ required, found: {} — state bridge disabled",
                    MIN_API_VERSION, ControlFlexApi.getApiVersion());
            return;
        }
        if (!ControlFlexApi.isAvailable()) {
            LOGGER.warn("[cfx-compat-epicfight] ControlFlex API not available — state bridge disabled");
            return;
        }

        stateBridge.initialize();

        IPlayerStateRegistry registry = ControlFlexApi.getPlayerStateRegistry();
        if (registry != null) {
            boolean inBattleMode = registry.getState(EpicFightStateBridge.STATE_KEY);
            LOGGER.info("[cfx-compat-epicfight] EpicFight state bridge ready: battle_mode={}", inBattleMode);
        }
    }

    public EpicFightStateBridge getStateBridge() {
        return stateBridge;
    }

    private static Path resolveModsDir() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gameDirectory != null) {
            return mc.gameDirectory.toPath().resolve(CompatConfigInstaller.MODS_RELATIVE);
        }
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            if (gameDir != null) {
                return gameDir.resolve(CompatConfigInstaller.MODS_RELATIVE);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("[cfx-compat-epicfight] FMLPaths.GAMEDIR unavailable: {}", e.getMessage());
        }
        return null;
    }

    private static byte[] readClasspathResource(String resourcePath) {
        try (InputStream in = CfxEpicFightPlugin.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            LOGGER.warn("[cfx-compat-epicfight] Failed to read {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }
}
```

All new comments and logs are English.

- [ ] **Step 3: Compile and test**

Run: `./gradlew compileJava test`

Expected: PASS. `epicfight_keys.json` gone from resources; `epicfight.json` present.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ifels/cfx/epicfight/CfxEpicFightPlugin.java \
        src/main/resources/assets/cfx_compat_epicfight/compat/epicfight.json
git add -u src/main/resources/assets/cfx_compat_epicfight/compat/epicfight_keys.json
git commit -m "feat: ship epicfight.json and SHA-1-align mods/ on install"
```

---

### Task 7: Docs

**Files:**
- Modify: `README.md`, `README_ZH.md`, `docs/DESCRIPTION.md`
- Modify: `docs/en/design.md`, `docs/zh/design.md`
- Modify: `docs/en/implementation.md`, `docs/zh/implementation.md`

- [ ] **Step 1: Version, filename, path**

Replace across these files:

- ControlFlex ≥ `0.8.5` → `0.8.7` (requirements tables and plugin version-check sentences).
- `epicfight_keys.json` → `epicfight.json` where it names the shipped compat file.
- `config/controlflex/compat/cfx-mod/` → `config/controlflex/compat/mods/`
- `config/controlflex/guides/cfx-mod/` → `config/controlflex/guides/mods/`

In `docs/en/design.md` plugin bullet, add: mods/ is SHA-1-aligned with the bundled file; leftover `epicfight_keys.json` in mods/ is deleted; `compat/user/` is not modified.

Same meaning in `docs/zh/design.md` (Chinese body, as existing docs language).

In `docs/en/implementation.md` and `docs/zh/implementation.md` Compat JSON section: describe `inGameKeys` / `screenKeys` / `channels: ["keyMapping"]` instead of `skipForgeKeys` / `guiKeys` as the shipped format. Keep the behavioral intent (combat keys via keyMapping only, tooltip as screen key, install tip for the bridge mod).

- [ ] **Step 2: Commit**

```bash
git add README.md README_ZH.md docs/DESCRIPTION.md \
        docs/en/design.md docs/zh/design.md \
        docs/en/implementation.md docs/zh/implementation.md
git commit -m "docs: ControlFlex 0.8.7 floor and epicfight.json deploy path"
```

---

### Task 8: Final verification

- [ ] **Step 1: Test + compile**

Run: `./gradlew test compileJava`

Expected: BUILD SUCCESSFUL, all `CompatConfigInstallerTest` tests pass.

- [ ] **Step 2: Confirm artifacts**

- `build.gradle` has JitPack + `control-flex-api:0.8.7`, no `fileTree` API jar.
- `libs/` has no `controlflex-api-*.jar`.
- Resource is `compat/epicfight.json` only.
- `mods.toml` is `[0.8.7,)`.
- New Java comments are English.

No extra commit unless verification required a fix; if a fix was needed, commit that fix with a message that states why.
