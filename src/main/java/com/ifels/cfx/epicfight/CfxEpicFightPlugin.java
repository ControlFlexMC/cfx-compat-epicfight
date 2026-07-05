package com.ifels.cfx.epicfight;

import com.ifels.controlflex.api.ControlFlexApi;
import com.ifels.controlflex.api.ICompatAssetInstaller;
import com.ifels.controlflex.api.IControlFlexPlugin;
import com.ifels.controlflex.api.IPlayerStateRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * SPI plugin: registers EpicFight player state with ControlFlex when API is ready.
 */
public class CfxEpicFightPlugin implements IControlFlexPlugin {

    private static final Logger LOGGER = LogManager.getLogger("cfx-compat-epicfight");
    private static final String GUIDE_RESOURCE = "/assets/cfx_compat_epicfight/guides/epicfight_guid.json";
    private static final String GUIDE_FILE_NAME = "epicfight_guid.json";
    private static final String COMPAT_RESOURCE = "/assets/cfx_compat_epicfight/compat/epicfight_keys.json";
    private static final String COMPAT_FILE_NAME = "epicfight_keys.json";

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
        installer.install(COMPAT_RESOURCE, COMPAT_FILE_NAME);
    }

    @Override
    public void onInstallGuideAssets(ICompatAssetInstaller installer) {
        installer.install(GUIDE_RESOURCE, GUIDE_FILE_NAME);
    }

    @Override
    public void onControlFlexReady() {
        instance = this;

        if (!requireApiVersion("0.8.5")) {
            LOGGER.warn("[cfx-compat-epicfight] ControlFlex API 0.8.5+ required, found: {} — state bridge disabled",
                    ControlFlexApi.getApiVersion());
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
}
