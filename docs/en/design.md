# Design

## Problem

Epic Fight has its own controller abstraction layer (`IEpicFightControllerMod` SPI). At startup it checks for a registered controller mod:

- If one exists → uses `controllerBinding()` and `getInputState()` for all input.
- If none exists → falls back to GLFW keyboard polling, which gamepad bindings can never trigger.

Without a ControlFlex bridge, Epic Fight treats the player as a keyboard-only user even when ControlFlex is installed and a controller is connected.

**Why JSON compat configs alone can't fix this:**

| Problem | Why JSON isn't enough |
|---------|----------------------|
| Epic Fight reads `PlayerInputState` directly | `PlayerInputState` has stick axes, movement impulses, jump/sneak booleans — these don't go through `KeyMapping`. JSON can route key events but can't populate a structured input state. |
| Epic Fight queries `ControllerBinding` per action | Each `InputAction` enum value calls `controllerBinding()` to decide whether it should use controller or keyboard input. JSON has no way to return a `ControllerBinding` object. |
| `controllerBinding()` is hardcoded to Controlify | Epic Fight's source calls `EpicFightControlifyControllerMod.getBinding(this)` directly — there's no registry or service loader for alternative controller mods. |

## Architecture

Three layers, with the bridge in the middle:

```
┌──────────────────────────────────────────────┐
│                  Epic Fight                   │
│                                              │
│  InputManager.checkAction()                  │
│    → controllerMod.getInputMode()            │
│    → action.controllerBinding()  [Mixin]     │
│    → controllerMod.getInputState()           │
└──────────────────┬───────────────────────────┘
                   │ IEpicFightControllerMod
┌──────────────────┴───────────────────────────┐
│            cfx-compat-epicfight               │
│                                              │
│  CfxEpicFightControllerMod                   │
│  ├── getInputMode()    → MIXED / KBM         │
│  ├── getInputState()   → PlayerInputState    │
│  └── getBinding()      → ControllerBinding   │
│                                              │
│  CfxControllerBinding   (digital actions)    │
│  CfxMovementBinding     (analog stick axes)  │
│  ActionBindingMapper    (action→binding map) │
│  EpicFightStateBridge   (battle mode sync)   │
└──────────────────┬───────────────────────────┘
                   │ ControlFlexApi / SPI
┌──────────────────┴───────────────────────────┐
│                ControlFlex                    │
│                                              │
│  IActionStateProvider   (game/GUI actions)   │
│  IInputProvider         (controller state)   │
│  IPlayerStateRegistry   (mod state push)     │
│  IControlFlexPlugin     (lifecycle callbacks)│
└──────────────────────────────────────────────┘
```

## Component Roles

### CfxEpicFightMod — Forge mod entry point

- Registers `CfxEpicFightControllerMod` with Epic Fight's `EpicFightControllerModProvider` during `FMLClientSetupEvent`.
- Maintains a `clientTickCounter` (incremented each `ClientTickEvent`) for bindings to detect tick boundaries without depending on `gameTime`.
- Verifies Mixin application on startup — calls `controllerBinding()` on a known action and logs success/failure.

### CfxEpicFightPlugin — ControlFlex SPI plugin

- Implements `IControlFlexPlugin` for lifecycle integration:
  - `onInstallCompatConfigs` → deploys `epicfight_keys.json` to `config/controlflex/compat/cfx-mod/`.
  - `onInstallGuideAssets` → deploys `epicfight_guid.json` to `config/controlflex/guides/cfx-mod/`.
  - `onControlFlexReady` → initializes the state bridge after API providers are available.
- Version-checks the ControlFlex API at `0.8.5`.

### CfxEpicFightControllerMod — Controller mod implementation

- Implements `IEpicFightControllerMod`:
  - `getInputMode()`: Returns `MIXED` when ControlFlex is available and a controller is connected; `KEYBOARD_MOUSE` otherwise. This single method gates Epic Fight's entire controller path.
  - `getInputState()`: Builds `PlayerInputState` from ControlFlex's `IControllerState` (stick axes) and `IActionStateProvider` (jump/sneak). Falls back to vanilla `PlayerInput` on any null check.
- Exposes static `getBinding()` methods for Mixin callbacks, delegating to `ActionBindingMapper`.

### ActionBindingMapper — Action-to-binding registry

- Maps each Epic Fight `InputAction` enum value to a `ControllerBinding` implementation.
- Three categories:
  - **Digital EpicFight actions** (ATTACK, GUARD, DODGE, etc.) → `CfxControllerBinding` with game-layer action IDs like `"epicfight:key.epicfight.attack"`.
  - **Digital Minecraft actions** (JUMP, SNEAK, ATTACK_DESTROY, etc.) → `CfxControllerBinding` with vanilla key IDs like `"key.jump"`.
  - **Analog stick actions** (MOVE_FORWARD/BACKWARD/LEFT/RIGHT) → `CfxMovementBinding` reading stick axes directly.
- Actions without a binding (OPEN_SKILL_SCREEN, etc.) return `Optional.empty()`, causing Epic Fight to fall back to keyboard checking.

### CfxControllerBinding — Digital action binding

- Wraps ControlFlex's `IActionStateProvider` (game or GUI layer) as an Epic Fight `ControllerBinding`.
- Lazy-tick refresh: on each new client tick, shifts `activeNow → activePreviously`, then queries ControlFlex for current state.
- Implements all `ControllerBinding` edge-detection methods (`isDigitalJustPressed`, `isDigitalJustReleased`, etc.).

### CfxMovementBinding — Analog stick binding

- Reads `IControllerState` stick axes directly (not through action tracker), returning 0.0–1.0 analog values.
- Digital state derived from a threshold (0.3) — active when the corresponding axis exceeds it.
- Same lazy-tick pattern as `CfxControllerBinding`.

### EpicFightStateBridge — Battle mode sync

- Listens to Epic Fight's `ChangePlayerModeEvent` to detect battle/minecraft mode transitions.
- Pushes `epicfight:battle_mode` state to ControlFlex's `IPlayerStateRegistry`.
- Handles login timing: defers initial state query by 20 ticks after `ClientPlayerNetworkEvent.LoggingIn` to ensure Epic Fight's capability system is initialized.
- This state is consumed by `itemSuppressKeys.playerState` conditions in compat JSON.

## Mixin Strategy

Epic Fight hardcodes `EpicFightControlifyControllerMod.getBinding(this)` inside `controllerBinding()`. To redirect this to ControlFlex:

- **Target**: `EpicFightInputAction.controllerBinding()` and `MinecraftInputAction.controllerBinding()`.
- **Strategy**: `@Overwrite` the entire method body.
- **Why `@Overwrite`**: The method's logic is a hardcoded class reference — there's no injection point or registry to hook into. `@Overwrite` replaces it cleanly.
- **Key constraint**: The mixin **never references** `EpicFightControlifyControllerMod` — if Controlify isn't installed, referencing its class causes `ClassNotFoundException`. Instead, it casts `this` and calls `CfxEpicFightControllerMod.getBinding()`.
- **Compatibility**: This mixin conflicts with Controlify (both overwrite the same method). Declared as incompatible in `mods.toml`.

## Data Flow

### Combat action (e.g. ATTACK)

```
Controller button press
  → ControlFlex ActionStateTracker: "epicfight:key.epicfight.attack" = active
    → Epic Fight: InputManager.checkAction(ATTACK)
      → action.controllerBinding()  [Mixin → CfxEpicFightControllerMod.getBinding()]
        → ActionBindingMapper.get(ATTACK) → CfxControllerBinding
          → CfxControllerBinding.isDigitalActiveNow()
            → IActionStateProvider.isGameActionActive("epicfight:key.epicfight.attack")
              → true → Epic Fight triggers attack
```

### Movement

```
Left stick
  → ControlFlex IControllerState.leftStickX/Y
    → Epic Fight: controllerMod.getInputState()
      → CfxEpicFightControllerMod.getInputState()
        → PlayerInputState(forwardImpulse=-leftStickY, leftImpulse=-leftStickX, ...)
          → Epic Fight drives player movement
```

### State sync

```
Epic Fight: player switches to battle mode
  → ChangePlayerModeEvent fired
    → EpicFightStateBridge.onPlayerModeChanged()
      → IPlayerStateRegistry.setState("epicfight:battle_mode", true)
        → ControlFlex updates playerState
          → compat JSON itemSuppressKeys with playerState condition now active
```
