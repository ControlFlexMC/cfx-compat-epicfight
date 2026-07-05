# 技术实现

## 注册时机

Epic Fight 要求在开始处理输入前完成 controller mod 注册。注册窗口为 `FMLClientSetupEvent`：

```java
// CfxEpicFightMod.onClientSetup()
CfxEpicFightControllerMod impl = new CfxEpicFightControllerMod();
EpicFightControllerModProvider.set(MOD_ID, impl);
```

注册后，`verifyMixinApplied()` 对 `EpicFightInputAction.ATTACK` 调用 `controllerBinding()` 确认两个 Mixin 均已正确加载。此检查失败意味着桥接模组已安装但无法工作 — 以 ERROR 级别记录日志。

## Mixin 详解

### 目标方法

两个 `controllerBinding()` 方法遵循相同模式。修改前：

```java
// Epic Fight 源码（简化）
public Optional<ControllerBinding> controllerBinding() {
    if (EpicFightControllerModProvider.get() == null)
        throw new IllegalStateException("...");
    return Optional.of(EpicFightControlifyControllerMod.getBinding(this));
}
```

修改后：

```java
// Mixin 替换为
public Optional<ControllerBinding> controllerBinding() {
    if (EpicFightControllerModProvider.get() == null)
        throw new IllegalStateException("...");
    EpicFightInputAction self = (EpicFightInputAction)(Object) this;
    return CfxEpicFightControllerMod.getBinding(self);
}
```

### 为什么要通过 Object 转型

`EpicFightInputAction` 是枚举。Mixin 目标类和 `this` 引用在运行时是同一类型，但 Mixin 处理器在编译期看不到这个关系（目标是 `value = EpicFightInputAction.class`，mixin 类是独立的类）。通过 `Object` 转型绕过了编译器检查，同时不会引入对 Controlify 的类引用。

### remap = false

Epic Fight 是混淆过的 Forge 模组。目标方法属于 Epic Fight 的 API 层，不是 Minecraft 原版代码，因此 `remap = false` 阻止 Mixin 处理器通过 MCP/Intermediary mapping 去映射目标类名。

## 绑定类型

### 数字绑定（`CfxControllerBinding`）

每个数字动作映射到一个 ControlFlex action ID。ID 格式与 ControlFlex 内部 `ActionStateTracker` 的 key 一致：

| 来源 | Action ID 格式 | 示例 |
|------|---------------|------|
| Epic Fight 按键 | `epicfight:<keyName>` | `"epicfight:key.epicfight.attack"` |
| Minecraft 原版按键 | `<keyName>`（无前缀） | `"key.jump"` |

`isGuiAction` 标志决定查询 ControlFlex 的哪一层：

- `false`（game 层）→ `IActionStateProvider.isGameActionActive()`
- `true`（GUI 层）→ `IActionStateProvider.isGuiActionActive()`

当前唯一映射的 GUI 层动作是 `WEAPON_INNATE_SKILL_TOOLTIP` → `"epicfight:key.epicfight.show_tooltip"`。这正是桥接模组出现之前损坏的那个动作。

### 模拟绑定（`CfxMovementBinding`）

移动动作不经过 action tracker — ControlFlex 在原始输入层处理摇杆移动，而非作为离散动作。因此 `CfxMovementBinding` 在 `getAnalogueNow()` 中直接读取 `IControllerState` 的摇杆轴值：

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

数字状态通过阈值判定模拟量（0.3）得出。这同时提供了模拟移动（通过 `getAnalogueNow()`）和数字边沿检测（通过 `isDigitalJustPressed()` / `isDigitalJustReleased()`）。

## 惰性 Tick 模式

两种绑定类型共享惰性 tick 刷新机制，无需在每个 client tick 显式调用 `tickAll()`：

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

**为什么用惰性模式而非推送：**

- Epic Fight 在一个 tick 内的不可预测时间点调用 binding 方法（例如 `isDigitalActiveNow()` 可能被不同的 `checkAction()` 调用点多次调用同一 action）。
- 单次 `refreshIfNewTick()` 确保每 tick 只采样一次，同一 tick 内后续所有调用返回一致的值。
- 无需专门的 tick 处理器 — 每 tick 的首次查询触发刷新。

**为什么用自定义 tick 计数器而非 `gameTime`：**

Epic Fight 的 `gameTime`（来自 `Minecraft.getInstance().level`）在世界加载前为 `0`，游戏暂停时冻结。`clientTickCounter` 在 `ClientTickEvent.Phase.END` 无条件递增，因此绑定在加载画面、暂停菜单和多人游戏登录期间均可正常工作。

## 战斗模式状态桥

### 问题

ControlFlex 的 compat JSON 系统支持 `playerState` 条件（如"持双手武器**且**在战斗模式下时抑制攻击"）。但 Epic Fight 的战斗模式是内部状态 — 无代码集成时 ControlFlex 无法得知。

### 解决方案

`EpicFightStateBridge` 通过监听 Epic Fight 的模式变更事件并推送状态来打通这个隔阂：

```java
// 监听 ChangePlayerModeEvent
private void onPlayerModeChanged(ChangePlayerModeEvent event) {
    epicFightMode = event.getPlayerMode() == PlayerPatch.PlayerMode.EPICFIGHT;
    pushState();
}

// 推送到 ControlFlex
private void pushState() {
    IPlayerStateRegistry registry = ControlFlexApi.getPlayerStateRegistry();
    if (registry != null) {
        registry.setState(STATE_KEY, epicFightMode);
    }
}
```

### 延迟初始化

进入世界时，Epic Fight 的 `PlayerPatch` capability 可能尚未就绪。桥接层在 `ClientPlayerNetworkEvent.LoggingIn` 后延迟 20 tick 再查询初始状态：

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

### 事件取消处理

如果 `ChangePlayerModeEvent` 被其他模组取消，桥接层会从 capability 系统重新查询实际模式，而非信任事件中的提议模式。

## 回退策略

每个读取 ControlFlex 状态的入口点都包含空安全检查与键盘回退：

```java
// getInputState()
if (!ControlFlexApi.isAvailable()) return fallbackVanillaInput();
IInputProvider input = ControlFlexApi.getInputProvider();
if (input == null || !input.isConnected()) return fallbackVanillaInput();
IControllerState state = input.getControllerState();
if (state == null) return fallbackVanillaInput();
// ... 使用 state
```

这涵盖了以下场景：
- ControlFlex 未安装 → `fallbackVanillaInput()` 读取 `Minecraft.getInstance().player.input`
- SDL3 初始化失败 → ControlFlex 可用但无手柄 → `KEYBOARD_MOUSE` 模式
- 游戏中手柄断开 → 下次 `getInputMode()` 返回 `KEYBOARD_MOUSE`

## 技术决策与权衡

| 决策 | 理由 | 权衡 |
|------|------|------|
| `@Overwrite` 而非 `@Inject` | `controllerBinding()` 是完全硬编码的方法 — 不存在可部分 hook 的点 | 若 Epic Fight 大幅修改方法签名则需同步更新 |
| 自定义 `clientTickCounter` 而非 `gameTime` | `gameTime` 在未加载世界时为 0，暂停时冻结 | 多一个字段和监听器需要维护 |
| 惰性 tick 而非推送 tick | Epic Fight 在不可预测的时间点查询 binding；惰性模式避免冗余采样 | 可预测性略低 — 每 tick 的首次查询稍贵 |
| 通过 `ActionStateTracker` 处理 `jump`/`sneak` | 复用 ControlFlex 已有的动作绑定系统 | 最大延迟 ~50ms（tick 粒度）— 对跳跃/潜行可接受 |
| Epic Fight SPI 而非 GLFW/InputConstants mixin | GLFW 由父 ClassLoader（LWJGL）加载，Mixin 无法触及 | 与 Epic Fight API 耦合更紧 — 版本锁定到 Epic Fight 的 `IEpicFightControllerMod` 接口 |
| 独立的 `CfxMovementBinding` 类 | 移动本质是模拟量；数字绑定是二值的。独立类各司其职 | 两个 binding 类而非一个带模式标志的类 |

## Compat JSON 要点

附带的 `epicfight_keys.json` 承担了按键路由的主要工作。关键设计：

- **所有战斗动作使用 `skipForgeKeys`** — Epic Fight 同时监听 `KeyMapping.setPressed()` 和 Forge `InputEvent`。不用 `skipForgeKeys` 会导致每次按键触发两次。
- **guard 使用 `specialActionKeys` + `PHASE_PERSISTENT`** — 打开界面（背包等）时防御必须保持按住，否则玩家掉防受伤。
- **`itemSuppressKeys`** — 战斗模式下持双手武器时抑制原版 `attack`，防止 Epic Fight 的攻击系统与原版左键冲突。
- **`guiKeys` 给 `show_tooltip`** — 技能提示是 GUI 层动作，仅在有界面打开（技能界面、背包）时激活。
