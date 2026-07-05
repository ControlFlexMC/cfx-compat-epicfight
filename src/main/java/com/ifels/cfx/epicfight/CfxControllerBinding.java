package com.ifels.cfx.epicfight;

import com.ifels.controlflex.api.ControlFlexApi;
import com.ifels.controlflex.api.IActionStateProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import yesman.epicfight.api.client.input.controller.ControllerBinding;

public class CfxControllerBinding implements ControllerBinding {
    private final ResourceLocation id;
    private final String cfxActionId;
    private final boolean isGuiAction;

    private boolean activeNow = false;
    private boolean activePreviously = false;
    private int lastTickCount = -1;

    public CfxControllerBinding(ResourceLocation id, String cfxActionId, boolean isGuiAction) {
        this.id = id;
        this.cfxActionId = cfxActionId;
        this.isGuiAction = isGuiAction;
    }

    @Override
    public @NotNull ResourceLocation id() {
        return id;
    }

    @Override
    public boolean isDigitalActiveNow() {
        refreshIfNewTick();
        return activeNow;
    }

    @Override
    public boolean wasDigitalActivePreviously() {
        refreshIfNewTick();
        return activePreviously;
    }

    @Override
    public boolean isDigitalJustPressed() {
        return isDigitalActiveNow() && !wasDigitalActivePreviously();
    }

    @Override
    public boolean isDigitalJustReleased() {
        return !isDigitalActiveNow() && wasDigitalActivePreviously();
    }

    @Override
    public float getAnalogueNow() {
        return isDigitalActiveNow() ? 1.0f : 0.0f;
    }

    @Override
    public void emulatePress() {
    }

    @Override
    public @NotNull Object physicalInputId() {
        return cfxActionId;
    }

    private void refreshIfNewTick() {
        int currentTick = CfxEpicFightMod.getClientTickCounter();
        if (currentTick != lastTickCount) {
            activePreviously = activeNow;
            activeNow = queryTracker();
            lastTickCount = currentTick;
        }
    }

    private boolean queryTracker() {
        if (!ControlFlexApi.isAvailable()) {
            return false;
        }
        IActionStateProvider actions = ControlFlexApi.getActionStateProvider();
        if (actions == null) {
            return false;
        }
        return isGuiAction
                ? actions.isGuiActionActive(cfxActionId)
                : actions.isGameActionActive(cfxActionId);
    }
}
