# Control Flex × Epic Fight

A compatibility bridge that brings full controller support to Epic Fight through ControlFlex.

---

## What This Does

Without this mod, Epic Fight **cannot see your controller** — even with ControlFlex installed. Epic Fight reads input through its own `IEpicFightControllerMod` SPI, not through standard `KeyMapping` events. This bridge registers ControlFlex as Epic Fight's controller provider and translates every button press, stick movement, and action binding between the two systems.

**The result:** full gamepad control over Epic Fight's entire combat system.

---

## Features

- 🎮 **All combat actions mappable to controller** — Attack, Guard, Dodge, Switch Mode, Weapon Innate Skill, Mover Skill, Lock-On, and more.
- 🕹️ **Analog stick movement integrated with combat** — left stick position is passed directly to Epic Fight, enabling precise footwork, strafing, and speed-sensitive guard detection.
- 🎯 **Lock-on targeting** — lock onto enemies and cycle between targets using controller buttons.
- 🔄 **Battle mode state sync** — when you switch between mining and combat mode, this bridge tells ControlFlex so it can suppress conflicting vanilla actions (e.g. blocking "use" while wielding a two-handed weapon in battle mode).
- 📦 **Plug-and-play configs** — automatically installs optimized key routing, double-trigger prevention, and in-game tutorial guides on first launch. No manual setup required.
- 🌐 **Localized guides** — in-game tutorials available in English and 简体中文.
- ⌨️ **Graceful fallback** — if your controller disconnects or ControlFlex is unavailable, Epic Fight falls back to standard keyboard input without errors.

---

## Dependencies (Required)

| Mod | Version |
|-----|---------|
| [ControlFlex](https://www.curseforge.com/minecraft/mc-mods/controlflex) | ≥ 0.8.5 |
| [Epic Fight](https://www.curseforge.com/minecraft/mc-mods/epic-fight-mod) | ≥ 20.14 |

---

## Compatibility

- ❌ **Not compatible with Controlify** — both mods overwrite the same Epic Fight controller binding methods and cannot coexist.
- ✅ Works in singleplayer and multiplayer (client-side only).

---

## How It Works

```
Gamepad → ControlFlex (raw input + action binding system)
                ↓
         cfx-compat-epicfight (translates to Epic Fight API objects)
                ↓
         Epic Fight (combat engine: attacks, guard, dodge, skills, lock-on)
```

Two Mixin `@Overwrite`s redirect Epic Fight's hardcoded Controlify references to this bridge. An SPI plugin deploys compatibility configs and guide assets on first launch. A state bridge keeps ControlFlex aware of the player's current battle mode so item/action suppression rules work correctly.

---

## Notes

- This mod is **client-side only** — it does not need to be installed on servers.
- Make sure ControlFlex detects your controller before entering a world.
- If Weapon Innate Skill doesn't seem to work, bind it to the **same button** as Attack and set the binding mode to **HOLD** in ControlFlex settings.
