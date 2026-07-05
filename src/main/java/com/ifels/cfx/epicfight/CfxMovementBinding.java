package com.ifels.cfx.epicfight;

import com.ifels.controlflex.api.ControlFlexApi;
import com.ifels.controlflex.api.IControllerState;
import com.ifels.controlflex.api.IInputProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import yesman.epicfight.api.client.input.controller.ControllerBinding;

public class CfxMovementBinding implements ControllerBinding {
    public enum Direction {
        FORWARD, BACKWARD, LEFT, RIGHT
    }

    private static final float STICK_THRESHOLD = 0.3f;

    private final ResourceLocation id;
    private final Direction direction;

    private boolean activeNow = false;
    private boolean activePreviously = false;
    private int lastTickCount = -1;

    public CfxMovementBinding(ResourceLocation id, Direction direction) {
        this.id = id;
        this.direction = direction;
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
        if (!ControlFlexApi.isAvailable()) {
            return 0.0f;
        }
        IInputProvider input = ControlFlexApi.getInputProvider();
        if (input == null || !input.isConnected()) {
            return 0.0f;
        }
        IControllerState state = input.getControllerState();
        if (state == null) {
            return 0.0f;
        }

        return switch (direction) {
            case FORWARD -> Math.max(0, -state.getLeftStickY());
            case BACKWARD -> Math.max(0, state.getLeftStickY());
            case LEFT -> Math.max(0, -state.getLeftStickX());
            case RIGHT -> Math.max(0, state.getLeftStickX());
        };
    }

    @Override
    public void emulatePress() {
    }

    @Override
    public @NotNull Object physicalInputId() {
        return "stick_" + direction.name().toLowerCase();
    }

    private void refreshIfNewTick() {
        int currentTick = CfxEpicFightMod.getClientTickCounter();
        if (currentTick != lastTickCount) {
            activePreviously = activeNow;
            activeNow = getAnalogueNow() > STICK_THRESHOLD;
            lastTickCount = currentTick;
        }
    }
}
