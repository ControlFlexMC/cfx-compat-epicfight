package com.ifels.cfx.epicfight;

import yesman.epicfight.api.client.camera.EpicFightCameraAPI;

/**
 * Forwards ControlFlex turn input from the non-"simulate mouse" channels to the
 * Epic Fight camera.
 *
 * <p>Matches the semantics of Epic Fight's own {@code MixinMouseHandler}: when
 * the EF camera takes over ({@code camera_mode=ALWAYS} / TPS battle camera /
 * lock-on), {@link EpicFightCameraAPI#turnCamera(double, double)} returns
 * {@code true}, meaning this frame's turn has been consumed by the EF camera --
 * the caller (CF bridge) should skip writing the player's yaw/pitch.</p>
 *
 * <p>The deltas passed by CF's direct-turn / smooth-direct-turn channels are
 * calibrated "turn-before", the same calibration the vanilla mouse pipeline
 * hands to EF, so forwarding them directly rotates at the same speed as
 * "simulate mouse"; CF's EMA / aim-down-sights damping does not apply while EF
 * owns the camera (the view is decided by the EF camera, equivalent to
 * SIMULATE).</p>
 */
public final class EpicFightCameraBridge {

    private EpicFightCameraBridge() {
    }

    /**
     * @return true = the Epic Fight camera has consumed this frame's turn, CF
     *         should not write the player's yaw/pitch; false = EF did not take
     *         over (or the query failed), the caller keeps its original behavior.
     */
    public static boolean tryConsumeCameraTurn(double yaw, double pitch) {
        try {
            return EpicFightCameraAPI.getInstance().turnCamera(yaw, pitch);
        } catch (Throwable t) {
            // EF in an abnormal state (e.g. patch not ready): fall back
            // silently and let CF take its original path
            return false;
        }
    }
}
