package com.ifels.cfx.epicfight;

import com.ifels.controlflex.api.ControlFlexApi;
import com.ifels.controlflex.api.IActionStateProvider;
import com.ifels.controlflex.api.IControllerState;
import com.ifels.controlflex.api.IInputProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;
import yesman.epicfight.api.client.input.InputMode;
import yesman.epicfight.api.client.input.PlayerInputState;
import yesman.epicfight.api.client.input.action.EpicFightInputAction;
import yesman.epicfight.api.client.input.action.MinecraftInputAction;
import yesman.epicfight.api.client.input.controller.ControllerBinding;
import yesman.epicfight.api.client.input.controller.IEpicFightControllerMod;

import java.util.Optional;

public class CfxEpicFightControllerMod implements IEpicFightControllerMod {

    private static final float STICK_THRESHOLD = 0.3f;
    private static final ActionBindingMapper BINDING_MAPPER = new ActionBindingMapper();

    @Override
    public String getModName() {
        return "ControlFlex";
    }

    // ControlFlex action IDs for EpicFight's attack / weapon-innate-skill keys.
    // These MUST match the IDs registered in ActionBindingMapper.
    private static final String CFX_ACTION_ATTACK = "epicfight:key.epicfight.attack";
    private static final String CFX_ACTION_WEAPON_INNATE = "epicfight:key.epicfight.weapon_innate_skill";

    @Override
    public @NotNull InputMode getInputMode() {
        // 1. No controller connected: keyboard/mouse only.
        if (!ControlFlexApi.isAvailable() || !ControlFlexApi.isControllerConnected()) {
            return InputMode.KEYBOARD_MOUSE;
        }
        // 2. The controller is pressing attack or weapon-innate-skill right now: switch
        //    to CONTROLLER so EpicFight's InputManager.isBoundToSamePhysicalInput()
        //    compares our per-action physicalInputId() (unique) instead of the shared
        //    left-mouse KeyMapping, letting the two fire independently on different
        //    controller buttons.
        if (isControllerAttackOrInnateActive()) {
            return InputMode.CONTROLLER;
        }
        // 3. Otherwise MIXED: keyboard/mouse and controller both remain usable.
        return InputMode.MIXED;
    }

    /** True when ControlFlex reports the controller is currently driving EpicFight's
     *  attack or weapon-innate-skill action. */
    private static boolean isControllerAttackOrInnateActive() {
        IActionStateProvider actions = ControlFlexApi.getActionStateProvider();
        if (actions == null) {
            return false;
        }
        return actions.isGameActionActive(CFX_ACTION_ATTACK)
                || actions.isGameActionActive(CFX_ACTION_WEAPON_INNATE);
    }

    @Override
    public @NotNull PlayerInputState getInputState() {
        if (!ControlFlexApi.isAvailable()) {
            return fallbackVanillaInput();
        }

        IInputProvider input = ControlFlexApi.getInputProvider();
        if (input == null || !input.isConnected()) {
            return fallbackVanillaInput();
        }

        IControllerState state = input.getControllerState();
        if (state == null) {
            return fallbackVanillaInput();
        }

        float forwardImpulse = -state.getLeftStickY();
        float leftImpulse = -state.getLeftStickX();

        IActionStateProvider actions = ControlFlexApi.getActionStateProvider();
        if (actions == null) {
            return fallbackVanillaInput();
        }

        return new PlayerInputState(
                leftImpulse,
                forwardImpulse,
                forwardImpulse > STICK_THRESHOLD,
                forwardImpulse < -STICK_THRESHOLD,
                leftImpulse > STICK_THRESHOLD,
                leftImpulse < -STICK_THRESHOLD,
                actions.isGameActionActive("key.jump"),
                actions.isGameActionActive("key.sneak")
        );
    }

    private static @NotNull PlayerInputState fallbackVanillaInput() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return new PlayerInputState(0, 0, false, false, false, false, false, false);
        }
        return PlayerInputState.fromVanillaInput(player.input);
    }

    public static Optional<ControllerBinding> getBinding(@NotNull EpicFightInputAction action) {
        return BINDING_MAPPER.getBinding(action);
    }

    public static Optional<ControllerBinding> getBinding(@NotNull MinecraftInputAction action) {
        return BINDING_MAPPER.getBinding(action);
    }
}
