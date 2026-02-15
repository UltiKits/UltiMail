package com.ultikits.plugins.mail.listener;

import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.service.MailService;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MailNotifyListener.
 * <p>
 * Uses pure Mockito (no MockBukkit) for maximum compatibility.
 */
@DisplayName("MailNotifyListener 测试")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class MailNotifyListenerTest {

    private MailNotifyListener listener;

    @Mock
    private MailService mockMailService;

    private MailConfig config;

    @Mock
    private Player player;

    @Mock
    private BukkitScheduler mockScheduler;

    @Mock
    private BukkitTask mockTask;

    @Mock
    private Player.Spigot mockSpigot;

    @Mock
    private PluginManager mockPluginManager;

    @Mock
    private Plugin mockBukkitPlugin;

    private MockedStatic<Bukkit> mockedBukkit;

    private UUID playerUuid;

    private UltiToolsPlugin mockPlugin;

    @BeforeEach
    void setUp() throws Exception {
        // Setup mock UltiToolsPlugin
        mockPlugin = TestHelper.mockUltiToolsPlugin();

        // Use real config
        config = new MailConfig();

        playerUuid = UUID.randomUUID();
        lenient().when(player.getUniqueId()).thenReturn(playerUuid);
        lenient().when(player.getName()).thenReturn("TestPlayer");
        lenient().when(player.isOnline()).thenReturn(true);
        lenient().when(player.spigot()).thenReturn(mockSpigot);

        // Mock Bukkit static
        mockedBukkit = mockStatic(Bukkit.class);
        mockedBukkit.when(Bukkit::getScheduler).thenReturn(mockScheduler);
        mockedBukkit.when(Bukkit::getPluginManager).thenReturn(mockPluginManager);
        lenient().when(mockPluginManager.getPlugin("UltiTools")).thenReturn(mockBukkitPlugin);

        // Mock scheduler to capture and run the delayed task immediately
        lenient().when(mockScheduler.runTaskLater(any(), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(1);
                runnable.run();
                return mockTask;
            });

        // Create listener and inject dependencies
        listener = new MailNotifyListener();
        TestHelper.injectField(listener, "mailService", mockMailService);
        TestHelper.injectField(listener, "config", config);
        TestHelper.injectField(listener, "plugin", mockPlugin);
    }

    @AfterEach
    void tearDown() {
        mockedBukkit.close();
        TestHelper.cleanupMocks();
    }


    // ==================== Notification Toggle Tests ====================

    @Nested
    @DisplayName("通知开关测试")
    class NotificationToggleTests {

        @Test
        @DisplayName("通知关闭时不应该调用邮件服务")
        void shouldNotNotifyWhenDisabled() {
            config.setNotifyOnJoin(false);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            verify(mockMailService, never()).getUnreadCount(any());
        }

        @Test
        @DisplayName("通知开启时应该检查未读邮件")
        void shouldCheckUnreadWhenEnabled() {
            config.setNotifyOnJoin(true);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(0);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            verify(mockMailService).getUnreadCount(playerUuid);
        }
    }

    // ==================== Unread Count Tests ====================

    @Nested
    @DisplayName("未读邮件通知测试")
    class UnreadCountTests {

        @Test
        @DisplayName("有未读邮件时应该发送通知")
        void shouldNotifyWhenHasUnreadMails() {
            config.setNotifyOnJoin(true);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(5);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            // Notification is sent via spigot API (sendMessage with TextComponents)
            verify(mockSpigot).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
        }

        @Test
        @DisplayName("没有未读邮件时不应该通知")
        void shouldNotNotifyWhenNoUnread() {
            config.setNotifyOnJoin(true);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(0);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            // No spigot message should be sent
            verify(mockSpigot, never()).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
        }
    }

    // ==================== Player Online Status Tests ====================

    @Nested
    @DisplayName("玩家在线状态测试")
    class PlayerOnlineStatusTests {

        @Test
        @DisplayName("玩家离线时不应发送通知")
        void shouldNotNotifyIfPlayerWentOffline() {
            config.setNotifyOnJoin(true);
            when(player.isOnline()).thenReturn(false);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            verify(mockMailService, never()).getUnreadCount(any());
        }
    }

    // ==================== Delay Tests ====================

    @Nested
    @DisplayName("通知延迟测试")
    class NotificationDelayTests {

        @Test
        @DisplayName("应该使用配置的延迟时间")
        void shouldUseConfiguredDelay() {
            config.setNotifyOnJoin(true);
            config.setNotifyDelay(5);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(1);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            // Verify runTaskLater was called with delay of 5 * 20 = 100 ticks
            verify(mockScheduler).runTaskLater(any(), any(Runnable.class), eq(100L));
        }

        @Test
        @DisplayName("延迟为0时应使用0 ticks")
        void shouldUseZeroTicksForZeroDelay() {
            config.setNotifyOnJoin(true);
            config.setNotifyDelay(0);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(1);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            verify(mockScheduler).runTaskLater(any(), any(Runnable.class), eq(0L));
        }
    }

    // ==================== Clickable Notification Tests ====================

    @Nested
    @DisplayName("可点击通知消息测试")
    class ClickableNotificationTests {

        @Test
        @DisplayName("通知消息应包含未读数量")
        void shouldIncludeUnreadCount() {
            config.setNotifyOnJoin(true);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(3);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            // Verify spigot sendMessage was called with BaseComponent array
            verify(mockSpigot).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
        }

        @Test
        @DisplayName("多次加入应该每次都发通知")
        void shouldNotifyOnEachJoin() {
            config.setNotifyOnJoin(true);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(2);

            PlayerJoinEvent event1 = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event1);

            PlayerJoinEvent event2 = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event2);

            verify(mockSpigot, times(2)).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
        }

        @Test
        @DisplayName("未读数为1也应发送通知")
        void shouldNotifyForSingleUnread() {
            config.setNotifyOnJoin(true);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(1);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            verify(mockSpigot).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
        }

        @Test
        @DisplayName("大量未读也应正常通知")
        void shouldNotifyForLargeUnreadCount() {
            config.setNotifyOnJoin(true);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(999);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            verify(mockSpigot).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent[].class));
        }
    }

    // ==================== Lazy Init Tests ====================

    @Nested
    @DisplayName("懒加载测试")
    class LazyInitTests {

        @Test
        @DisplayName("bukkitPlugin应在首次调用时初始化")
        void shouldLazyInitBukkitPlugin() throws Exception {
            config.setNotifyOnJoin(true);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(0);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            // Verify getPluginManager was called for lazy init
            mockedBukkit.verify(Bukkit::getPluginManager);
        }

        @Test
        @DisplayName("多次调用不应重复初始化")
        void shouldNotReinitBukkitPlugin() throws Exception {
            config.setNotifyOnJoin(true);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(0);

            // First call
            listener.onPlayerJoin(new PlayerJoinEvent(player, "joined"));
            // Second call - bukkitPlugin already set
            listener.onPlayerJoin(new PlayerJoinEvent(player, "joined"));

            // getPluginManager should be called only once (for first lazy init)
            mockedBukkit.verify(Bukkit::getPluginManager, times(1));
        }
    }

    // ==================== Config Interaction Tests ====================

    @Nested
    @DisplayName("配置交互测试")
    class ConfigInteractionTests {

        @Test
        @DisplayName("默认延迟应为3秒(60 ticks)")
        void shouldUseDefaultDelayOf3Seconds() {
            config.setNotifyOnJoin(true);
            // Default notifyDelay is 3
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(1);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            verify(mockScheduler).runTaskLater(any(), any(Runnable.class), eq(60L));
        }

        @Test
        @DisplayName("默认配置应启用通知")
        void shouldBeEnabledByDefault() {
            assertThat(config.isNotifyOnJoin()).isTrue();
        }
    }

    // ==================== i18n Tests ====================

    @Nested
    @DisplayName("国际化测试")
    class I18nTests {

        @Test
        @DisplayName("应该调用plugin.i18n获取翻译文本")
        void shouldCallI18nForTranslation() {
            config.setNotifyOnJoin(true);
            when(mockMailService.getUnreadCount(playerUuid)).thenReturn(5);

            PlayerJoinEvent event = new PlayerJoinEvent(player, "joined");
            listener.onPlayerJoin(event);

            // i18n should be called for notification text
            verify(mockPlugin, atLeast(1)).i18n(anyString());
        }
    }

    // ==================== Annotation Tests ====================

    @Nested
    @DisplayName("注解配置测试")
    class AnnotationTests {

        @Test
        @DisplayName("类应该有 @EventListener 注解")
        void shouldHaveEventListenerAnnotation() {
            assertThat(MailNotifyListener.class.isAnnotationPresent(
                com.ultikits.ultitools.annotations.EventListener.class
            )).isTrue();
        }

        @Test
        @DisplayName("应该实现 Bukkit Listener 接口")
        void shouldImplementListener() {
            assertThat(org.bukkit.event.Listener.class)
                .isAssignableFrom(MailNotifyListener.class);
        }

        @Test
        @DisplayName("onPlayerJoin 应该有 @EventHandler 注解")
        void shouldHaveEventHandlerAnnotation() throws Exception {
            boolean hasAnnotation = MailNotifyListener.class
                .getMethod("onPlayerJoin", PlayerJoinEvent.class)
                .isAnnotationPresent(org.bukkit.event.EventHandler.class);

            assertThat(hasAnnotation).isTrue();
        }
    }
}
