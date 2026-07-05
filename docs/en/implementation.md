# Implementation

## Registration Timing

Epic Fight requires the controller mod to be registered before any input processing happens. The registration window is `FMLClientSetupEvent`:

```java
// CfxEpicFightMod.onClientSetup()
CfxEpicFightControllerMod impl = new CfxEpicFightControllerMod();
EpicFightControllerModProvider.set(MOD_ID, impl);
```

After registration, `verifyMixinApplied()` calls `controllerBinding()` on `EpicFightInputAction.ATTACK` to confirm both Mixins loaded correctly. A failure here means the bridge is installed but non-functional — logged at ERROR level.

## Mixin Details

### Target methods

Both `controllerBinding()` methods follow the same pattern. Before:

```java
// Epic Fight source (simplified)
public Optional<ControllerBinding> controllerBinding() {
    if (EpicFightControllerModProvider.get() == null)
        throw new IllegalStateException("...");
    return Optional.of(EpicFightControlifyControllerMod.getBinding(this));
}
```

After:

```java
// Mixin replaces with
public Optional<ControllerBinding> controllerBinding() {
    if (EpicFightControllerModProvider.get() == null)
        throw new IllegalStateException("...");
    EpicFightInputAction self = (EpicFightInputAction)(Object) this;
    return CfxEpicFightControllerMod.getBinding(self);
}
```

### Why cast via Object

`EpicFightInputAction` is an enum. The Mixin target class and `this` reference share the same runtime type, but the Mixin processor can't see that relationship at compile time (the target is `value = EpicFightInputAction.class`, the mixin class is a separate class). Casting through `Object` bypasses the compiler's type-checking without introducing class references to Controlify.

### remap = false

Epic Fight is an obfuscated Forge mod. The target methods belong to Epic Fight's API layer, not Minecraft vanilla, so `remap = false` prevents the Mixin processor from trying to map the target class name through MCP/Intermediary mappings.

## Binding Types

### Digital bindings (`CfxControllerBinding`)

Each digital action maps to a ControlFlex action ID. The ID format matches ControlFlex's internal `ActionStateTracker` keys:

| Source | Action ID format | Example |
|--------|-----------------|---------|
| Epic Fight key | `epicfight:<keyName>` | `"epicfight:key.epicfight.attack"` |
| Minecraft vanilla key | `<keyName>` (no prefix) | `"key.jump"` |

The `isGuiAction` flag determines which ControlFlex layer is queried:

- `false` (game layer) → `IActionStateProvider.isGameActionActive()`
- `true` (GUI layer) → `IActionStateProvider.isGuiActionActive()`

The only GUI-layer action currently mapped is `WEAPON_INNATE_SKILL_TOOLTIP` → `"epicfight:key.epicfight.show_tooltip"`. This is the action that was broken before the bridge existed.

### Analog bindings (`CfxMovementBinding`)

Movement actions don't go through the action tracker — ControlFlex handles stick movement at the raw input level, not as discrete actions. Instead, `CfxMovementBinding` reads `IControllerState` stick axes directly in `getAnalogueNow()`:

```java
public float getAnalogueNow() {
    IControllerState state = input.getControllerState();
    return switch (direction) {
        case FORWARD  -> Math.max(0, -state.getLeftStickY());
        case BACKWARD -> Math.max(0,  state.getLeftStickY());
        case LEFT     -> Math.max(0, -state.getLeftStickX());
        case RIGHT    -> Math.max(0,  state.getLeftStickX());
    };
}
```

Digital state is derived by thresholding the analog value at 0.3. This provides both analog movement (through `getAnalogueNow()`) and digital edge detection (through `isDigitalJustPressed()` / `isDigitalJustReleased()`).

## Lazy Tick Pattern

Both binding types share a lazy-tick refresh mechanism instead of requiring an explicit `tickAll()` on every client tick:

```java
private boolean activeNow = false;
private boolean activePreviously = false;
private int lastTickCount = -1;

private void refreshIfNewTick() {
    int currentTick = CfxEpicFightMod.getClientTickCounter();
    if (currentTick != lastTickCount) {
        activePreviously = activeNow;
        activeNow = queryCurrentState();
        lastTickCount = currentTick;
    }
}
```

**Why lazy instead of push:**

- Epic Fight calls binding methods at unpredictable times within a tick (e.g. `isDigitalActiveNow()` may be called multiple times for the same action from different `checkAction()` sites).
- A single `refreshIfNewTick()` ensures state is sampled once per tick, and all subsequent calls within the same tick return consistent values.
- No dedicated tick handler needed — the first query each tick triggers the refresh.

**Why a custom tick counter instead of `gameTime`:**

Epic Fight's `gameTime` (from `Minecraft.getInstance().level`) is `0` before world load and freezes when the game is paused. `clientTickCounter` increments unconditionally on `ClientTickEvent.Phase.END`, so bindings work during loading screens, pause menus, and multiplayer login.

## Battle Mode State Bridge

### Problem

ControlFlex's compat JSON system supports `playerState` conditions (e.g. "suppress attack when holding two-hand weapons **and** in battle mode"). But Epic Fight's battle mode is internal state — ControlFlex has no way to know it without code integration.

### Solution

`EpicFightStateBridge` bridges this gap by listening to Epic Fight's mode-change event and pushing state:

```java
// Listens to ChangePlayerModeEvent
private void onPlayerModeChanged(ChangePlayerModeEvent event) {
    epicFightMode = event.getPlayerMode() == PlayerPatch.PlayerMode.EPICFIGHT;
    pushState();
}

// Pushes to ControlFlex
private void pushState() {
    IPlayerStateRegistry registry = ControlFlexApi.getPlayerStateRegistry();
    if (registry != null) {
        registry.setState(STATE_KEY, epicFightMode);
    }
}
```

### Delayed initialization

On world join, Epic Fight's `PlayerPatch` capability may not be available immediately. The bridge defers the initial query by 20 ticks after `ClientPlayerNetworkEvent.LoggingIn`:

```java
private void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
    delayedQueryCountdown = LOGIN_QUERY_DELAY_TICKS;
}

public void tick() {
    if (delayedQueryCountdown == 0) {
        epicFightMode = queryCurrentMode();
        pushState();
    }
}
```

### Event cancellation handling

If `ChangePlayerModeEvent` is cancelled by another mod, the bridge re-queries the actual mode from the capability system instead of trusting the event's proposed mode.

## Fallback Strategy

Every entry point that reads ControlFlex state includes null-safety checks with keyboard fallback:

```java
// getInputState()
if (!ControlFlexApi.isAvailable()) return fallbackVanillaInput();
IInputProvider input = ControlFlexApi.getInputProvider();
if (input == null || !input.isConnected()) return fallbackVanillaInput();
IControllerState state = input.getControllerState();
if (state == null) return fallbackVanillaInput();
// ... use state
```

This handles:
- ControlFlex not installed → `fallbackVanillaInput()` reads `Minecraft.getInstance().player.input`
- SDL3 initialization failed → ControlFlex available but no controller → `KEYBOARD_MOUSE` mode
- Controller disconnected mid-game → next `getInputMode()` returns `KEYBOARD_MOUSE`

## Technical Decisions & Tradeoffs

| Decision | Rationale | Tradeoff |
|----------|-----------|----------|
| `@Overwrite` rather than `@Inject` | `controllerBinding()` is a complete hardcoded method — no partial hook point exists | Breaks if Epic Fight significantly changes the method signature |
| Custom `clientTickCounter` over `gameTime` | `gameTime` is 0 without a loaded world and freezes on pause | One more field and listener to maintain |
| Lazy tick over push tick | Epic Fight queries bindings at unpredictable points; lazy avoids redundant sampling | Slightly less predictable — first query each tick is marginally more expensive |
| `jump`/`sneak` via `ActionStateTracker` | Reuses ControlFlex's existing action binding system | ~50ms max latency (tick granularity) — acceptable for jump/sneak but noticeable if used for frame-precise actions |
| Epic Fight SPI over GLFW/InputConstants mixin | GLFW is loaded by the parent classloader (LWJGL), Mixin can't reach it | Tighter coupling to Epic Fight's API — version-locked to Epic Fight's `IEpicFightControllerMod` interface |
| Separate `CfxMovementBinding` class | Movement is analog by nature; digital bindings are binary. Separate classes keep each simple | Two binding classes instead of one with mode flags |

## Compat JSON Highlights

The bundled `epicfight_keys.json` does the heavy lifting for key routing. Key decisions:

- **`skipForgeKeys` on all combat actions** — Epic Fight listens on both `KeyMapping.setPressed()` and Forge `InputEvent`. Without `skipForgeKeys`, every action fires twice per press.
- **`specialActionKeys` with `PHASE_PERSISTENT` on guard** — guard must stay held when a screen opens (inventory etc.), otherwise the player drops guard and gets hit.
- **`itemSuppressKeys`** — suppresses vanilla `attack` while holding two-hand weapons in battle mode, preventing conflicts between Epic Fight's attack system and vanilla left-click.
- **`guiKeys` for `show_tooltip`** — tooltip display is a GUI-layer action; it should only activate while a screen is open (skill screen, inventory).
