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
