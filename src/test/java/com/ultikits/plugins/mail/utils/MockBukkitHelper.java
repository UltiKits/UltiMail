/**
 * Synced from UltiTools-API v6.2.0
 * Source: UltiEssentials test utilities
 * Migrated onto org.mockbukkit.mockbukkit:mockbukkit-v1.21 during phase 14 (14-10).
 */
package com.ultikits.plugins.mail.utils;

import java.lang.reflect.Field;

import org.bukkit.Bukkit;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * MockBukkit 测试工具类
 * 提供健壮的 MockBukkit 清理功能，解决测试之间的单例冲突问题
 */
public final class MockBukkitHelper {

    private MockBukkitHelper() {
        // 工具类不允许实例化
    }

    /**
     * The module's single, shared test-time live-server bootstrap.
     * <p>
     * Every test that needs a real MockBukkit-backed {@code Server} — including the reopen-guard
     * sentinel ({@code UltiMailRegistrySentinelTest}) — must call this method rather than invoking
     * {@link MockBukkit#mock()} directly. Centralizing the call here is what lets the sentinel
     * actually detect a regression: if this method is ever changed to skip {@link MockBukkit#mock()}
     * (e.g. rewritten to install a bare Mockito {@code Server} mock instead), every caller —
     * including the sentinel — observes the same broken bootstrap, rather than the sentinel silently
     * continuing to pass on a live server it stood up independently.
     *
     * @return the live {@link ServerMock} instance, for callers that need to add players/plugins
     */
    public static ServerMock bootstrapServer() {
        ensureCleanState();
        ServerMock server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        return server;
    }

    /**
     * 安全地清理 MockBukkit 和 Bukkit 的单例状态
     * 在每个测试的 @BeforeEach 开始时调用
     */
    public static void ensureCleanState() {
        // 1. 尝试标准的 MockBukkit.unmock()
        try {
            if (MockBukkit.isMocked()) {
                MockBukkit.unmock();
            }
        } catch (Exception ignored) {
        }

        // 2. 强制清理 MockBukkit 的内部状态
        // NOTE: the 1.21 generation's internal singleton field is `mock` (a ServerMock
        // reference), not the legacy generation's `mocked` (a boolean) — confirmed via
        // javap against the real 4.101.0 jar. Reflecting on the old name would silently
        // clear nothing.
        try {
            Field mockField = MockBukkit.class.getDeclaredField("mock");
            mockField.setAccessible(true);
            mockField.set(null, null);
        } catch (Exception ignored) {
        }

        // 3. 强制清理 Bukkit 的 server 单例
        if (Bukkit.getServer() != null) {
            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                serverField.set(null, null);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 安全地卸载 MockBukkit
     * 在每个测试的 @AfterEach 结束时调用
     */
    public static void safeUnmock() {
        try {
            MockBukkit.unmock();
        } catch (Exception ignored) {
        }

        // 确保完全清理
        ensureCleanState();
    }
}
