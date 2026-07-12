package com.ifels.cfx.epicfight;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yesman.epicfight.api.client.input.action.EpicFightInputAction;
import yesman.epicfight.api.client.input.controller.EpicFightControllerModProvider;

import java.util.Optional;

@Mod(value = CfxEpicFightMod.MOD_ID, dist = Dist.CLIENT)
public class CfxEpicFightMod {
    public static final String MOD_ID = "cfx_compat_epicfight";
    private static final Logger LOGGER = LogManager.getLogger("cfx-compat-epicfight");

    private static int clientTickCounter = 0;

    public static int getClientTickCounter() {
        return clientTickCounter;
    }

    public CfxEpicFightMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CfxEpicFightControllerMod impl = new CfxEpicFightControllerMod();
            EpicFightControllerModProvider.set(MOD_ID, impl);
            LOGGER.info("Registered ControlFlex as EpicFight controller mod");

            verifyMixinApplied();
        });
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance() != null) {
            clientTickCounter++;
            CfxEpicFightPlugin plugin = CfxEpicFightPlugin.getInstance();
            if (plugin != null) {
                plugin.getStateBridge().tick();
            }
        }
    }

    private static void verifyMixinApplied() {
        try {
            Optional<?> result = EpicFightInputAction.ATTACK.controllerBinding();
            LOGGER.info(
                    "Mixin verification passed: controllerBinding() returned {}",
                    result.isPresent() ? "binding" : "empty"
            );
        } catch (Exception e) {
            LOGGER.error("Mixin verification FAILED! controllerBinding() threw: {}", e.getMessage());
        }
    }
}
