# cfx-compat-epicfight

ControlFlex ↔ Epic Fight bridge mod — injects controller input through Epic Fight's native `IEpicFightControllerMod` SPI, so combat actions, movement, and weapon skills work with a gamepad.

[中文文档](README_ZH.md)

## Why This Mod?

Epic Fight uses its own controller input system (`IEpicFightControllerMod`). Without a registered controller mod implementation, Epic Fight falls back to checking raw GLFW keyboard state — which gamepad bindings can never satisfy.

**JSON compat configs alone are not enough** because:

- Epic Fight reads `PlayerInputState` (stick movement, jump, sneak) directly from its controller mod — not from `KeyMapping` events.
- Epic Fight queries `ControllerBinding` per action to decide input mode. JSON `skipForgeKeys` / `guiKeys` can route key events, but they can't provide the `ControllerBinding` objects Epic Fight expects.

## What This Mod Does

| Feature | How |
|---------|-----|
| **Controller input injection** | Registers ControlFlex as Epic Fight's controller mod. Stick → `PlayerInputState`, actions → `ControllerBinding`, all through Epic Fight's native API. |
| **Battle mode state sync** | Detects `ChangePlayerModeEvent` and pushes `epicfight:battle_mode` to ControlFlex, enabling `playerState` conditions in compat JSON (e.g. suppress "use" while holding certain weapons in battle mode). |
| **Compat & guide deployment** | Ships `epicfight_keys.json` (key routing, `skipForgeKeys`, `itemSuppressKeys`) and `epicfight_guid.json` (binding suggestions for new players). |

## How It Works

```
Controller hardware
  → ControlFlex (raw input + action binding)
    → cfx-compat-epicfight (translates to Epic Fight API)
      → Epic Fight (combat, movement, lock-on, skills)
```

The bridge implements `IEpicFightControllerMod` and uses Mixin `@Overwrite` on `controllerBinding()` to redirect Epic Fight's action-to-binding lookup through ControlFlex's action state tracker, instead of the hardcoded Controlify path.

## Requirements

- **ControlFlex** ≥ 0.8.5
- **Epic Fight** ≥ 20.14 (Minecraft 1.20.1, Forge)
- **Mixin** 0.8.5 (bundled by Forge)

## Incompatible With

- **Controlify** — both register as Epic Fight's controller mod. Only one can be active.

## Install

Place the mod JAR in `mods/` alongside ControlFlex and Epic Fight. On first launch the bridge deploys its compat/guide configs to `config/controlflex/compat/cfx-mod/` and `config/controlflex/guides/cfx-mod/`.

## Documentation

| Document | Content |
|----------|---------|
| [Design](docs/en/design.md) | Architecture layers, component roles, data flow |
| [Implementation](docs/en/implementation.md) | Mixin strategy, binding types, lazy tick, state bridge |

## License

[MIT](LICENSE)
