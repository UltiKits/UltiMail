# UltiMail 单元测试

## 测试概览

UltiMail 插件包含全面的单元测试，覆盖实体、配置、服务、GUI、监听器和命令组件。

### 测试统计

| 类别 | 测试类 | 状态 |
|------|--------|------|
| 实体 | `MailDataTest` | ✅ 通过 |
| 配置 | `MailConfigTest` | ✅ 通过 |
| 服务 | `MailServiceTest` | ✅ 通过 |
| GUI | `MailboxGUITest` | ✅ 通过 |
| GUI | `SentboxGUITest` | ✅ 通过 |
| GUI | `AttachmentSelectorPageTest` | ✅ 通过 |
| 监听器 | `MailNotifyListenerTest` | ✅ 通过 |
| 命令 | `MailCommandTest` | ✅ 通过 |
| 命令 | `SendMailCommandTest` | ✅ 通过 |

## 运行测试

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=MailDataTest
```

## 测试框架

- **JUnit 5** (5.10.1) - 测试框架
- **MockBukkit** (`org.mockbukkit.mockbukkit:mockbukkit-v1.21`, 4.101.0) - Bukkit/Paper 测试服务器模拟
- **Mockito** (5.5.0) - 通用模拟
- **AssertJ** (3.24.2) - 流畅断言

## 测试结构

```
src/test/java/com/ultikits/plugins/mail/
├── UltiMailRegistrySentinelTest.java  # 回归防护哨兵：确认测试仍能到达真实 Bukkit 注册表
├── utils/
│   ├── MockBukkitHelper.java    # MockBukkit 清理工具
│   └── TestHelper.java          # Mock 实例创建助手
├── entity/
│   └── MailDataTest.java        # 邮件数据实体测试
├── config/
│   └── MailConfigTest.java      # 配置实体测试
├── service/
│   └── MailServiceTest.java     # 邮件服务测试
├── gui/
│   ├── MailboxGUITest.java      # 收件箱 GUI 测试
│   ├── SentboxGUITest.java      # 发件箱 GUI 测试
│   └── AttachmentSelectorPageTest.java  # 附件选择页测试
├── listener/
│   └── MailNotifyListenerTest.java   # 通知监听器测试
└── commands/
    ├── MailCommandTest.java     # /mail 命令测试
    └── SendMailCommandTest.java # /sendmail 命令测试
```

## 测试覆盖范围

### MailDataTest (16 个测试)
- 构造函数默认值
- `hasItems()` 方法
- `hasCommands()` 方法
- Getter/Setter 方法
- equals/hashCode 合约

### MailConfigTest (29 个测试)

- 默认配置值
- Getter/Setter 方法
- 边界值测试
- 消息占位符测试
- 邮件 SMTP 配置测试

## MockBukkit 迁移历史（2026-09，Phase 14）

`AttachmentSelectorPageTest`、`MailboxGUITest`、`SentboxGUITest` 曾长期携带
`@Disabled("MockBukkit 与 Java 21 + Paper API 存在兼容性问题，待修复")`，且从未真正运行过。

**实际根因已用字节码确认，并非笼统的"Java 21 兼容性问题"：** 旧依赖
`com.github.seeseemelk:MockBukkit-v1.19:3.1.0` 编译期调用的是
`org.bukkit.command.SimpleCommandMap` 的单参数构造函数
`SimpleCommandMap(Server)`；Paper 1.21 的 `paper-api` 只保留了双参数构造函数
`SimpleCommandMap(Server, Map<String, Command>)`，二者是二进制不兼容关系。移除
`@Disabled` 后单独运行 `AttachmentSelectorPageTest`，全部用例均在
`MockBukkit.mock()` 自身抛出的
`NoSuchMethodError: 'void org.bukkit.command.SimpleCommandMap.<init>(org.bukkit.Server)'`
处失败，尚未触及任何注册表相关代码。

`SendMailCommandTest` 中的 8 个用例则是另一类问题：经由
`Material.isAir -> Material.asBlockType` 触达 Bukkit 注册表，而旧依赖
`MockBukkit-v1.19:3.1.0` 的 jar 内**没有**任何 `io.papermc.paper.registry.RegistryAccess`
的 `META-INF/services` 声明（该 jar 只声明了 `LegacyComponentSerializer$Provider` 与
`org.bukkit.plugin.PluginLoader` 两项），`ServiceLoader` 因此找不到实现，抛出
`IllegalStateException: No RegistryAccess implementation found`。缺的是 classpath 上的
ServiceLoader 提供者，与「有没有引导真实测试期服务器」无关——不要把两者混为一谈。

**解决方式：** 将 MockBukkit 从 `com.github.seeseemelk:MockBukkit-v1.19:3.1.0` 迁移到框架自身已在使用的
`org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.101.0`（同时移除仅为该旧依赖存在的 `jitpack.io`
仓库配置）。迁移后三个 GUI 测试类的 `@Disabled` 被移除并全部通过；
`SendMailCommandTest` 的 8 个用例随之自动修复，且**没有**引导任何测试服务器：
`mockbukkit-v1.21-4.101.0.jar` 携带
`META-INF/services/io.papermc.paper.registry.RegistryAccess`
（→ `org.mockbukkit.mockbukkit.registry.RegistryAccessMock`）与
`META-INF/services/io.papermc.paper.ServerBuildInfo`
（→ `ServerBuildInfoMock`），注册表常量仅凭该依赖位于 classpath 即可解析。该测试类
自身仍是纯 Mockito——其类头写明 "Uses pure Mockito (no MockBukkit)"，全文不出现
`MockBukkit` 或 `MockBukkitHelper`。

**这条区分贯穿整个 Phase 14，务必分清：**

- **注册表常量解析**——`Material.X`、`Sound.X`、`InventoryType.X`、`PotionEffectType.X`
  能否解析、其类初始化能否成功——来自 classpath 上的 ServiceLoader 提供者，**不需要真实服务器**。
- **物品构造**——`new ItemStack(Material.X)`、真实的 `ItemMeta`——**需要**真实服务器，
  因为 `Material.asItemType()` 要经过 `Bukkit.getUnsafe()`。

实测（在模块测试 classpath 上运行、不调用 `MockBukkit.mock()` 的独立探针）：`Bukkit.getServer()`
为 `null` 时，上述四类常量连同 `Material.DIAMOND.isAir()`、`Material.STONE.asBlockType()`
全部通过，而 `new ItemStack(Material.DIAMOND)` 抛出
`IllegalArgumentException: DIAMOND isn't an item`；改为先调用 `MockBukkit.mock()` 后，
`Bukkit.getUnsafe()` 返回 `UnsafeValuesMock`，同一行构造随即成功。

`UltiMailRegistrySentinelTest` 作为回归防护，守的正是后一半：它的每条断言都依赖真实服务器实例
（`Bukkit.getUnsafe()`、`Bukkit.createProfile(...)`、`new ItemStack(...)`），刻意不断言任何
裸常量——裸常量只要依赖还在 classpath 上就能通过，无法察觉引导被移除。若模块共享的
`MockBukkitHelper.bootstrapServer()` 引导被静默移除或换成裸 Mockito `Server`，该哨兵即失败。

## 贡献指南

添加新测试时请遵循以下原则：

1. 使用 `@DisplayName` 提供中文测试描述
2. 使用 `@Nested` 类组织相关测试
3. 仅在测试确实需要真实的 Bukkit/Paper 行为（注册表、命令分发、事件等）时才引导 MockBukkit
4. 不需要真实 Bukkit 环境的逻辑优先使用纯 Mockito 进行模拟
5. 使用 AssertJ 的流畅断言风格
