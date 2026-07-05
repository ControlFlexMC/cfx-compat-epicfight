# cfx-compat-epicfight

ControlFlex ↔ Epic Fight 桥接模组 — 通过 Epic Fight 原生 `IEpicFightControllerMod` SPI 注入手柄输入，使战斗动作、移动和武器技能支持手柄操作。

[English](README.md)

## 为什么需要这个模组？

Epic Fight 有自己的手柄输入系统（`IEpicFightControllerMod` 接口）。如果没有注册的控制器实现，Epic Fight 会回退到检查原始 GLFW 键盘状态 — 手柄绑定永远无法满足这个条件。

**仅靠 JSON compat 配置不够**，原因：

- Epic Fight 直接从其 controller mod 读取 `PlayerInputState`（摇杆移动、跳跃、潜行），而非从 `KeyMapping` 事件。
- Epic Fight 按 action 查询 `ControllerBinding` 来决定输入模式。JSON 的 `skipForgeKeys` / `guiKeys` 可以路由按键事件，但无法提供 Epic Fight 所需的 `ControllerBinding` 对象。

## 这个模组做了什么？

| 功能 | 实现方式 |
|------|----------|
| **控制器输入注入** | 将 ControlFlex 注册为 Epic Fight 的控制器模组。摇杆 → `PlayerInputState`，动作 → `ControllerBinding`，全程走 Epic Fight 原生 API。 |
| **战斗模式状态同步** | 监听 `ChangePlayerModeEvent`，实时推送 `epicfight:battle_mode` 到 ControlFlex，使 compat JSON 中的 `playerState` 条件生效（如在战斗模式下持特定武器时抑制"使用"键）。 |
| **部署 compat 与 guide 配置** | 附带 `epicfight_keys.json`（按键路由、`skipForgeKeys`、`itemSuppressKeys`）和 `epicfight_guid.json`（新手手柄绑定引导）。 |

## 工作原理

```
控制器硬件
  → ControlFlex（原始输入 + 动作绑定）
    → cfx-compat-epicfight（转换为 Epic Fight API）
      → Epic Fight（战斗、移动、锁定、技能）
```

桥接层实现了 `IEpicFightControllerMod` 接口，并通过 Mixin `@Overwrite` 将 Epic Fight 的 `controllerBinding()` 方法重定向到 ControlFlex 的动作状态追踪器，替代了硬编码的 Controlify 路径。

## 前置模组

- **ControlFlex** ≥ 0.8.5
- **Epic Fight** ≥ 20.14（Minecraft 1.20.1, Forge）
- **Mixin** 0.8.5（Forge 内置）

## 不兼容

- **Controlify** — 两者都会注册为 Epic Fight 的控制器模组，只能二选一。

## 安装

将模组 JAR 放入 `mods/` 目录，与 ControlFlex 和 Epic Fight 并列。首次启动时，桥接模组会将 compat/guide 配置部署到 `config/controlflex/compat/cfx-mod/` 和 `config/controlflex/guides/cfx-mod/`。

## 文档

| 文档 | 内容 |
|------|------|
| [方案设计](docs/zh/design.md) | 架构分层、组件职责、数据流 |
| [技术实现](docs/zh/implementation.md) | Mixin 策略、绑定类型、惰性 tick、状态桥 |

## 许可证

[MIT](LICENSE)
