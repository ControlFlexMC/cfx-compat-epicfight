package com.ifels.cfx.epicfight;

import com.ifels.controlflex.api.ControlFlexApi;
import com.ifels.controlflex.api.IPlayerStateRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import yesman.epicfight.api.forgeevent.ChangePlayerModeEvent;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/**
 * Detects EpicFight battle/vanilla mode and pushes state to ControlFlex via API.
 */
public class EpicFightStateBridge {

    private static final Logger LOGGER = LogManager.getLogger("cfx-compat-epicfight");

    public static final String STATE_KEY = "epicfight:battle_mode";

    private static final int LOGIN_QUERY_DELAY_TICKS = 20;

    private volatile boolean epicFightMode = false;
    private boolean listenerRegistered = false;
    private int delayedQueryCountdown = -1;

    public void initialize() {
        if (listenerRegistered) {
            return;
        }
        listenerRegistered = true;

        epicFightMode = queryCurrentMode();
        pushState();

        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                ChangePlayerModeEvent.class, this::onPlayerModeChanged);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                ClientPlayerNetworkEvent.LoggingIn.class, this::onClientPlayerLoggingIn);

        LOGGER.info("[EpicFightStateBridge] Initialized, initial epicFightMode={}", epicFightMode);
    }

    public void tick() {
        if (delayedQueryCountdown > 0) {
            delayedQueryCountdown--;
        } else if (delayedQueryCountdown == 0) {
            delayedQueryCountdown = -1;
            boolean oldState = epicFightMode;
            epicFightMode = queryCurrentMode();
            if (oldState != epicFightMode) {
                pushState();
            }
            LOGGER.info("[EpicFightStateBridge] Delayed query result: epicFightMode={}", epicFightMode);
        }
    }

    private void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        delayedQueryCountdown = LOGIN_QUERY_DELAY_TICKS;
        LOGGER.info("[EpicFightStateBridge] Client player logging in, scheduling delayed query ({}t)",
                LOGIN_QUERY_DELAY_TICKS);
    }

    private void onPlayerModeChanged(ChangePlayerModeEvent event) {
        boolean oldState = epicFightMode;

        if (event.isCanceled()) {
            epicFightMode = queryCurrentMode();
        } else {
            epicFightMode = event.getPlayerMode() == PlayerPatch.PlayerMode.EPICFIGHT;
        }

        if (oldState != epicFightMode) {
            pushState();
        }
    }

    private boolean queryCurrentMode() {
        try {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return false;
            }
            LocalPlayerPatch patch = EpicFightCapabilities.getLocalPlayerPatch(player);
            return patch != null && patch.isEpicFightMode();
        } catch (Exception e) {
            LOGGER.warn("[EpicFightStateBridge] Failed to query EpicFight mode: {}", e.getMessage());
            return false;
        }
    }

    private void pushState() {
        IPlayerStateRegistry registry = ControlFlexApi.getPlayerStateRegistry();
        if (registry != null) {
            registry.setState(STATE_KEY, epicFightMode);
        }
    }
}
