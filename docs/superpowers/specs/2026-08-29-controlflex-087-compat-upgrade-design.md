# ControlFlex 0.8.7 API and compat config upgrade

Date: 2026-08-29
Status: approved (two review rounds applied 2026-08-29)
Repo: cfx-compat-epicfight

## 1. Goal

Upgrade this bridge mod to ControlFlex API 0.8.7, ship the new-schema Epic Fight compat file, and on every launch keep the plugin-owned `mods/` copy aligned with the bundled resource. Leftover `epicfight_keys.json` in `mods/` is deleted. User edits belong in `compat/user/`; this mod never writes or deletes that layer.

## 2. Confirmed decisions

| Topic | Decision |
|---|---|
| API dependency | JitPack `compileOnly 'com.github.ControlFlexMC:control-flex-api:0.8.7'`. Repositories: `mavenLocal()` then `maven { url 'https://jitpack.io' }` (local publish works before the GitHub tag exists) |
| Runtime ControlFlex | `mods.toml` `versionRange="[0.8.7,)"` |
| API version check | `requireApiVersion("0.8.7")`. This compares `ControlFlexApi.getApiVersion()`, not the Forge mod version. ControlFlex ≥ 0.8.7 must report API `0.8.7` (the 1.20.1 tree still hardcodes `0.8.6` in `ControlFlexApiAdapter` — that is a ControlFlex-side fix) |
| Compat resource | Replace `epicfight_keys.json` with `/Users/ifels/tmp/epicfight.json` as `compat/epicfight.json` (copy as-is) |
| `mods/` overwrite | Always match bundled bytes. SHA-1(local) ≠ SHA-1(bundled) → call `installer.install`. Hand-edits in `mods/` are not preserved |
| User customization | Copy the file into `compat/user/` yourself. This mod does not touch `user/` |
| Leftover old name | After the write decision, delete only `modsDir/epicfight_keys.json` (not recursive, not `user/`) |
| Hash | SHA-1 of raw file bytes, 40-character lowercase hex, leading zeros preserved (`HexFormat` or equivalent) |
| Write path | ControlFlex `ICompatAssetInstaller.install(...)` |
| Delete path | This mod. A delete API may be added to the installer later; swap only the delete call |
| Game directory | Prefer `Minecraft.getInstance().gameDirectory`. If the instance or `gameDirectory` is null, fall back to Forge `FMLPaths.GAMEDIR.get()` so leftover delete still runs. If both fail: warn, skip hash and delete, still call `installer.install` |
| This mod version | `mod_version=0.8.7` |
| Code comments | English only in every new or edited Java file in this work. Do not rewrite comments in untouched files |
| Out of scope | Mixins, state bridge, guide JSON content, action bindings, ControlFlex API changes |

## 2.1 Comment language

All code comments written for this upgrade are English: class/method Javadoc, inline comments, and test comments. Log strings in touched plugin/helper code stay English. Existing Chinese in README_ZH and `docs/zh/` stays Chinese.

## 3. Architecture

Split two units. The helper never calls `install` and never imports Minecraft.

```
CfxEpicFightPlugin.onInstallCompatConfigs(installer)
  1. Resolve modsDir:
       Minecraft.getInstance().gameDirectory / config/controlflex/compat/mods
       else FMLPaths.GAMEDIR / config/controlflex/compat/mods
  2. Read classpath bytes for COMPAT_RESOURCE (plugin Class.getResourceAsStream)
  3. If bytes are null: warn, do not call install
     Else if modsDir is null: installer.install(...)
     Else if CompatConfigInstaller.needsOverwrite(bytes, modsDir/epicfight.json):
          installer.install(COMPAT_RESOURCE, "epicfight.json")
          if install returns false: warn
  4. If modsDir != null: CompatConfigInstaller.deleteLegacyFile(modsDir)
  5. Never open compat/user/

CfxEpicFightPlugin.onInstallGuideAssets(installer)
  unchanged: installer.install(guide resource, "epicfight_guid.json")
```

`ICompatAssetInstaller` in API 0.8.7 exposes only `boolean install(String resourcePath, String fileName)`. It does not return the target directory and has no delete method. ControlFlex writes to `config/controlflex/compat/mods/` from `discoverPlugins()` during `onKeyMappingsReady`.

`CompatConfigInstaller` is a Minecraft-free package-private/public helper:

| Method | Role |
|---|---|
| `sha1Hex(byte[] data)` | 40-char lowercase hex, or `null` if SHA-1 is unavailable |
| `needsOverwrite(byte[] bundled, Path dest)` | `false` if `bundled == null`; `true` if dest missing, unreadable, hash unavailable, or hash mismatch |
| `deleteLegacyFile(Path modsDir)` | `Files.deleteIfExists(modsDir.resolve("epicfight_keys.json"))`; swallow I/O errors and return `false` on failure, `true` if deleted or already absent |

Relative mods path constant: `config/controlflex/compat/mods`.

ControlFlex `MigrationGate` may already rename `epicfight_keys.json` → `epicfight.json` during `initializeClient()`, before this callback. Hash mismatch then overwrites with the bundled file; leftover delete is a no-op. That is expected.

When ControlFlex later adds installer `delete()`, replace only `deleteLegacyFile`'s body (or the plugin call). SHA-1 gate and `install()` stay.

## 4. Install flow

Resource path: `/assets/cfx_compat_epicfight/compat/epicfight.json`  
Target name: `epicfight.json`  
Legacy name: `epicfight_keys.json`

### New file

1. Plugin reads bundled classpath bytes.
2. `needsOverwrite`: SHA-1 bundled vs dest file (`sha1Hex("abc".getBytes(US_ASCII))` = `a9993e364706816aba3e25717850c26c9cd0d89d`).
3. Dest missing → overwrite. Dest hash equal → skip `install`. Dest hash different or unreadable → overwrite.
4. Overwrite → `installer.install(COMPAT_RESOURCE, "epicfight.json")`.

The SHA-1 gate decides whether to call `install`. ControlFlex still performs the copy.

### Legacy file

After the write decision, delete `modsDir/epicfight_keys.json` only. Do not hash it, back it up, or move it to `user/`.

### Logging

- Bundled resource missing: warn.
- `install` returns false: warn.
- Legacy delete I/O failure: warn.
- Game directory unresolved: warn.
- Successful overwrite and successful leftover delete: info (skip-install is silent so every launch is not noisy).

### Failures

| Condition | Behavior |
|---|---|
| Bundled resource missing | Warn, do not call `install`, still try to delete the legacy file if `modsDir` is known |
| `bundled == null` passed to `needsOverwrite` | Return `false` (do not install) |
| SHA-1 algorithm unavailable | `sha1Hex` returns null; `needsOverwrite` returns `true` |
| Cannot read disk file | `needsOverwrite` returns `true` |
| `installer.install` returns false | Warn, still delete the legacy file |
| Legacy delete fails | Warn, do not throw, do not abort startup |
| Minecraft instance/`gameDirectory` null | Fall back to `FMLPaths.GAMEDIR` |
| Both game-dir sources fail | Warn, skip hash and delete, still call `installer.install` if bundled bytes exist |

## 5. Build and metadata

- `build.gradle` `repositories`: `mavenLocal()`; `maven { url 'https://jitpack.io' }` (keep `mavenCentral()` and CurseMaven).
- `dependencies`: replace `compileOnly fileTree(dir: 'libs', ...)` with `compileOnly 'com.github.ControlFlexMC:control-flex-api:0.8.7'`.
- `testImplementation` JUnit Jupiter 5.10.x; `tasks.test { useJUnitPlatform() }`. Tests must not be bundled into the mod jar.
- Delete tracked `libs/controlflex-api-0.8.5.jar`. Keep `libs/.gitkeep`.
- `gradle.properties`: `mod_version=0.8.7`.
- `mods.toml`: ControlFlex `versionRange="[0.8.7,)"`.
- Plugin constants: `COMPAT_FILE_NAME = "epicfight.json"`; `requireApiVersion("0.8.7")`.

## 6. Bundled JSON

Copy `/Users/ifels/tmp/epicfight.json` to `src/main/resources/assets/cfx_compat_epicfight/compat/epicfight.json` without schema edits. Delete `epicfight_keys.json`.

The new file uses ControlFlex 0.8.7 compat shape (`dependencies`, `config_version`, `mod_versions`, `inGameKeys`, `screenKeys`). This spec does not redefine that schema.

## 7. Testing

`CompatConfigInstaller` is Minecraft-free. Tests use a temp `modsDir`. Do not mock `ICompatAssetInstaller` in helper tests; the plugin's `install` call is not unit-tested (would need Minecraft).

1. `sha1Hex` of US-ASCII `"abc"` equals `a9993e364706816aba3e25717850c26c9cd0d89d` (length 40).
2. Dest missing → `needsOverwrite` true.
3. Dest bytes identical to bundled → `needsOverwrite` false.
4. Dest bytes differ → `needsOverwrite` true.
5. `bundled == null` → `needsOverwrite` false.
6. Unreadable dest (optional: dest is a directory) → `needsOverwrite` true.
7. `deleteLegacyFile`: keys file present → removed; return true.
8. `deleteLegacyFile`: keys file absent → no-op, no exception, return true.
9. Sibling `user/epicfight_keys.json` (helper is given only `modsDir`) is unchanged.

No Minecraft tests for Gradle or `mods.toml`.

## 8. Docs

Update version floor (ControlFlex ≥ 0.8.7), compat filename (`epicfight.json`), and deploy path `config/controlflex/compat/mods/` (replace leftover `cfx-mod` wording) in:

- `README.md`, `README_ZH.md`, `docs/DESCRIPTION.md`
- `docs/en/design.md`, `docs/zh/design.md`
- `docs/en/implementation.md`, `docs/zh/implementation.md`

Document the `mods/` SHA-1 align + leftover delete, and that `user/` is not modified.

## 9. Non-goals

- Adding `getTargetDir()` or `delete()` to `ICompatAssetInstaller` (future API work).
- Migrating or overwriting `compat/user/`.
- Rewriting Mixins, `EpicFightStateBridge`, guide assets, or key-binding Java, including translating comments in those untouched files.
- Changing the contents of `/Users/ifels/tmp/epicfight.json` beyond copying it into the jar.
- Unit-testing `CfxEpicFightPlugin` (Minecraft).

## 10. Spec review (2026-08-29)

### Round 1

- Helper vs plugin mixed: tests spoke of an "install callback" while the helper was also described as calling `install`. Fixed: helper is hash + delete only; plugin reads classpath and calls `install`.
- `needsOverwrite(null)` was unspecified vs "missing resource → do not install". Fixed: null bundled → false.
- SHA-1 hex padding unspecified. Fixed: 40-char lowercase.
- Minecraft null left leftover `epicfight_keys.json` beside a newly installed `epicfight.json`. Fixed: `FMLPaths.GAMEDIR` fallback.
- JitPack-only would fail until tag `0.8.7` exists. Fixed: `mavenLocal()` first.
- JUnit without `useJUnitPlatform()` would not run. Fixed in §5.
- `requireApiVersion("0.8.7")` is not the Forge mod version. Documented ControlFlex `getApiVersion()` coupling.

### Round 2

- ControlFlex `MigrationGate` may already rename the legacy file before this plugin runs. Documented as expected.
- Delete must be a single filename in `modsDir`, not a tree walk. Documented.
- Skip-install logging every launch would be noisy. Info only on overwrite and leftover delete.
- Helper tests must not require API/Minecraft mocks. Documented.
- README still says `compat/cfx-mod/`; docs update now includes `mods/` correction.
- `deleteLegacyFile` I/O: return boolean, never throw.
