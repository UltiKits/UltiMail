package com.ultikits.plugins.mail.service;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MailService.
 * <p>
 * Uses pure Mockito (no MockBukkit) for maximum compatibility.
 */
@DisplayName("MailService 测试")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MailServiceTest {

    private MailService mailService;

    @Mock
    private DataOperator<MailData> mockDataOperator;

    @Mock
    private Query<MailData> mockQueryBuilder;

    private MailConfig config;

    @Mock
    private Player sender;

    @Mock
    private Player receiver;

    @Mock
    private PlayerInventory senderInventory;

    @Mock
    private PlayerInventory receiverInventory;

    private MockedStatic<Bukkit> mockedBukkit;

    private UUID senderUuid;
    private UUID receiverUuid;

    @BeforeEach
    void setUp() throws Exception {
        // Setup mock UltiToolsPlugin
        UltiToolsPlugin mockPlugin = TestHelper.mockUltiToolsPlugin();
        when(mockPlugin.getDataOperator(MailData.class)).thenReturn(mockDataOperator);

        // Setup Query DSL chain (lenient because not all nested test classes use queries)
        lenient().when(mockDataOperator.query()).thenReturn(mockQueryBuilder);
        lenient().when(mockQueryBuilder.where(anyString())).thenReturn(mockQueryBuilder);
        lenient().when(mockQueryBuilder.eq(any())).thenReturn(mockQueryBuilder);

        // Use real MailConfig with default values
        config = new MailConfig();

        // Setup UUIDs
        senderUuid = UUID.randomUUID();
        receiverUuid = UUID.randomUUID();

        // Setup sender mock
        lenient().when(sender.getUniqueId()).thenReturn(senderUuid);
        lenient().when(sender.getName()).thenReturn("SenderPlayer");
        lenient().when(sender.getInventory()).thenReturn(senderInventory);
        lenient().when(sender.isOnline()).thenReturn(true);

        // Setup receiver mock
        lenient().when(receiver.getUniqueId()).thenReturn(receiverUuid);
        lenient().when(receiver.getName()).thenReturn("ReceiverPlayer");
        lenient().when(receiver.getInventory()).thenReturn(receiverInventory);
        lenient().when(receiver.isOnline()).thenReturn(true);

        // Mock Bukkit static methods
        mockedBukkit = mockStatic(Bukkit.class);
        mockedBukkit.when(() -> Bukkit.getPlayerExact("ReceiverPlayer")).thenReturn(receiver);
        mockedBukkit.when(() -> Bukkit.getPlayerExact("SenderPlayer")).thenReturn(sender);

        // Create service and inject dependencies
        mailService = new MailService();
        injectField(mailService, "config", config);
        injectField(mailService, "dataOperator", mockDataOperator);
        injectField(mailService, "plugin", mockPlugin);
    }

    @AfterEach
    void tearDown() {
        mockedBukkit.close();
        TestHelper.cleanupMocks();
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Long> getCooldownMap() throws Exception {
        Field field = MailService.class.getDeclaredField("sendCooldowns");
        field.setAccessible(true);
        return (Map<UUID, Long>) field.get(mailService);
    }

    // ==================== sendMail Tests ====================

    @Nested
    @DisplayName("sendMail 方法测试")
    class SendMailTests {

        @Test
        @DisplayName("应该成功发送纯文本邮件")
        void shouldSendTextMail() {
            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "测试标题", "测试内容", null);

            assertThat(result).isTrue();
            verify(mockDataOperator).insert(any(MailData.class));
        }

        @Test
        @DisplayName("标题过长时应该返回 false")
        void shouldRejectLongSubject() {
            String longSubject = String.join("", Collections.nCopies(100, "a"));

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", longSubject, "内容", null);

            assertThat(result).isFalse();
            verify(mockDataOperator, never()).insert(any());
        }

        @Test
        @DisplayName("内容过长时应该返回 false")
        void shouldRejectLongContent() {
            String longContent = String.join("", Collections.nCopies(1000, "a"));

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "标题", longContent, null);

            assertThat(result).isFalse();
            verify(mockDataOperator, never()).insert(any());
        }

        @Test
        @DisplayName("接收者不存在时应该返回 false")
        void shouldRejectNonExistentReceiver() {
            // Player not found online
            mockedBukkit.when(() -> Bukkit.getPlayerExact("nonexistent")).thenReturn(null);
            // Player not found offline
            OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
            when(offlinePlayer.hasPlayedBefore()).thenReturn(false);
            when(offlinePlayer.isOnline()).thenReturn(false);
            mockedBukkit.when(() -> Bukkit.getOfflinePlayer("nonexistent")).thenReturn(offlinePlayer);

            boolean result = mailService.sendMail(sender, "nonexistent", "标题", "内容", null);

            assertThat(result).isFalse();
            verify(mockDataOperator, never()).insert(any());
        }

        @Test
        @DisplayName("应该成功发送带命令的邮件")
        void shouldSendMailWithCommands() {
            List<String> commands = Arrays.asList("give %player% diamond 1", "console:eco give %player% 100");

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null, commands);

            assertThat(result).isTrue();
            verify(mockDataOperator).insert(argThat(mail ->
                mail.getCommands() != null && mail.getCommands().contains("diamond")
            ));
        }

        @Test
        @DisplayName("冷却期间应该拒绝发送")
        void shouldRejectWhenOnCooldown() throws Exception {
            // Send first mail to set cooldown
            boolean first = mailService.sendMail(sender, "ReceiverPlayer", "标题1", "内容1", null);
            assertThat(first).isTrue();

            // Second should be on cooldown
            boolean second = mailService.sendMail(sender, "ReceiverPlayer", "标题2", "内容2", null);
            assertThat(second).isFalse();
        }

        @Test
        @DisplayName("冷却过期后应该允许发送")
        void shouldAllowSendAfterCooldownExpires() throws Exception {
            // Set cooldown far in the past
            getCooldownMap().put(senderUuid, System.currentTimeMillis() - 60000L);

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("发送成功后应该通知在线接收者")
        void shouldNotifyOnlineReceiver() {
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            verify(receiver).sendMessage(any(String.class));
        }

        @Test
        @DisplayName("接收者离线时不应发送通知")
        void shouldNotNotifyOfflineReceiver() {
            // Make receiver offline
            mockedBukkit.when(() -> Bukkit.getPlayerExact("ReceiverPlayer")).thenReturn(null)
                .thenReturn(null);
            // But they exist as offline player for UUID lookup
            OfflinePlayer offlineReceiver = mock(OfflinePlayer.class);
            when(offlineReceiver.hasPlayedBefore()).thenReturn(true);
            when(offlineReceiver.getUniqueId()).thenReturn(receiverUuid);
            mockedBukkit.when(() -> Bukkit.getOfflinePlayer("ReceiverPlayer")).thenReturn(offlineReceiver);

            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            // receiver.sendMessage should not be called since receiver is not online
            verify(receiver, never()).sendMessage(any(String.class));
        }

        @Test
        @DisplayName("通知消息应包含发送者名字")
        void shouldIncludeSenderNameInNotification() {
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            verify(receiver).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("SenderPlayer")
            ));
        }

        @Test
        @DisplayName("发送成功后应该设置冷却")
        void shouldSetCooldownAfterSending() throws Exception {
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            Map<UUID, Long> cooldowns = getCooldownMap();
            assertThat(cooldowns).containsKey(senderUuid);
        }

        @Test
        @DisplayName("空物品数组应该被忽略")
        void shouldIgnoreEmptyItemsArray() {
            ItemStack[] items = new ItemStack[0];

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", items);

            assertThat(result).isTrue();
            verify(mockDataOperator).insert(argThat(mail ->
                mail.getItems() == null
            ));
        }

        @Test
        @DisplayName("全AIR物品数组应该被忽略")
        void shouldIgnoreAllAirItems() {
            ItemStack airItem = mock(ItemStack.class);
            when(airItem.getType()).thenReturn(Material.AIR);
            ItemStack[] items = new ItemStack[]{airItem};

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", items);

            assertThat(result).isTrue();
            // Mail should be inserted without items
            verify(mockDataOperator).insert(any(MailData.class));
        }

        @Test
        @DisplayName("null项在物品数组中应该被过滤")
        void shouldFilterNullItems() {
            ItemStack[] items = new ItemStack[]{null, null};

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", items);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("空命令列表不应设置commands字段")
        void shouldNotSetCommandsWhenEmpty() {
            List<String> commands = Collections.emptyList();

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null, commands);

            assertThat(result).isTrue();
            verify(mockDataOperator).insert(argThat(mail ->
                mail.getCommands() == null
            ));
        }

        @Test
        @DisplayName("邮件数据应包含正确的发送者信息")
        void shouldContainCorrectSenderInfo() {
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            verify(mockDataOperator).insert(argThat(mail ->
                mail.getSenderUuid().equals(senderUuid.toString()) &&
                mail.getSenderName().equals("SenderPlayer")
            ));
        }

        @Test
        @DisplayName("邮件数据应包含正确的接收者信息")
        void shouldContainCorrectReceiverInfo() {
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            verify(mockDataOperator).insert(argThat(mail ->
                mail.getReceiverUuid().equals(receiverUuid.toString()) &&
                mail.getReceiverName().equals("ReceiverPlayer")
            ));
        }

        @Test
        @DisplayName("邮件数据应包含正确的主题和内容")
        void shouldContainCorrectSubjectAndContent() {
            mailService.sendMail(sender, "ReceiverPlayer", "测试标题", "测试内容", null);

            verify(mockDataOperator).insert(argThat(mail ->
                "测试标题".equals(mail.getSubject()) &&
                "测试内容".equals(mail.getContent())
            ));
        }

        @Test
        @DisplayName("邮件数据应设置发送时间")
        void shouldSetSentTime() {
            long before = System.currentTimeMillis();
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            verify(mockDataOperator).insert(argThat(mail ->
                mail.getSentTime() >= before && mail.getSentTime() <= System.currentTimeMillis()
            ));
        }

        @Test
        @DisplayName("刚好在长度限制内的标题应该被接受")
        void shouldAcceptSubjectAtMaxLength() {
            String exactLengthSubject = String.join("", Collections.nCopies(50, "a"));

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", exactLengthSubject, "内容", null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("刚好在长度限制内的内容应该被接受")
        void shouldAcceptContentAtMaxLength() {
            String exactLengthContent = String.join("", Collections.nCopies(500, "a"));

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "标题", exactLengthContent, null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("通过离线玩家UUID查找接收者")
        void shouldFindReceiverByOfflineUuid() {
            // Clear online lookup
            mockedBukkit.when(() -> Bukkit.getPlayerExact("OfflineGuy")).thenReturn(null);
            OfflinePlayer offlineReceiver = mock(OfflinePlayer.class);
            when(offlineReceiver.hasPlayedBefore()).thenReturn(true);
            when(offlineReceiver.getUniqueId()).thenReturn(receiverUuid);
            mockedBukkit.when(() -> Bukkit.getOfflinePlayer("OfflineGuy")).thenReturn(offlineReceiver);

            boolean result = mailService.sendMail(sender, "OfflineGuy", "标题", "内容", null);

            assertThat(result).isTrue();
        }
    }

    // ==================== getInbox Tests ====================

    @Nested
    @DisplayName("getInbox 方法测试")
    class GetInboxTests {

        @Test
        @DisplayName("应该返回玩家的收件箱")
        void shouldReturnInbox() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer"));
            mails.add(createTestMail("s2", "sender2", receiverUuid.toString(), "ReceiverPlayer"));
            when(mockQueryBuilder.list()).thenReturn(mails);

            List<MailData> result = mailService.getInbox(receiverUuid);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("应该过滤掉已删除的邮件")
        void shouldFilterDeletedMails() {
            List<MailData> mails = new ArrayList<>();
            MailData mail1 = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            MailData mail2 = createTestMail("s2", "sender2", receiverUuid.toString(), "ReceiverPlayer");
            mail2.setDeletedByReceiver(true);
            mails.add(mail1);
            mails.add(mail2);
            when(mockQueryBuilder.list()).thenReturn(mails);

            List<MailData> result = mailService.getInbox(receiverUuid);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("应该按时间降序排序")
        void shouldSortByTimeDescending() {
            List<MailData> mails = new ArrayList<>();
            MailData mail1 = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail1.setSentTime(1000L);
            MailData mail2 = createTestMail("s2", "sender2", receiverUuid.toString(), "ReceiverPlayer");
            mail2.setSentTime(2000L);
            mails.add(mail1);
            mails.add(mail2);
            when(mockQueryBuilder.list()).thenReturn(mails);

            List<MailData> result = mailService.getInbox(receiverUuid);

            assertThat(result.get(0).getSentTime()).isGreaterThan(result.get(1).getSentTime());
        }

        @Test
        @DisplayName("空收件箱应该返回空列表")
        void shouldReturnEmptyForEmptyInbox() {
            when(mockQueryBuilder.list()).thenReturn(new ArrayList<>());

            List<MailData> result = mailService.getInbox(receiverUuid);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("所有邮件都被删除时应返回空列表")
        void shouldReturnEmptyWhenAllDeleted() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setDeletedByReceiver(true);
            mails.add(mail);
            when(mockQueryBuilder.list()).thenReturn(mails);

            List<MailData> result = mailService.getInbox(receiverUuid);

            assertThat(result).isEmpty();
        }
    }

    // ==================== getSentMails Tests ====================

    @Nested
    @DisplayName("getSentMails 方法测试")
    class GetSentMailsTests {

        @Test
        @DisplayName("应该返回玩家的发件箱")
        void shouldReturnSentMails() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail(senderUuid.toString(), "SenderPlayer", "r1", "receiver1"));
            when(mockQueryBuilder.list()).thenReturn(mails);

            List<MailData> result = mailService.getSentMails(senderUuid);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("应该过滤掉发件人已删除的邮件")
        void shouldFilterDeletedBySender() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail(senderUuid.toString(), "SenderPlayer", "r1", "receiver1");
            mail.setDeletedBySender(true);
            mails.add(mail);
            when(mockQueryBuilder.list()).thenReturn(mails);

            List<MailData> result = mailService.getSentMails(senderUuid);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该按时间降序排序")
        void shouldSortSentMailsByTimeDescending() {
            List<MailData> mails = new ArrayList<>();
            MailData mail1 = createTestMail(senderUuid.toString(), "SenderPlayer", "r1", "receiver1");
            mail1.setSentTime(1000L);
            MailData mail2 = createTestMail(senderUuid.toString(), "SenderPlayer", "r2", "receiver2");
            mail2.setSentTime(3000L);
            mails.add(mail1);
            mails.add(mail2);
            when(mockQueryBuilder.list()).thenReturn(mails);

            List<MailData> result = mailService.getSentMails(senderUuid);

            assertThat(result.get(0).getSentTime()).isGreaterThan(result.get(1).getSentTime());
        }
    }

    // ==================== getUnreadCount Tests ====================

    @Nested
    @DisplayName("getUnreadCount 方法测试")
    class GetUnreadCountTests {

        @Test
        @DisplayName("应该返回正确的未读邮件数")
        void shouldReturnUnreadCount() {
            List<MailData> mails = new ArrayList<>();
            MailData mail1 = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail1.setRead(false);
            MailData mail2 = createTestMail("s2", "sender2", receiverUuid.toString(), "ReceiverPlayer");
            mail2.setRead(true);
            MailData mail3 = createTestMail("s3", "sender3", receiverUuid.toString(), "ReceiverPlayer");
            mail3.setRead(false);
            mails.add(mail1);
            mails.add(mail2);
            mails.add(mail3);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.getUnreadCount(receiverUuid);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("没有未读邮件时应返回 0")
        void shouldReturnZeroWhenNoUnread() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setRead(true);
            mails.add(mail);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.getUnreadCount(receiverUuid);

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("空收件箱应返回 0")
        void shouldReturnZeroForEmptyInbox() {
            when(mockQueryBuilder.list()).thenReturn(new ArrayList<>());

            int count = mailService.getUnreadCount(receiverUuid);

            assertThat(count).isEqualTo(0);
        }
    }

    // ==================== markAsRead Tests ====================

    @Nested
    @DisplayName("markAsRead 方法测试")
    class MarkAsReadTests {

        @Test
        @DisplayName("应该将邮件标记为已读")
        void shouldMarkMailAsRead() throws Exception {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            assertThat(mail.isRead()).isFalse();

            mailService.markAsRead(mail);

            assertThat(mail.isRead()).isTrue();
            verify(mockDataOperator).update(mail);
        }

        @Test
        @DisplayName("update失败时应该记录错误")
        void shouldLogErrorOnUpdateFailure() throws Exception {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            doThrow(new IllegalAccessException("test error")).when(mockDataOperator).update(mail);

            // Should not throw exception
            mailService.markAsRead(mail);

            assertThat(mail.isRead()).isTrue();
        }
    }

    // ==================== claimItems Tests ====================

    @Nested
    @DisplayName("claimItems 方法测试")
    class ClaimItemsTests {

        @Test
        @DisplayName("已领取的邮件应返回空数组")
        void shouldReturnEmptyForClaimedMail() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setClaimed(true);

            ItemStack[] result = mailService.claimItems(mail, receiver);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("没有附件的邮件应返回空数组")
        void shouldReturnEmptyForNoItems() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setItems(null);

            ItemStack[] result = mailService.claimItems(mail, receiver);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("空字符串附件应返回空数组")
        void shouldReturnEmptyForEmptyStringItems() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setItems("");

            ItemStack[] result = mailService.claimItems(mail, receiver);

            assertThat(result).isEmpty();
        }
    }

    // ==================== getItemCount Tests ====================

    @Nested
    @DisplayName("getItemCount 方法测试")
    class GetItemCountTests {

        @Test
        @DisplayName("没有附件时应返回 0")
        void shouldReturnZeroForNoItems() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setItems(null);

            int count = mailService.getItemCount(mail);

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("空字符串时应返回 0")
        void shouldReturnZeroForEmptyString() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setItems("");

            int count = mailService.getItemCount(mail);

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("无效的Base64数据应返回 0")
        void shouldReturnZeroForInvalidBase64() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setItems("not-valid-base64");

            int count = mailService.getItemCount(mail);

            assertThat(count).isEqualTo(0);
        }
    }

    // ==================== executeMailCommands Tests ====================

    @Nested
    @DisplayName("executeMailCommands 方法测试")
    class ExecuteMailCommandsTests {

        @Test
        @DisplayName("无命令时不应执行任何操作")
        void shouldDoNothingWhenNoCommands() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands(null);

            mailService.executeMailCommands(receiver, mail);

            // No interactions expected
            verify(receiver, never()).performCommand(anyString());
        }

        @Test
        @DisplayName("命令已执行时不应重复执行")
        void shouldNotExecuteAlreadyExecutedCommands() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("[\"give %player% diamond 1\"]");
            mail.setCommandsExecuted(true);

            mailService.executeMailCommands(receiver, mail);

            verify(receiver, never()).performCommand(anyString());
        }

        @Test
        @DisplayName("应该执行普通命令作为玩家")
        void shouldExecutePlayerCommand() throws Exception {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("[\"give ReceiverPlayer diamond 1\"]");

            mailService.executeMailCommands(receiver, mail);

            verify(receiver).performCommand("give ReceiverPlayer diamond 1");
            assertThat(mail.isCommandsExecuted()).isTrue();
        }

        @Test
        @DisplayName("应该执行console:前缀命令作为控制台")
        void shouldExecuteConsoleCommand() throws Exception {
            ConsoleCommandSender consoleSender = mock(ConsoleCommandSender.class);
            mockedBukkit.when(Bukkit::getConsoleSender).thenReturn(consoleSender);
            mockedBukkit.when(() -> Bukkit.dispatchCommand(eq(consoleSender), anyString())).thenReturn(true);

            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("[\"console:eco give ReceiverPlayer 100\"]");

            mailService.executeMailCommands(receiver, mail);

            mockedBukkit.verify(() -> Bukkit.dispatchCommand(eq(consoleSender), eq("eco give ReceiverPlayer 100")));
            assertThat(mail.isCommandsExecuted()).isTrue();
        }

        @Test
        @DisplayName("应该替换%player%占位符")
        void shouldReplacePlayerPlaceholder() throws Exception {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("[\"give %player% diamond 1\"]");

            mailService.executeMailCommands(receiver, mail);

            verify(receiver).performCommand("give ReceiverPlayer diamond 1");
        }

        @Test
        @DisplayName("执行后应标记为已执行")
        void shouldMarkCommandsAsExecuted() throws Exception {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("[\"test\"]");

            mailService.executeMailCommands(receiver, mail);

            assertThat(mail.isCommandsExecuted()).isTrue();
            verify(mockDataOperator).update(mail);
        }

        @Test
        @DisplayName("空命令列表不应执行")
        void shouldNotExecuteEmptyCommandList() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("[]");

            mailService.executeMailCommands(receiver, mail);

            verify(receiver, never()).performCommand(anyString());
        }

        @Test
        @DisplayName("应该执行多个命令")
        void shouldExecuteMultipleCommands() throws Exception {
            ConsoleCommandSender consoleSender = mock(ConsoleCommandSender.class);
            mockedBukkit.when(Bukkit::getConsoleSender).thenReturn(consoleSender);
            mockedBukkit.when(() -> Bukkit.dispatchCommand(any(), anyString())).thenReturn(true);

            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("[\"give ReceiverPlayer diamond 1\",\"console:eco give ReceiverPlayer 100\"]");

            mailService.executeMailCommands(receiver, mail);

            verify(receiver).performCommand("give ReceiverPlayer diamond 1");
            mockedBukkit.verify(() -> Bukkit.dispatchCommand(eq(consoleSender), eq("eco give ReceiverPlayer 100")));
        }
    }

    // ==================== deleteMail Tests ====================

    @Nested
    @DisplayName("deleteMail 方法测试")
    class DeleteMailTests {

        @Test
        @DisplayName("接收者删除应标记 deletedByReceiver")
        void shouldMarkDeletedByReceiver() throws Exception {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");

            mailService.deleteMail(mail, receiverUuid);

            assertThat(mail.isDeletedByReceiver()).isTrue();
            verify(mockDataOperator).update(mail);
        }

        @Test
        @DisplayName("发件者删除应标记 deletedBySender")
        void shouldMarkDeletedBySender() throws Exception {
            MailData mail = createTestMail(senderUuid.toString(), "SenderPlayer", "r1", "receiver1");

            mailService.deleteMail(mail, senderUuid);

            assertThat(mail.isDeletedBySender()).isTrue();
            verify(mockDataOperator).update(mail);
        }

        @Test
        @DisplayName("双方都删除时应真正删除")
        void shouldReallyDeleteWhenBothDeleted() {
            MailData mail = createTestMail(senderUuid.toString(), "SenderPlayer",
                receiverUuid.toString(), "ReceiverPlayer");
            mail.setDeletedBySender(true);
            mail.setId("123");

            mailService.deleteMail(mail, receiverUuid);

            verify(mockDataOperator).delById("123");
        }

        @Test
        @DisplayName("无关玩家删除不应改变状态")
        void shouldNotChangeStateForUnrelatedPlayer() throws Exception {
            UUID unrelatedUuid = UUID.randomUUID();
            MailData mail = createTestMail(senderUuid.toString(), "SenderPlayer",
                receiverUuid.toString(), "ReceiverPlayer");

            mailService.deleteMail(mail, unrelatedUuid);

            assertThat(mail.isDeletedBySender()).isFalse();
            assertThat(mail.isDeletedByReceiver()).isFalse();
        }
    }

    // ==================== deleteAllByReceiver Tests ====================

    @Nested
    @DisplayName("deleteAllByReceiver 方法测试")
    class DeleteAllByReceiverTests {

        @Test
        @DisplayName("应该删除所有无附件的邮件")
        void shouldDeleteMailsWithoutItems() throws Exception {
            List<MailData> mails = new ArrayList<>();
            MailData mail1 = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            MailData mail2 = createTestMail("s2", "sender2", receiverUuid.toString(), "ReceiverPlayer");
            mails.add(mail1);
            mails.add(mail2);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.deleteAllByReceiver(receiverUuid);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("应该跳过有未领取附件的邮件")
        void shouldSkipMailsWithUnclaimedItems() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setItems("someItems");
            mail.setClaimed(false);
            mails.add(mail);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.deleteAllByReceiver(receiverUuid);

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("已领取附件的邮件应该被删除")
        void shouldDeleteClaimedItemsMails() throws Exception {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setItems("someItems");
            mail.setClaimed(true);
            mails.add(mail);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.deleteAllByReceiver(receiverUuid);

            assertThat(count).isEqualTo(1);
        }
    }

    // ==================== deleteReadByReceiver Tests ====================

    @Nested
    @DisplayName("deleteReadByReceiver 方法测试")
    class DeleteReadByReceiverTests {

        @Test
        @DisplayName("应该只删除已读邮件")
        void shouldDeleteOnlyReadMails() throws Exception {
            List<MailData> mails = new ArrayList<>();
            MailData mail1 = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail1.setRead(true);
            MailData mail2 = createTestMail("s2", "sender2", receiverUuid.toString(), "ReceiverPlayer");
            mail2.setRead(false);
            mails.add(mail1);
            mails.add(mail2);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.deleteReadByReceiver(receiverUuid);

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("已读但有未领取附件的邮件不应删除")
        void shouldNotDeleteReadMailsWithUnclaimedItems() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setRead(true);
            mail.setItems("someItems");
            mail.setClaimed(false);
            mails.add(mail);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.deleteReadByReceiver(receiverUuid);

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("已读且已领取附件的邮件应删除")
        void shouldDeleteReadAndClaimedMails() throws Exception {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setRead(true);
            mail.setItems("someItems");
            mail.setClaimed(true);
            mails.add(mail);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.deleteReadByReceiver(receiverUuid);

            assertThat(count).isEqualTo(1);
        }
    }

    // ==================== getMail Tests ====================

    @Nested
    @DisplayName("getMail 方法测试")
    class GetMailTests {

        @Test
        @DisplayName("应该通过ID获取邮件")
        void shouldGetMailById() {
            MailData expectedMail = createTestMail("s1", "sender1", "r1", "receiver1");
            when(mockDataOperator.getById("123")).thenReturn(expectedMail);

            MailData result = mailService.getMail("123");

            assertThat(result).isEqualTo(expectedMail);
        }

        @Test
        @DisplayName("不存在的ID应返回 null")
        void shouldReturnNullForNonExistentId() {
            when(mockDataOperator.getById("999")).thenReturn(null);

            MailData result = mailService.getMail("999");

            assertThat(result).isNull();
        }
    }

    // ==================== sendMailInternal Tests ====================

    @Nested
    @DisplayName("sendMailInternal 方法测试")
    class SendMailInternalTests {

        @Test
        @DisplayName("应该成功发送内部邮件")
        void shouldSendInternalMail() {
            boolean result = mailService.sendMailInternal(
                senderUuid, "SenderPlayer", "ReceiverPlayer", "标题", "内容", null);

            assertThat(result).isTrue();
            verify(mockDataOperator).insert(any(MailData.class));
        }

        @Test
        @DisplayName("接收者不存在时应返回 false")
        void shouldReturnFalseWhenReceiverNotFound() {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("nonexistent")).thenReturn(null);
            OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
            when(offlinePlayer.hasPlayedBefore()).thenReturn(false);
            when(offlinePlayer.isOnline()).thenReturn(false);
            mockedBukkit.when(() -> Bukkit.getOfflinePlayer("nonexistent")).thenReturn(offlinePlayer);

            boolean result = mailService.sendMailInternal(
                senderUuid, "SenderPlayer", "nonexistent", "标题", "内容", null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("应该支持 null UUID (系统邮件)")
        void shouldSupportNullUuidForSystemMail() {
            boolean result = mailService.sendMailInternal(
                null, "System", "ReceiverPlayer", "系统通知", "内容", null);

            assertThat(result).isTrue();
            verify(mockDataOperator).insert(argThat(mail ->
                mail.getSenderUuid() == null
            ));
        }
    }

    // ==================== getConfig Tests ====================

    @Nested
    @DisplayName("getConfig 方法测试")
    class GetConfigTests {

        @Test
        @DisplayName("应该返回注入的配置")
        void shouldReturnInjectedConfig() {
            MailConfig result = mailService.getConfig();

            assertThat(result).isSameAs(config);
        }
    }

    // ==================== sendMail edge cases ====================

    @Nested
    @DisplayName("sendMail 边界条件测试")
    class SendMailEdgeCaseTests {

        @Test
        @DisplayName("冷却时间为0时应总是允许发送")
        void shouldAlwaysAllowWhenCooldownZero() throws Exception {
            config.setSendCooldown(0);
            // Send first
            boolean first = mailService.sendMail(sender, "ReceiverPlayer", "标题1", "内容1", null);
            assertThat(first).isTrue();
            // Send second immediately
            boolean second = mailService.sendMail(sender, "ReceiverPlayer", "标题2", "内容2", null);
            assertThat(second).isTrue();
        }

        @Test
        @DisplayName("冷却时应发送冷却消息给玩家")
        void shouldSendCooldownMessageToPlayer() throws Exception {
            mailService.sendMail(sender, "ReceiverPlayer", "标题1", "内容1", null);
            mailService.sendMail(sender, "ReceiverPlayer", "标题2", "内容2", null);

            // Second send should show cooldown msg
            verify(sender, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[send_cooldown]")
            ));
        }

        @Test
        @DisplayName("标题超出限制时应发送长度消息给玩家")
        void shouldSendSubjectTooLongMessage() {
            String longSubject = String.join("", Collections.nCopies(100, "a"));
            mailService.sendMail(sender, "ReceiverPlayer", longSubject, "内容", null);

            verify(sender).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[send_subject_too_long]")
            ));
        }

        @Test
        @DisplayName("内容超出限制时应发送长度消息给玩家")
        void shouldSendContentTooLongMessage() {
            String longContent = String.join("", Collections.nCopies(1000, "a"));
            mailService.sendMail(sender, "ReceiverPlayer", "标题", longContent, null);

            verify(sender).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[send_content_too_long]")
            ));
        }

        @Test
        @DisplayName("接收者不存在时应发送未找到消息给玩家")
        void shouldSendPlayerNotFoundMessage() {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("ghost")).thenReturn(null);
            OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
            when(offlinePlayer.hasPlayedBefore()).thenReturn(false);
            when(offlinePlayer.isOnline()).thenReturn(false);
            mockedBukkit.when(() -> Bukkit.getOfflinePlayer("ghost")).thenReturn(offlinePlayer);

            mailService.sendMail(sender, "ghost", "标题", "内容", null);

            verify(sender).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[send_player_not_found]")
            ));
        }

        @Test
        @DisplayName("新建邮件应默认未读")
        void shouldDefaultToUnread() {
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            verify(mockDataOperator).insert(argThat(mail ->
                !mail.isRead()
            ));
        }

        @Test
        @DisplayName("新建邮件应默认未领取")
        void shouldDefaultToUnclaimed() {
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            verify(mockDataOperator).insert(argThat(mail ->
                !mail.isClaimed()
            ));
        }

        @Test
        @DisplayName("新建邮件应默认未删除")
        void shouldDefaultToNotDeleted() {
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            verify(mockDataOperator).insert(argThat(mail ->
                !mail.isDeletedBySender() && !mail.isDeletedByReceiver()
            ));
        }

        @Test
        @DisplayName("新建邮件命令未执行标志应为false")
        void shouldDefaultCommandsNotExecuted() {
            List<String> commands = Arrays.asList("give %player% diamond 1");
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null, commands);

            verify(mockDataOperator).insert(argThat(mail ->
                !mail.isCommandsExecuted()
            ));
        }

        @Test
        @DisplayName("通知消息应包含配置的模板内容")
        void shouldUseConfiguredNotificationTemplate() {
            config.setMailReceivedMessage("&a{SENDER} 给你发了邮件");
            mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", null);

            verify(receiver).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("SenderPlayer") && msg.contains("给你发了邮件")
            ));
        }

        @Test
        @DisplayName("通过isOnline的离线玩家也应查找到")
        void shouldFindReceiverViaIsOnline() {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("OnlineCheck")).thenReturn(null);
            OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
            when(offlinePlayer.hasPlayedBefore()).thenReturn(false);
            when(offlinePlayer.isOnline()).thenReturn(true);
            when(offlinePlayer.getUniqueId()).thenReturn(receiverUuid);
            mockedBukkit.when(() -> Bukkit.getOfflinePlayer("OnlineCheck")).thenReturn(offlinePlayer);

            boolean result = mailService.sendMail(sender, "OnlineCheck", "标题", "内容", null);

            assertThat(result).isTrue();
        }
    }

    // ==================== sendMailInternal edge cases ====================

    @Nested
    @DisplayName("sendMailInternal 边界测试")
    class SendMailInternalEdgeCaseTests {

        @Test
        @DisplayName("内部邮件不应受冷却限制")
        void shouldNotBeCooldownLimited() throws Exception {
            getCooldownMap().put(senderUuid, System.currentTimeMillis());

            boolean result = mailService.sendMailInternal(
                senderUuid, "SenderPlayer", "ReceiverPlayer", "标题", "内容", null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("内部邮件应通知在线接收者")
        void shouldNotifyOnlineReceiverForInternalMail() {
            mailService.sendMailInternal(
                senderUuid, "SenderPlayer", "ReceiverPlayer", "标题", "内容", null);

            verify(receiver).sendMessage(any(String.class));
        }

        @Test
        @DisplayName("内部邮件数据应包含正确的发送者名")
        void shouldContainCorrectSenderName() {
            mailService.sendMailInternal(
                senderUuid, "CustomSender", "ReceiverPlayer", "标题", "内容", null);

            verify(mockDataOperator).insert(argThat(mail ->
                "CustomSender".equals(mail.getSenderName())
            ));
        }

        @Test
        @DisplayName("内部邮件数据应包含正确的接收者UUID")
        void shouldContainCorrectReceiverUuid() {
            mailService.sendMailInternal(
                senderUuid, "SenderPlayer", "ReceiverPlayer", "标题", "内容", null);

            verify(mockDataOperator).insert(argThat(mail ->
                receiverUuid.toString().equals(mail.getReceiverUuid())
            ));
        }
    }

    // ==================== deleteMail edge cases ====================

    @Nested
    @DisplayName("deleteMail 边界测试")
    class DeleteMailEdgeCaseTests {

        @Test
        @DisplayName("发件人先删后接收者删应真正删除")
        void shouldReallyDeleteWhenSenderDeletedFirst() {
            MailData mail = createTestMail(senderUuid.toString(), "SenderPlayer",
                receiverUuid.toString(), "ReceiverPlayer");
            mail.setDeletedBySender(true);
            mail.setId("456");

            mailService.deleteMail(mail, receiverUuid);

            verify(mockDataOperator).delById("456");
        }

        @Test
        @DisplayName("接收者先删后发件人删应真正删除")
        void shouldReallyDeleteWhenReceiverDeletedFirst() {
            MailData mail = createTestMail(senderUuid.toString(), "SenderPlayer",
                receiverUuid.toString(), "ReceiverPlayer");
            mail.setDeletedByReceiver(true);
            mail.setId("789");

            mailService.deleteMail(mail, senderUuid);

            verify(mockDataOperator).delById("789");
        }

        @Test
        @DisplayName("用户同时是发件人和接收人时删除应同时设置两个标记")
        void shouldSetBothFlagsWhenSameUser() {
            MailData mail = createTestMail(senderUuid.toString(), "SenderPlayer",
                senderUuid.toString(), "SenderPlayer");
            mail.setId("101");

            mailService.deleteMail(mail, senderUuid);

            // Both flags should be true, so real delete
            verify(mockDataOperator).delById("101");
        }

        @Test
        @DisplayName("update异常时不应抛出")
        void shouldNotThrowOnUpdateException() throws Exception {
            MailData mail = createTestMail(senderUuid.toString(), "SenderPlayer", "r1", "receiver1");
            doThrow(new IllegalAccessException("test")).when(mockDataOperator).update(mail);

            // Should not throw
            mailService.deleteMail(mail, senderUuid);

            assertThat(mail.isDeletedBySender()).isTrue();
        }
    }

    // ==================== executeMailCommands edge cases ====================

    @Nested
    @DisplayName("executeMailCommands 边界测试")
    class ExecuteMailCommandsEdgeCaseTests {

        @Test
        @DisplayName("无效JSON commands应处理异常不抛出")
        void shouldHandleInvalidJsonGracefully() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("not valid json");

            // Should not throw
            mailService.executeMailCommands(receiver, mail);

            verify(receiver, never()).performCommand(anyString());
        }

        @Test
        @DisplayName("空字符串commands不应执行")
        void shouldNotExecuteEmptyStringCommands() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("");

            mailService.executeMailCommands(receiver, mail);

            verify(receiver, never()).performCommand(anyString());
        }

        @Test
        @DisplayName("console:前缀大小写混合也应被识别")
        void shouldRecognizeMixedCaseConsolePrefix() {
            ConsoleCommandSender consoleSender = mock(ConsoleCommandSender.class);
            mockedBukkit.when(Bukkit::getConsoleSender).thenReturn(consoleSender);
            mockedBukkit.when(() -> Bukkit.dispatchCommand(any(), anyString())).thenReturn(true);

            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("[\"Console:test command\"]");

            mailService.executeMailCommands(receiver, mail);

            mockedBukkit.verify(() -> Bukkit.dispatchCommand(eq(consoleSender), eq("test command")));
        }

        @Test
        @DisplayName("update异常时executeMailCommands不应抛出")
        void shouldNotThrowOnUpdateFailure() throws Exception {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setCommands("[\"test\"]");
            doThrow(new IllegalAccessException("test")).when(mockDataOperator).update(mail);

            // Should not throw — caught by general catch(Exception)
            mailService.executeMailCommands(receiver, mail);
        }
    }

    // ==================== getUnreadCount edge cases ====================

    @Nested
    @DisplayName("getUnreadCount 边界测试")
    class GetUnreadCountEdgeCaseTests {

        @Test
        @DisplayName("已删除的未读邮件不应计入未读数")
        void shouldNotCountDeletedUnreadMails() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setRead(false);
            mail.setDeletedByReceiver(true);
            mails.add(mail);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.getUnreadCount(receiverUuid);

            // getInbox filters deleted, so 0 unread
            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("多个未读邮件应正确累加")
        void shouldCorrectlyCountMultipleUnread() {
            List<MailData> mails = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                MailData mail = createTestMail("s" + i, "sender" + i, receiverUuid.toString(), "ReceiverPlayer");
                mail.setRead(false);
                mails.add(mail);
            }
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.getUnreadCount(receiverUuid);

            assertThat(count).isEqualTo(10);
        }
    }

    // ==================== getSentMails edge cases ====================

    @Nested
    @DisplayName("getSentMails 边界测试")
    class GetSentMailsEdgeCaseTests {

        @Test
        @DisplayName("空发件箱应返回空列表")
        void shouldReturnEmptyForEmptySentbox() {
            when(mockQueryBuilder.list()).thenReturn(new ArrayList<>());

            List<MailData> result = mailService.getSentMails(senderUuid);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("所有发件都删除时应返回空列表")
        void shouldReturnEmptyWhenAllSentDeleted() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail(senderUuid.toString(), "SenderPlayer", "r1", "receiver1");
            mail.setDeletedBySender(true);
            mails.add(mail);
            when(mockQueryBuilder.list()).thenReturn(mails);

            List<MailData> result = mailService.getSentMails(senderUuid);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("多个发件应保留未删除的")
        void shouldKeepOnlyNonDeletedSentMails() {
            List<MailData> mails = new ArrayList<>();
            MailData mail1 = createTestMail(senderUuid.toString(), "SenderPlayer", "r1", "receiver1");
            MailData mail2 = createTestMail(senderUuid.toString(), "SenderPlayer", "r2", "receiver2");
            mail2.setDeletedBySender(true);
            MailData mail3 = createTestMail(senderUuid.toString(), "SenderPlayer", "r3", "receiver3");
            mails.add(mail1);
            mails.add(mail2);
            mails.add(mail3);
            when(mockQueryBuilder.list()).thenReturn(mails);

            List<MailData> result = mailService.getSentMails(senderUuid);

            assertThat(result).hasSize(2);
        }
    }

    // ==================== deleteAllByReceiver edge cases ====================

    @Nested
    @DisplayName("deleteAllByReceiver 边界测试")
    class DeleteAllByReceiverEdgeCaseTests {

        @Test
        @DisplayName("空收件箱deleteAll应返回0")
        void shouldReturnZeroForEmptyInbox() {
            when(mockQueryBuilder.list()).thenReturn(new ArrayList<>());

            int count = mailService.deleteAllByReceiver(receiverUuid);

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("混合有附件和无附件的邮件")
        void shouldHandleMixedItemsMails() {
            List<MailData> mails = new ArrayList<>();
            MailData noItems = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            MailData withUnclaimed = createTestMail("s2", "sender2", receiverUuid.toString(), "ReceiverPlayer");
            withUnclaimed.setItems("items");
            withUnclaimed.setClaimed(false);
            MailData withClaimed = createTestMail("s3", "sender3", receiverUuid.toString(), "ReceiverPlayer");
            withClaimed.setItems("items");
            withClaimed.setClaimed(true);
            mails.add(noItems);
            mails.add(withUnclaimed);
            mails.add(withClaimed);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.deleteAllByReceiver(receiverUuid);

            // noItems + withClaimed = 2, withUnclaimed skipped
            assertThat(count).isEqualTo(2);
        }
    }

    // ==================== deleteReadByReceiver edge cases ====================

    @Nested
    @DisplayName("deleteReadByReceiver 边界测试")
    class DeleteReadByReceiverEdgeCaseTests {

        @Test
        @DisplayName("没有已读邮件时应返回0")
        void shouldReturnZeroWhenNoReadMails() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setRead(false);
            mails.add(mail);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.deleteReadByReceiver(receiverUuid);

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("空收件箱deleteRead应返回0")
        void shouldReturnZeroForEmptyInbox() {
            when(mockQueryBuilder.list()).thenReturn(new ArrayList<>());

            int count = mailService.deleteReadByReceiver(receiverUuid);

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("混合已读未读邮件")
        void shouldHandleMixedReadUnreadMails() {
            List<MailData> mails = new ArrayList<>();
            MailData unread1 = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            unread1.setRead(false);
            MailData read1 = createTestMail("s2", "sender2", receiverUuid.toString(), "ReceiverPlayer");
            read1.setRead(true);
            MailData read2 = createTestMail("s3", "sender3", receiverUuid.toString(), "ReceiverPlayer");
            read2.setRead(true);
            MailData unread2 = createTestMail("s4", "sender4", receiverUuid.toString(), "ReceiverPlayer");
            unread2.setRead(false);
            mails.add(unread1);
            mails.add(read1);
            mails.add(read2);
            mails.add(unread2);
            when(mockQueryBuilder.list()).thenReturn(mails);

            int count = mailService.deleteReadByReceiver(receiverUuid);

            assertThat(count).isEqualTo(2);
        }
    }

    // ==================== claimItems edge cases ====================

    @Nested
    @DisplayName("claimItems 边界测试")
    class ClaimItemsEdgeCaseTests {

        @Test
        @DisplayName("无效base64数据应返回空数组")
        void shouldReturnEmptyForInvalidBase64Items() {
            MailData mail = createTestMail("s1", "sender1", receiverUuid.toString(), "ReceiverPlayer");
            mail.setItems("not-valid-base64-data");

            ItemStack[] result = mailService.claimItems(mail, receiver);

            // deserializeItems returns empty array on error
            assertThat(result).isEmpty();
        }
    }

    // ==================== sendToAll Tests ====================

    @Nested
    @DisplayName("sendToAll 方法测试")
    class SendToAllTests {

        @Mock
        private BukkitScheduler mockScheduler;

        @Mock
        private Plugin mockBukkitPlugin;

        @Mock
        private org.bukkit.plugin.PluginManager mockPluginManager;

        @BeforeEach
        void setUpSendToAll() throws Exception {
            mockedBukkit.when(Bukkit::getScheduler).thenReturn(mockScheduler);
            mockedBukkit.when(Bukkit::getPluginManager).thenReturn(mockPluginManager);
            lenient().when(mockPluginManager.getPlugin("UltiTools")).thenReturn(mockBukkitPlugin);

            // Inject bukkitPlugin for sendToAll
            injectField(mailService, "bukkitPlugin", mockBukkitPlugin);

            // Make runTaskAsynchronously run immediately
            lenient().when(mockScheduler.runTaskAsynchronously(any(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return null;
                });

            // Make runTask run immediately (for progress & final notification BukkitRunnables)
            lenient().when(mockScheduler.runTask(any(Plugin.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return null;
                });
        }

        @Test
        @DisplayName("应该给所有离线玩家发送邮件")
        void shouldSendToAllOfflinePlayers() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();
            OfflinePlayer off1 = mock(OfflinePlayer.class);
            when(off1.getUniqueId()).thenReturn(uuid1);
            when(off1.getName()).thenReturn("Player1");
            when(off1.isOnline()).thenReturn(false);
            OfflinePlayer off2 = mock(OfflinePlayer.class);
            when(off2.getUniqueId()).thenReturn(uuid2);
            when(off2.getName()).thenReturn("Player2");
            when(off2.isOnline()).thenReturn(false);
            mockedBukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[]{off1, off2});

            mailService.sendToAll(sender, "广播内容", null);

            verify(mockDataOperator, times(2)).insert(any(MailData.class));
        }

        @Test
        @DisplayName("应该跳过发送者自己")
        void shouldSkipSender() {
            OfflinePlayer senderOffline = mock(OfflinePlayer.class);
            lenient().when(senderOffline.getUniqueId()).thenReturn(senderUuid);
            lenient().when(senderOffline.getName()).thenReturn("SenderPlayer");
            UUID otherUuid = UUID.randomUUID();
            OfflinePlayer other = mock(OfflinePlayer.class);
            when(other.getUniqueId()).thenReturn(otherUuid);
            when(other.getName()).thenReturn("OtherPlayer");
            when(other.isOnline()).thenReturn(false);
            mockedBukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[]{senderOffline, other});

            mailService.sendToAll(sender, "广播内容", null);

            // Only 1 insert (skipped sender)
            verify(mockDataOperator, times(1)).insert(any(MailData.class));
        }

        @Test
        @DisplayName("空玩家列表不应发送")
        void shouldNotSendForEmptyPlayerList() {
            mockedBukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[0]);

            mailService.sendToAll(sender, "广播内容", null);

            verify(mockDataOperator, never()).insert(any(MailData.class));
        }

        @Test
        @DisplayName("在线接收者应收到通知")
        void shouldNotifyOnlineReceivers() {
            OfflinePlayer offReceiver = mock(OfflinePlayer.class);
            when(offReceiver.getUniqueId()).thenReturn(receiverUuid);
            when(offReceiver.getName()).thenReturn("ReceiverPlayer");
            when(offReceiver.isOnline()).thenReturn(true);
            when(offReceiver.getPlayer()).thenReturn(receiver);
            mockedBukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[]{offReceiver});

            mailService.sendToAll(sender, "广播内容", null);

            verify(receiver).sendMessage(any(String.class));
        }

        @Test
        @DisplayName("应该发送最终成功消息")
        void shouldSendFinalSuccessMessage() {
            UUID otherUuid = UUID.randomUUID();
            OfflinePlayer other = mock(OfflinePlayer.class);
            when(other.getUniqueId()).thenReturn(otherUuid);
            when(other.getName()).thenReturn("OtherPlayer");
            when(other.isOnline()).thenReturn(false);
            mockedBukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[]{other});

            mailService.sendToAll(sender, "广播内容", null);

            // The final notification BukkitRunnable should send success message
            verify(sender, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[sendall_success]")
            ));
        }

        @Test
        @DisplayName("getName为null的玩家应使用Unknown")
        void shouldUseUnknownForNullName() {
            UUID otherUuid = UUID.randomUUID();
            OfflinePlayer other = mock(OfflinePlayer.class);
            when(other.getUniqueId()).thenReturn(otherUuid);
            when(other.getName()).thenReturn(null);
            when(other.isOnline()).thenReturn(false);
            mockedBukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[]{other});

            mailService.sendToAll(sender, "广播内容", null);

            verify(mockDataOperator).insert(argThat(mail ->
                "Unknown".equals(mail.getReceiverName())
            ));
        }
    }

    // ==================== init Tests ====================

    @Nested
    @DisplayName("init 方法测试")
    class InitTests {

        @Test
        @DisplayName("getConfig 不应返回 null")
        void shouldReturnNonNullConfig() {
            MailConfig result = mailService.getConfig();

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("默认配置值应正确")
        void shouldHaveCorrectDefaultConfigValues() {
            MailConfig cfg = mailService.getConfig();

            assertThat(cfg.getMaxItems()).isEqualTo(27);
            assertThat(cfg.getMaxSubjectLength()).isEqualTo(50);
            assertThat(cfg.getMaxContentLength()).isEqualTo(500);
            assertThat(cfg.getSendCooldown()).isEqualTo(10);
            assertThat(cfg.isNotifyOnJoin()).isTrue();
            assertThat(cfg.getNotifyDelay()).isEqualTo(3);
        }
    }

    // ==================== sendMailInternal extended Tests ====================

    @Nested
    @DisplayName("sendMailInternal 扩展测试")
    class SendMailInternalExtendedTests {

        @Test
        @DisplayName("接收者不存在应返回false")
        void shouldReturnFalseForUnknownReceiver() {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("Unknown")).thenReturn(null);
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offlinePlayer = mock(org.bukkit.OfflinePlayer.class);
            when(offlinePlayer.hasPlayedBefore()).thenReturn(false);
            when(offlinePlayer.isOnline()).thenReturn(false);
            mockedBukkit.when(() -> Bukkit.getOfflinePlayer("Unknown")).thenReturn(offlinePlayer);

            boolean result = mailService.sendMailInternal(UUID.randomUUID(), "Sender", "Unknown",
                "Subject", "Content", null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("senderUuid为null应该正确处理")
        void shouldHandleNullSenderUuid() {
            Player receiverPlayer = mock(Player.class);
            UUID recUuid = UUID.randomUUID();
            when(receiverPlayer.getUniqueId()).thenReturn(recUuid);
            mockedBukkit.when(() -> Bukkit.getPlayerExact("ReceiverPlayer")).thenReturn(receiverPlayer);

            boolean result = mailService.sendMailInternal(null, "SystemSender", "ReceiverPlayer",
                "Subject", "Content", null);

            assertThat(result).isTrue();
            verify(mockDataOperator).insert(argThat(mail ->
                mail.getSenderUuid() == null && "SystemSender".equals(mail.getSenderName())
            ));
        }

        @Test
        @DisplayName("正常发送应返回true并插入数据")
        void shouldReturnTrueAndInsertOnSuccess() {
            Player receiverPlayer = mock(Player.class);
            UUID recUuid = UUID.randomUUID();
            when(receiverPlayer.getUniqueId()).thenReturn(recUuid);
            when(receiverPlayer.isOnline()).thenReturn(true);
            mockedBukkit.when(() -> Bukkit.getPlayerExact("ReceiverPlayer")).thenReturn(receiverPlayer);

            UUID senderUuid = UUID.randomUUID();
            boolean result = mailService.sendMailInternal(senderUuid, "Sender", "ReceiverPlayer",
                "Subject", "Content", null);

            assertThat(result).isTrue();
            verify(mockDataOperator).insert(any(MailData.class));
        }
    }

    // ==================== notifyReceiver Tests ====================

    @Nested
    @DisplayName("notifyReceiver 方法测试")
    class NotifyReceiverTests {

        @Test
        @DisplayName("在线接收者应该收到通知消息")
        void shouldNotifyOnlineReceiver() throws Exception {
            when(receiver.isOnline()).thenReturn(true);
            mockedBukkit.when(() -> Bukkit.getPlayerExact("ReceiverPlayer")).thenReturn(receiver);

            Method method = MailService.class.getDeclaredMethod("notifyReceiver", String.class, String.class);
            method.setAccessible(true);

            method.invoke(mailService, "ReceiverPlayer", "SenderPlayer");

            verify(receiver).sendMessage(any(String.class));
        }

        @Test
        @DisplayName("离线接收者不应发送消息")
        void shouldNotNotifyOfflineReceiver() throws Exception {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("OfflinePlayer")).thenReturn(null);

            Method method = MailService.class.getDeclaredMethod("notifyReceiver", String.class, String.class);
            method.setAccessible(true);

            method.invoke(mailService, "OfflinePlayer", "SenderPlayer");

            verify(receiver, never()).sendMessage(any(String.class));
        }
    }

    // ==================== isOnCooldown Tests ====================

    @Nested
    @DisplayName("isOnCooldown 方法测试")
    class IsOnCooldownTests {

        @Test
        @DisplayName("从未发送过的玩家不应处于冷却中")
        void shouldNotBeOnCooldownForNewPlayer() throws Exception {
            Method method = MailService.class.getDeclaredMethod("isOnCooldown", UUID.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(mailService, UUID.randomUUID());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("最近发送过的玩家应处于冷却中")
        void shouldBeOnCooldownForRecentSender() throws Exception {
            UUID uuid = UUID.randomUUID();
            getCooldownMap().put(uuid, System.currentTimeMillis());

            Method method = MailService.class.getDeclaredMethod("isOnCooldown", UUID.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(mailService, uuid);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("冷却过期后不应处于冷却中")
        void shouldNotBeOnCooldownAfterExpiry() throws Exception {
            UUID uuid = UUID.randomUUID();
            // Set cooldown far in the past (60 seconds ago, default cooldown is 10s)
            getCooldownMap().put(uuid, System.currentTimeMillis() - 60000L);

            Method method = MailService.class.getDeclaredMethod("isOnCooldown", UUID.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(mailService, uuid);

            assertThat(result).isFalse();
        }
    }

    // ==================== getPlayerUuid Tests ====================

    @Nested
    @DisplayName("getPlayerUuid 方法测试")
    class GetPlayerUuidTests {

        @Test
        @DisplayName("在线玩家应通过getPlayerExact找到")
        void shouldFindOnlinePlayer() throws Exception {
            UUID onlineUuid = UUID.randomUUID();
            Player onlinePlayer = mock(Player.class);
            when(onlinePlayer.getUniqueId()).thenReturn(onlineUuid);
            mockedBukkit.when(() -> Bukkit.getPlayerExact("OnlinePlayer")).thenReturn(onlinePlayer);

            Method method = MailService.class.getDeclaredMethod("getPlayerUuid", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(mailService, "OnlinePlayer");

            assertThat(result).isEqualTo(onlineUuid.toString());
        }

        @Test
        @DisplayName("离线但玩过的玩家应通过getOfflinePlayer找到")
        void shouldFindOfflinePlayerWhoPlayedBefore() throws Exception {
            UUID offUuid = UUID.randomUUID();
            mockedBukkit.when(() -> Bukkit.getPlayerExact("OfflineGuy")).thenReturn(null);
            OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
            when(offlinePlayer.hasPlayedBefore()).thenReturn(true);
            when(offlinePlayer.getUniqueId()).thenReturn(offUuid);
            mockedBukkit.when(() -> Bukkit.getOfflinePlayer("OfflineGuy")).thenReturn(offlinePlayer);

            Method method = MailService.class.getDeclaredMethod("getPlayerUuid", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(mailService, "OfflineGuy");

            assertThat(result).isEqualTo(offUuid.toString());
        }

        @Test
        @DisplayName("从未玩过的玩家应返回null")
        void shouldReturnNullForUnknownPlayer() throws Exception {
            mockedBukkit.when(() -> Bukkit.getPlayerExact("NeverPlayed")).thenReturn(null);
            OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
            when(offlinePlayer.hasPlayedBefore()).thenReturn(false);
            when(offlinePlayer.isOnline()).thenReturn(false);
            mockedBukkit.when(() -> Bukkit.getOfflinePlayer("NeverPlayed")).thenReturn(offlinePlayer);

            Method method = MailService.class.getDeclaredMethod("getPlayerUuid", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(mailService, "NeverPlayed");

            assertThat(result).isNull();
        }
    }

    // ==================== createMailData Tests ====================

    @Nested
    @DisplayName("createMailData 方法测试")
    class CreateMailDataTests {

        @Test
        @DisplayName("无附件无命令应创建基本邮件")
        void shouldCreateBasicMail() throws Exception {
            Method method = MailService.class.getDeclaredMethod("createMailData",
                String.class, String.class, String.class, String.class,
                String.class, String.class, ItemStack[].class, List.class);
            method.setAccessible(true);

            MailData result = (MailData) method.invoke(mailService,
                "senderUuid", "SenderName", "receiverUuid", "ReceiverName",
                "Subject", "Content", null, null);

            assertThat(result).isNotNull();
            assertThat(result.getSenderUuid()).isEqualTo("senderUuid");
            assertThat(result.getSenderName()).isEqualTo("SenderName");
            assertThat(result.getReceiverUuid()).isEqualTo("receiverUuid");
            assertThat(result.getReceiverName()).isEqualTo("ReceiverName");
            assertThat(result.getSubject()).isEqualTo("Subject");
            assertThat(result.getContent()).isEqualTo("Content");
            assertThat(result.getItems()).isNull();
            assertThat(result.getCommands()).isNull();
        }

        @Test
        @DisplayName("有命令应序列化命令为JSON")
        void shouldSerializeCommands() throws Exception {
            Method method = MailService.class.getDeclaredMethod("createMailData",
                String.class, String.class, String.class, String.class,
                String.class, String.class, ItemStack[].class, List.class);
            method.setAccessible(true);

            List<String> commands = Arrays.asList("give %player% diamond 1", "console:eco give %player% 100");

            MailData result = (MailData) method.invoke(mailService,
                "senderUuid", "SenderName", "receiverUuid", "ReceiverName",
                "Subject", "Content", null, commands);

            assertThat(result).isNotNull();
            assertThat(result.getCommands()).isNotNull();
            assertThat(result.getCommands()).contains("diamond");
            assertThat(result.getCommands()).contains("eco");
        }

        @Test
        @DisplayName("空命令列表不应设置commands字段")
        void shouldNotSetEmptyCommands() throws Exception {
            Method method = MailService.class.getDeclaredMethod("createMailData",
                String.class, String.class, String.class, String.class,
                String.class, String.class, ItemStack[].class, List.class);
            method.setAccessible(true);

            List<String> emptyCommands = Collections.emptyList();

            MailData result = (MailData) method.invoke(mailService,
                "senderUuid", "SenderName", "receiverUuid", "ReceiverName",
                "Subject", "Content", null, emptyCommands);

            assertThat(result).isNotNull();
            assertThat(result.getCommands()).isNull();
        }

        @Test
        @DisplayName("全null和AIR物品应忽略")
        void shouldIgnoreNullAndAirItems() throws Exception {
            Method method = MailService.class.getDeclaredMethod("createMailData",
                String.class, String.class, String.class, String.class,
                String.class, String.class, ItemStack[].class, List.class);
            method.setAccessible(true);

            ItemStack airItem = mock(ItemStack.class);
            when(airItem.getType()).thenReturn(Material.AIR);
            ItemStack[] items = new ItemStack[]{null, airItem};

            MailData result = (MailData) method.invoke(mailService,
                "senderUuid", "SenderName", "receiverUuid", "ReceiverName",
                "Subject", "Content", items, null);

            assertThat(result).isNotNull();
            assertThat(result.getItems()).isNull(); // All filtered out, no valid items
        }

        @Test
        @DisplayName("超过最大附件数应返回null")
        void shouldReturnNullForTooManyItems() throws Exception {
            config.setMaxItems(2);

            Method method = MailService.class.getDeclaredMethod("createMailData",
                String.class, String.class, String.class, String.class,
                String.class, String.class, ItemStack[].class, List.class);
            method.setAccessible(true);

            ItemStack item1 = mock(ItemStack.class);
            when(item1.getType()).thenReturn(Material.DIAMOND);
            ItemStack item2 = mock(ItemStack.class);
            when(item2.getType()).thenReturn(Material.GOLD_INGOT);
            ItemStack item3 = mock(ItemStack.class);
            when(item3.getType()).thenReturn(Material.IRON_INGOT);
            ItemStack[] items = new ItemStack[]{item1, item2, item3};

            MailData result = (MailData) method.invoke(mailService,
                "senderUuid", "SenderName", "receiverUuid", "ReceiverName",
                "Subject", "Content", items, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("邮件应该设置发送时间")
        void shouldSetSentTime() throws Exception {
            Method method = MailService.class.getDeclaredMethod("createMailData",
                String.class, String.class, String.class, String.class,
                String.class, String.class, ItemStack[].class, List.class);
            method.setAccessible(true);

            long before = System.currentTimeMillis();
            MailData result = (MailData) method.invoke(mailService,
                "senderUuid", "SenderName", "receiverUuid", "ReceiverName",
                "Subject", "Content", null, null);
            long after = System.currentTimeMillis();

            assertThat(result.getSentTime()).isBetween(before, after);
        }
    }

    // ==================== sendMail items-too-many Tests ====================

    @Nested
    @DisplayName("sendMail 附件过多测试")
    class SendMailItemsTooManyTests {

        @Test
        @DisplayName("附件超过上限时应返回false并发送错误消息")
        void shouldRejectWhenItemsExceedMax() {
            config.setMaxItems(2);

            // Create 3 valid items (exceeds max of 2)
            ItemStack item1 = mock(ItemStack.class);
            when(item1.getType()).thenReturn(Material.DIAMOND);
            ItemStack item2 = mock(ItemStack.class);
            when(item2.getType()).thenReturn(Material.GOLD_INGOT);
            ItemStack item3 = mock(ItemStack.class);
            when(item3.getType()).thenReturn(Material.IRON_INGOT);
            ItemStack[] items = new ItemStack[]{item1, item2, item3};

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", items);

            assertThat(result).isFalse();
            verify(sender).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[send_items_too_many]")
            ));
            verify(mockDataOperator, never()).insert(any());
        }

        @Test
        @DisplayName("附件刚好在上限内应该发送成功")
        void shouldAcceptItemsAtExactLimit() {
            config.setMaxItems(2);

            // Create 2 valid items (equals max of 2)
            ItemStack item1 = mock(ItemStack.class);
            when(item1.getType()).thenReturn(Material.DIAMOND);
            ItemStack item2 = mock(ItemStack.class);
            when(item2.getType()).thenReturn(Material.GOLD_INGOT);
            ItemStack[] items = new ItemStack[]{item1, item2};

            boolean result = mailService.sendMail(sender, "ReceiverPlayer", "标题", "内容", items);

            // serializeItems will fail (no real Bukkit) so mail items will be null, but createMailData won't return null
            // This tests the happy path through createMailData's item validation
            assertThat(result).isTrue();
        }
    }

    // ==================== i18n Tests ====================

    @Nested
    @DisplayName("i18n 方法测试")
    class I18nTests {

        @Test
        @DisplayName("应该委托给plugin.i18n")
        void shouldDelegateToPlugin() throws Exception {
            Method method = MailService.class.getDeclaredMethod("i18n", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(mailService, "test_key");

            assertThat(result).isEqualTo("[test_key]");
        }
    }

    // ==================== init method Tests ====================

    @Nested
    @DisplayName("init 方法直接测试")
    class InitMethodTests {

        @Test
        @DisplayName("init应该设置dataOperator和bukkitPlugin")
        void shouldInitializeDataOperatorAndBukkitPlugin() throws Exception {
            // Create a fresh service
            MailService freshService = new MailService();
            UltiToolsPlugin mockPlugin2 = TestHelper.mockUltiToolsPlugin();
            when(mockPlugin2.getDataOperator(MailData.class)).thenReturn(mockDataOperator);
            injectField(freshService, "plugin", mockPlugin2);

            Plugin mockBukkitPlugin = mock(Plugin.class);
            org.bukkit.plugin.PluginManager pluginManager = mock(org.bukkit.plugin.PluginManager.class);
            when(pluginManager.getPlugin("UltiTools")).thenReturn(mockBukkitPlugin);
            mockedBukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            freshService.init();

            // Verify dataOperator was set
            java.lang.reflect.Field dataOpField = MailService.class.getDeclaredField("dataOperator");
            dataOpField.setAccessible(true);
            assertThat(dataOpField.get(freshService)).isSameAs(mockDataOperator);

            // Verify bukkitPlugin was set
            java.lang.reflect.Field bukkitPluginField = MailService.class.getDeclaredField("bukkitPlugin");
            bukkitPluginField.setAccessible(true);
            assertThat(bukkitPluginField.get(freshService)).isSameAs(mockBukkitPlugin);
        }
    }

    // Helper method
    private MailData createTestMail(String senderUuid, String senderName, String receiverUuid, String receiverName) {
        MailData mail = new MailData();
        mail.setSenderUuid(senderUuid);
        mail.setSenderName(senderName);
        mail.setReceiverUuid(receiverUuid);
        mail.setReceiverName(receiverName);
        mail.setSubject("Test Subject");
        mail.setContent("Test Content");
        mail.setSentTime(System.currentTimeMillis());
        return mail;
    }
}
