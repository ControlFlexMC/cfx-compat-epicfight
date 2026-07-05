package com.ifels.cfx.epicfight.mixin;

import com.ifels.cfx.epicfight.CfxEpicFightControllerMod;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import yesman.epicfight.api.client.input.action.EpicFightInputAction;
import yesman.epicfight.api.client.input.controller.ControllerBinding;
import yesman.epicfight.api.client.input.controller.EpicFightControllerModProvider;
import yesman.epicfight.api.client.input.controller.IEpicFightControllerMod;

import java.util.Optional;

@Mixin(value = EpicFightInputAction.class, remap = false)
public class EpicFightInputActionMixin {

    @Overwrite
    public @NotNull Optional<@NotNull ControllerBinding> controllerBinding() {
        IEpicFightControllerMod mod = EpicFightControllerModProvider.get();
        if (mod == null) {
            throw new IllegalStateException(
                    "controllerBinding() must not be called when the controller mod is not installed"
            );
        }
        EpicFightInputAction self = (EpicFightInputAction) (Object) this;
        return CfxEpicFightControllerMod.getBinding(self);
    }
}
