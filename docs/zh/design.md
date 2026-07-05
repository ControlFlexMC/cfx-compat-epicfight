# 方案设计

## 问题

Epic Fight 有自己的手柄抽象层（`IEpicFightControllerMod` SPI）。启动时它检查是否注册了 controller mod：

- 有 → 使用 `controllerBinding()` 和 `getInputState()` 处理所有输入。
- 无 → 回退到 GLFW 键盘轮询，手柄绑定永远无法触发。

没有 ControlFlex 桥接层时，即使安装了 ControlFlex 并连接了手柄，Epic Fight 仍将玩家视为纯键盘用户。

**为什么仅靠 JSON compat 配置不够：**

| 问题 | JSON 为什么解决不了 |
|------|---------------------|
| Epic Fight 直接读取 `PlayerInputState` | `PlayerInputState` 包含摇杆轴值、移动冲量、跳跃/潜行布尔值 — 这些不经过 `KeyMapping`。JSON 能路由按键事件，但无法填充结构化输入状态。 |
| Epic Fight 每个 action 查询 `ControllerBinding` | 每个 `InputAction` 枚举值调用 `controllerBinding()` 决定走手柄还是键盘路径。JSON 没法返回 `ControllerBinding` 对象。 |
| `controllerBinding()` 硬编码指向 Controlify | Epic Fight 源码直接调用 `EpicFightControlifyControllerMod.getBinding(this)` — 没有注册表或 service loader 机制支持替代的控制器模组。 |

## 架构

三层解耦，桥接层居中：

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
│  CfxControllerBinding   （数字动作）          │
│  CfxMovementBinding     （模拟摇杆）          │
│  ActionBindingMapper    （动作→绑定映射表）   │
│  EpicFightStateBridge   （战斗模式同步）      │
└──────────────────┬───────────────────────────┘
                   │ ControlFlexApi / SPI
┌──────────────────┴───────────────────────────┐
│                ControlFlex                    │
│                                              │
│  IActionStateProvider   （game/GUI 动作）     │
│  IInputProvider         （控制器状态）        │
│  IPlayerStateRegistry   （模组状态推送）      │
│  IControlFlexPlugin     （生命周期回调）      │
└──────────────────────────────────────────────┘
```

## 组件职责

### CfxEpicFightMod — Forge 模组入口

- 在 `FMLClientSetupEvent` 中将 `CfxEpicFightControllerMod` 注册到 Epic Fight 的 `EpicFightControllerModProvider`。
- 维护 `clientTickCounter`（每个 `ClientTickEvent` 递增），供 binding 检测 tick 边界，避免依赖 `gameTime`（暂停或 `level==null` 时会冻结）。
- 启动时验证 Mixin 是否生效 — 对已知 action 调用 `controllerBinding()` 并记录成功/失败日志。

### CfxEpicFightPlugin — ControlFlex SPI 插件

- 实现 `IControlFlexPlugin` 进行生命周期集成：
  - `onInstallCompatConfigs` → 部署 `epicfight_keys.json` 到 `config/controlflex/compat/cfx-mod/`。
  - `onInstallGuideAssets` → 部署 `epicfight_guid.json` 到 `config/controlflex/guides/cfx-mod/`。
  - `onControlFlexReady` → API provider 就绪后初始化状态桥。
- 版本检查 ControlFlex API ≥ 0.8.5。

### CfxEpicFightControllerMod — 控制器模组实现

- 实现 `IEpicFightControllerMod`：
  - `getInputMode()`：当 ControlFlex 可用且有手柄连接时返回 `MIXED`；否则返回 `KEYBOARD_MOUSE`。这一个方法的返回值决定了 Epic Fight 整个手柄路径的开关。
  - `getInputState()`：从 ControlFlex 的 `IControllerState`（摇杆轴）和 `IActionStateProvider`（跳跃/潜行）构建 `PlayerInputState`。任何 null 检查失败时回退到原版 `PlayerInput`。
- 对外暴露静态 `getBinding()` 方法供 Mixin 回调，内部委托给 `ActionBindingMapper`。

### ActionBindingMapper — 动作到绑定的映射表

- 将每个 Epic Fight `InputAction` 枚举值映射到对应的 `ControllerBinding` 实现。
- 分三类：
  - **EpicFight 数字动作**（ATTACK、GUARD、DODGE 等）→ `CfxControllerBinding`，对应 game 层 action ID，如 `"epicfight:key.epicfight.attack"`。
  - **Minecraft 数字动作**（JUMP、SNEAK、ATTACK_DESTROY 等）→ `CfxControllerBinding`，对应原版 key ID，如 `"key.jump"`。
  - **模拟摇杆动作**（MOVE_FORWARD/BACKWARD/LEFT/RIGHT）→ `CfxMovementBinding`，直接读取摇杆轴值。
- 无绑定的动作（OPEN_SKILL_SCREEN 等）返回 `Optional.empty()`，Epic Fight 回退到键盘检查路径。

### CfxControllerBinding — 数字动作绑定

- 将 ControlFlex 的 `IActionStateProvider`（game 或 GUI 层）包装为 Epic Fight 的 `ControllerBinding`。
- 惰性 tick 刷新：每新 tick 将 `activeNow → activePreviously`，然后查询 ControlFlex 获取当前状态。
- 实现了 `ControllerBinding` 的所有边沿检测方法（`isDigitalJustPressed`、`isDigitalJustReleased` 等）。

### CfxMovementBinding — 模拟摇杆绑定

- 直接读取 `IControllerState` 的摇杆轴值（不经过 action tracker），返回 0.0–1.0 模拟量。
- 数字状态由阈值判定（0.3）— 对应轴超过阈值即为 active。
- 与 `CfxControllerBinding` 相同的惰性 tick 模式。

### EpicFightStateBridge — 战斗模式状态同步

- 监听 Epic Fight 的 `ChangePlayerModeEvent`，检测战斗/普通模式切换。
- 将 `epicfight:battle_mode` 状态推送到 ControlFlex 的 `IPlayerStateRegistry`。
- 处理登录时序：在 `ClientPlayerNetworkEvent.LoggingIn` 后延迟 20 tick 再查询初始状态，确保 Epic Fight 的能力系统已初始化。
- 该状态被 compat JSON 中的 `itemSuppressKeys.playerState` 条件所消费。

## Mixin 策略

Epic Fight 在 `controllerBinding()` 中硬编码了 `EpicFightControlifyControllerMod.getBinding(this)`。要重定向到 ControlFlex：

- **目标**: `EpicFightInputAction.controllerBinding()` 和 `MinecraftInputAction.controllerBinding()`。
- **策略**: `@Overwrite` 整个方法体。
- **为什么用 `@Overwrite`**: 该方法逻辑是一个硬编码的类引用 — 没有注入点或注册表可供 hook。`@Overwrite` 是最干净的替换方式。
- **关键约束**: Mixin **绝不引用** `EpicFightControlifyControllerMod` — 如果 Controlify 未安装，引用其类会导致 `ClassNotFoundException`。取而代之的是 cast `this` 并调用 `CfxEpicFightControllerMod.getBinding()`。
- **兼容性**: 此 Mixin 与 Controlify 冲突（两者都 overwrite 同一方法）。在 `mods.toml` 中声明不兼容。

## 数据流

### 战斗动作（如 ATTACK）

```
手柄按键按下
  → ControlFlex ActionStateTracker: "epicfight:key.epicfight.attack" = 激活
    → Epic Fight: InputManager.checkAction(ATTACK)
      → action.controllerBinding()  [Mixin → CfxEpicFightControllerMod.getBinding()]
        → ActionBindingMapper.get(ATTACK) → CfxControllerBinding
          → CfxControllerBinding.isDigitalActiveNow()
            → IActionStateProvider.isGameActionActive("epicfight:key.epicfight.attack")
              → true → Epic Fight 触发攻击
```

### 移动

```
左摇杆
  → ControlFlex IControllerState.leftStickX/Y
    → Epic Fight: controllerMod.getInputState()
      → CfxEpicFightControllerMod.getInputState()
        → PlayerInputState(forwardImpulse=-leftStickY, leftImpulse=-leftStickX, ...)
          → Epic Fight 驱动玩家移动
```

### 状态同步

```
Epic Fight: 玩家切换到战斗模式
  → ChangePlayerModeEvent 触发
    → EpicFightStateBridge.onPlayerModeChanged()
      → IPlayerStateRegistry.setState("epicfight:battle_mode", true)
        → ControlFlex 更新 playerState
          → compat JSON 中带 playerState 条件的 itemSuppressKeys 生效
```
