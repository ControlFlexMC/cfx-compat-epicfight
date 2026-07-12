package com.ifels.cfx.epicfight;

import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.api.client.input.action.EpicFightInputAction;
import yesman.epicfight.api.client.input.action.InputAction;
import yesman.epicfight.api.client.input.action.MinecraftInputAction;
import yesman.epicfight.api.client.input.controller.ControllerBinding;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ActionBindingMapper {
    private final Map<InputAction, ControllerBinding> bindings = new HashMap<>();

    public ActionBindingMapper() {
        registerDigital(EpicFightInputAction.ATTACK, "epicfight:key.epicfight.attack", false);
        registerDigital(EpicFightInputAction.GUARD, "epicfight:key.epicfight.guard", false);
        registerDigital(EpicFightInputAction.DODGE, "epicfight:key.epicfight.dodge", false);
        registerDigital(EpicFightInputAction.LOCK_ON, "epicfight:key.epicfight.lock_on", false);
        registerDigital(EpicFightInputAction.LOCK_ON_SHIFT_LEFT, "epicfight:key.epicfight.lock_on_shift_left", false);
        registerDigital(EpicFightInputAction.LOCK_ON_SHIFT_RIGHT, "epicfight:key.epicfight.lock_on_shift_right", false);
        registerDigital(EpicFightInputAction.LOCK_ON_SHIFT_FREELY, "epicfight:key.epicfight.lock_on_shift_freely", false);
        registerDigital(EpicFightInputAction.SWITCH_MODE, "epicfight:key.epicfight.switch_mode", false);
        registerDigital(EpicFightInputAction.WEAPON_INNATE_SKILL, "epicfight:key.epicfight.weapon_innate_skill", false);
        registerDigital(EpicFightInputAction.MOBILITY, "epicfight:key.epicfight.mover_skill", false);

        registerDigital(EpicFightInputAction.WEAPON_INNATE_SKILL_TOOLTIP, "epicfight:key.epicfight.show_tooltip", true);

        registerDigital(MinecraftInputAction.JUMP, "key.jump", false);
        registerDigital(MinecraftInputAction.ATTACK_DESTROY, "key.attack", false);
        registerDigital(MinecraftInputAction.USE, "key.use", false);
        registerDigital(MinecraftInputAction.SNEAK, "key.sneak", false);
        registerDigital(MinecraftInputAction.SPRINT, "key.sprint", false);
        registerDigital(MinecraftInputAction.SWAP_OFF_HAND, "key.swapOffhand", false);
        registerDigital(MinecraftInputAction.DROP, "key.drop", false);

        registerMovement(MinecraftInputAction.MOVE_FORWARD, CfxMovementBinding.Direction.FORWARD);
        registerMovement(MinecraftInputAction.MOVE_BACKWARD, CfxMovementBinding.Direction.BACKWARD);
        registerMovement(MinecraftInputAction.MOVE_LEFT, CfxMovementBinding.Direction.LEFT);
        registerMovement(MinecraftInputAction.MOVE_RIGHT, CfxMovementBinding.Direction.RIGHT);
    }

    private void registerDigital(InputAction action, String cfxActionId, boolean isGuiAction) {
        String path = cfxActionId.replace(":", "_").replace(".", "_").toLowerCase();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CfxEpicFightMod.MOD_ID, path);
        bindings.put(action, new CfxControllerBinding(id, cfxActionId, isGuiAction));
    }

    private void registerMovement(InputAction action, CfxMovementBinding.Direction dir) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CfxEpicFightMod.MOD_ID, "move_" + dir.name().toLowerCase());
        bindings.put(action, new CfxMovementBinding(id, dir));
    }

    public Optional<ControllerBinding> getBinding(InputAction action) {
        return Optional.ofNullable(bindings.get(action));
    }
}
