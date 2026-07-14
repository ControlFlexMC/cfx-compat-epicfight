package com.ifels.cfx.epicfight;

import com.ifels.controlflex.api.ControlFlexApi;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yesman.epicfight.api.client.input.action.EpicFightInputAction;
import yesman.epicfight.api.client.input.controller.EpicFightControllerModProvider;

import java.util.Optional;

@Mod(CfxEpicFightMod.MOD_ID)
public class CfxEpicFightMod {
    public static final String MOD_ID = "cfx_compat_epicfight";
    private static final Logger LOGGER = LogManager.getLogger("cfx-compat-epicfight");

    private static int clientTickCounter = 0;

    public static int getClientTickCounter() {
        return clientTickCounter;
    }

    public CfxEpicFightMod() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // registerPlugin can be called from anywhere —
            // discoverPlugins() now waits for FMLLoadCompleteEvent
            ControlFlexApi.registerPlugin(new CfxEpicFightPlugin());

            CfxEpicFightControllerMod impl = new CfxEpicFightControllerMod();
            EpicFightControllerModProvider.set(MOD_ID, impl);
            LOGGER.info("Registered ControlFlex as EpicFight controller mod");

            verifyMixinApplied();
        });
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance() != null) {
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
