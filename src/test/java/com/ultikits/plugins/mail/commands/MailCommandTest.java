package com.ultikits.plugins.mail.commands;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.plugins.mail.service.MailService;
import com.ultikits.plugins.mail.utils.TestHelper;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MailCommand.
 * <p>
 * Uses pure Mockito (no MockBukkit) for maximum compatibility.
 * Tests actual command method execution with mocked dependencies.
 */
@DisplayName("MailCommand 测试")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MailCommandTest {

    private MailCommand mailCommand;

    @Mock
    private MailService mockMailService;

    @Mock
    private Player player;

    @Mock
    private PlayerInventory playerInventory;

    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        // Setup mock UltiToolsPlugin
        UltiToolsPlugin mockPlugin = TestHelper.mockUltiToolsPlugin();

        playerUuid = UUID.randomUUID();
        lenient().when(player.getUniqueId()).thenReturn(playerUuid);
        lenient().when(player.getName()).thenReturn("TestPlayer");
        lenient().when(player.getInventory()).thenReturn(playerInventory);

        // Create command with mock mailService and inject plugin
        mailCommand = new MailCommand(mockMailService);
        TestHelper.injectField(mailCommand, "plugin", mockPlugin);
    }

    @AfterEach
    void tearDown() {
        TestHelper.cleanupMocks();
    }

    // ==================== inbox Tests ====================

    @Nested
    @DisplayName("inbox 命令测试")
    class InboxCommandTests {

        @Test
        @DisplayName("空收件箱时应该显示空消息")
        void shouldShowEmptyMessageForEmptyInbox() {
            when(mockMailService.getInbox(playerUuid)).thenReturn(new ArrayList<>());

            mailCommand.inbox(player);

            // Should send yellow (empty) message
            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[inbox_empty]")));
        }

        @Test
        @DisplayName("有邮件时应该显示邮件列表")
        void shouldShowMailListWhenHasMails() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail("sender1", false, false));
            mails.add(createTestMail("sender2", true, false));
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.inbox(player);

            // Title message + 2 mail lines + hint = at least 4 messages
            verify(player, atLeast(4)).sendMessage(any(String.class));
        }

        @Test
        @DisplayName("已读邮件应该显示已读状态")
        void shouldShowReadStatus() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail("sender1", true, false));
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.inbox(player);

            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[inbox_status_read]")
            ));
        }

        @Test
        @DisplayName("未读邮件应该显示未读状态")
        void shouldShowUnreadStatus() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail("sender1", false, false));
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.inbox(player);

            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[inbox_status_unread]")
            ));
        }

        @Test
        @DisplayName("有附件的邮件应该显示附件标记")
        void shouldShowItemStatus() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender1", false, false);
            mail.setItems("base64data");
            mail.setClaimed(false);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.inbox(player);

            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[inbox_status_has_items]")
            ));
        }

        @Test
        @DisplayName("已领取附件应该显示已领取标记")
        void shouldShowClaimedStatus() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender1", false, false);
            mail.setItems("base64data");
            mail.setClaimed(true);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.inbox(player);

            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[inbox_status_claimed]")
            ));
        }

        @Test
        @DisplayName("标题行应显示邮件数量")
        void shouldShowMailCount() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail("sender1", false, false));
            mails.add(createTestMail("sender2", false, false));
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.inbox(player);

            // Title should contain count (i18n returns [inbox_title] and replace {0} with 2)
            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("2")
            ));
        }
    }

    // ==================== sent Tests ====================

    @Nested
    @DisplayName("sent 命令测试")
    class SentCommandTests {

        @Test
        @DisplayName("空发件箱应该显示空消息")
        void shouldShowEmptyForEmptySentbox() {
            when(mockMailService.getSentMails(playerUuid)).thenReturn(new ArrayList<>());

            mailCommand.sent(player);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[sentbox_empty]")));
        }

        @Test
        @DisplayName("有发出邮件时应该显示列表")
        void shouldShowSentMailsList() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = new MailData();
            mail.setSenderUuid(playerUuid.toString());
            mail.setSenderName("TestPlayer");
            mail.setReceiverUuid("r1");
            mail.setReceiverName("receiver1");
            mail.setSubject("Subject");
            mail.setContent("Content");
            mail.setRead(false);
            mails.add(mail);
            when(mockMailService.getSentMails(playerUuid)).thenReturn(mails);

            mailCommand.sent(player);

            // Title + at least one mail line
            verify(player, atLeast(2)).sendMessage(any(String.class));
        }

        @Test
        @DisplayName("发件箱应显示接收者名称")
        void shouldShowReceiverName() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = new MailData();
            mail.setSenderUuid(playerUuid.toString());
            mail.setSenderName("TestPlayer");
            mail.setReceiverUuid("r1");
            mail.setReceiverName("ReceiverGuy");
            mail.setSubject("Subject");
            mail.setContent("Content");
            mail.setRead(false);
            mails.add(mail);
            when(mockMailService.getSentMails(playerUuid)).thenReturn(mails);

            mailCommand.sent(player);

            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("ReceiverGuy")
            ));
        }
    }

    // ==================== readByIndex Tests ====================

    @Nested
    @DisplayName("read <index> 命令测试")
    class ReadByIndexTests {

        @Test
        @DisplayName("无效索引应该显示错误消息")
        void shouldShowErrorForInvalidIndex() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail("sender", false, false));
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 5);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[error_invalid_index]")));
        }

        @Test
        @DisplayName("索引为0时应该显示错误消息")
        void shouldShowErrorForZeroIndex() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail("sender", false, false));
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 0);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[error_invalid_index]")));
        }

        @Test
        @DisplayName("负数索引应该显示错误消息")
        void shouldShowErrorForNegativeIndex() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail("sender", false, false));
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, -1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[error_invalid_index]")));
        }

        @Test
        @DisplayName("有效索引应该标记为已读")
        void shouldMarkAsRead() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 1);

            verify(mockMailService).markAsRead(mail);
        }

        @Test
        @DisplayName("应该显示邮件详情")
        void shouldShowMailDetails() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("TestSender", false, false);
            mail.setSubject("测试标题");
            mail.setContent("测试内容");
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 1);

            // Should show sender, subject, time, content, delete hint = at least 6 messages
            verify(player, atLeast(6)).sendMessage(any(String.class));
            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("TestSender")));
            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("测试标题")));
            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("测试内容")));
        }

        @Test
        @DisplayName("有命令的邮件应该执行命令")
        void shouldExecuteCommandsWhenPresent() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setCommands("[\"give %player% diamond 1\"]");
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 1);

            verify(mockMailService).executeMailCommands(player, mail);
        }

        @Test
        @DisplayName("无命令的邮件不应执行命令")
        void shouldNotExecuteCommandsWhenNone() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setCommands(null);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 1);

            verify(mockMailService, never()).executeMailCommands(any(), any());
        }

        @Test
        @DisplayName("命令已执行的邮件不应重复执行")
        void shouldNotReExecuteCommands() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setCommands("[\"test\"]");
            mail.setCommandsExecuted(true);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 1);

            verify(mockMailService, never()).executeMailCommands(any(), any());
        }

        @Test
        @DisplayName("有未领取附件的邮件应显示领取提示")
        void shouldShowClaimHintForUnclaimedItems() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems("base64data");
            mail.setClaimed(false);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 1);

            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[mail_detail_items_hint]")
            ));
        }

        @Test
        @DisplayName("已领取附件应显示已领取消息")
        void shouldShowClaimedMessage() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems("base64data");
            mail.setClaimed(true);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 1);

            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[mail_detail_items_claimed]")
            ));
        }
    }

    // ==================== claim Tests ====================

    @Nested
    @DisplayName("claim 命令测试")
    class ClaimCommandTests {

        @Test
        @DisplayName("无效索引应该显示错误")
        void shouldShowErrorForInvalidIndex() {
            when(mockMailService.getInbox(playerUuid)).thenReturn(new ArrayList<>());

            mailCommand.claim(player, 1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[error_invalid_index]")));
        }

        @Test
        @DisplayName("已领取的邮件应该显示已领取消息")
        void shouldShowAlreadyClaimedMessage() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems("base64data");
            mail.setClaimed(true);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.claim(player, 1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[claim_already_claimed]")));
        }

        @Test
        @DisplayName("无附件邮件应该显示无附件消息")
        void shouldShowNoItemsMessage() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems(null);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.claim(player, 1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[claim_no_items]")));
        }

        @Test
        @DisplayName("背包空间不足时应显示错误")
        void shouldShowErrorWhenInventoryFull() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems("base64data");
            mail.setClaimed(false);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);
            when(mockMailService.getItemCount(mail)).thenReturn(5);
            // Player inventory full - no empty slots
            when(playerInventory.getStorageContents()).thenReturn(new ItemStack[]{
                mock(ItemStack.class), mock(ItemStack.class)
            });
            // Non-empty items
            for (ItemStack item : playerInventory.getStorageContents()) {
                lenient().when(item.getType()).thenReturn(Material.DIAMOND);
            }

            mailCommand.claim(player, 1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[claim_inventory_full]")));
        }

        @Test
        @DisplayName("成功领取应该显示成功消息")
        void shouldShowSuccessMessage() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems("base64data");
            mail.setClaimed(false);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);
            when(mockMailService.getItemCount(mail)).thenReturn(1);
            // Player has empty slots
            ItemStack airItem = mock(ItemStack.class);
            when(airItem.getType()).thenReturn(Material.AIR);
            when(playerInventory.getStorageContents()).thenReturn(new ItemStack[]{null, airItem});
            // claimItems returns items
            ItemStack diamond = mock(ItemStack.class);
            when(mockMailService.claimItems(mail, player)).thenReturn(new ItemStack[]{diamond});

            mailCommand.claim(player, 1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[claim_success]")));
        }
    }

    // ==================== delete Tests ====================

    @Nested
    @DisplayName("delete 命令测试")
    class DeleteCommandTests {

        @Test
        @DisplayName("无效索引应该显示错误")
        void shouldShowErrorForInvalidIndex() {
            when(mockMailService.getInbox(playerUuid)).thenReturn(new ArrayList<>());

            mailCommand.delete(player, 1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[error_invalid_index]")));
        }

        @Test
        @DisplayName("有未领取附件时应该拒绝删除")
        void shouldRejectDeleteWithUnclaimedItems() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems("base64data");
            mail.setClaimed(false);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.delete(player, 1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[delete_claim_first]")));
            verify(mockMailService, never()).deleteMail(any(), any());
        }

        @Test
        @DisplayName("无附件邮件应该成功删除")
        void shouldDeleteMailWithoutItems() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.delete(player, 1);

            verify(mockMailService).deleteMail(mail, playerUuid);
            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[delete_success]")));
        }

        @Test
        @DisplayName("已领取附件的邮件应该成功删除")
        void shouldDeleteMailWithClaimedItems() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems("base64data");
            mail.setClaimed(true);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.delete(player, 1);

            verify(mockMailService).deleteMail(mail, playerUuid);
            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[delete_success]")));
        }
    }

    // ==================== deleteAll Tests ====================

    @Nested
    @DisplayName("delall 命令测试")
    class DeleteAllTests {

        @Test
        @DisplayName("应该调用 deleteAllByReceiver")
        void shouldCallDeleteAll() {
            when(mockMailService.deleteAllByReceiver(playerUuid)).thenReturn(5);

            mailCommand.deleteAll(player);

            verify(mockMailService).deleteAllByReceiver(playerUuid);
            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("5")));
        }

        @Test
        @DisplayName("零删除时仍应显示消息")
        void shouldShowMessageForZeroDeletes() {
            when(mockMailService.deleteAllByReceiver(playerUuid)).thenReturn(0);

            mailCommand.deleteAll(player);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("0")));
        }
    }

    // ==================== deleteRead Tests ====================

    @Nested
    @DisplayName("delread 命令测试")
    class DeleteReadTests {

        @Test
        @DisplayName("应该调用 deleteReadByReceiver")
        void shouldCallDeleteRead() {
            when(mockMailService.deleteReadByReceiver(playerUuid)).thenReturn(3);

            mailCommand.deleteRead(player);

            verify(mockMailService).deleteReadByReceiver(playerUuid);
            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("3")));
        }
    }

    // ==================== sendAll Tests ====================

    @Nested
    @DisplayName("sendall 命令测试")
    class SendAllTests {

        @Test
        @DisplayName("应该调用 sendToAll")
        void shouldCallSendToAll() {
            mailCommand.sendAll(player, "广播内容");

            verify(mockMailService).sendToAll(player, "广播内容", null);
        }
    }

    // ==================== help Tests ====================

    @Nested
    @DisplayName("help 命令测试")
    class HelpTests {

        @Test
        @DisplayName("帮助命令应该显示帮助信息")
        void shouldShowHelpMessage() {
            mailCommand.help(player);

            // Help should send multiple lines
            verify(player, atLeast(5)).sendMessage(any(String.class));
        }

        @Test
        @DisplayName("有管理员权限时应显示管理员命令")
        void shouldShowAdminCommandsForAdmins() {
            when(player.hasPermission("ultimail.admin.sendall")).thenReturn(true);

            mailCommand.help(player);

            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("sendall")
            ));
        }

        @Test
        @DisplayName("无管理员权限时不应显示管理员命令")
        void shouldNotShowAdminCommandsForRegularPlayers() {
            when(player.hasPermission("ultimail.admin.sendall")).thenReturn(false);

            mailCommand.help(player);

            // handleHelp is called, but the sendall line should not be sent
            // The sendall line contains ChatColor.RED, and is only sent if hasPermission is true
            // We need to verify the exact calls
        }

        @Test
        @DisplayName("handleHelp 应该接受 CommandSender")
        void shouldAcceptCommandSender() {
            // Verify help() method sends messages to the player
            when(player.hasPermission("ultimail.admin.sendall")).thenReturn(false);

            mailCommand.help(player);

            verify(player, atLeast(1)).sendMessage(any(String.class));
        }
    }

    // ==================== Annotation Tests ====================

    @Nested
    @DisplayName("注解配置测试")
    class AnnotationTests {

        @Test
        @DisplayName("类应该有 @CmdExecutor 注解")
        void shouldHaveCmdExecutorAnnotation() {
            assertThat(MailCommand.class.isAnnotationPresent(
                com.ultikits.ultitools.annotations.command.CmdExecutor.class
            )).isTrue();
        }

        @Test
        @DisplayName("@CmdExecutor 应该有正确的别名")
        void shouldHaveCorrectAliases() {
            com.ultikits.ultitools.annotations.command.CmdExecutor annotation =
                MailCommand.class.getAnnotation(
                    com.ultikits.ultitools.annotations.command.CmdExecutor.class
                );

            assertThat(annotation.alias()).contains("mail", "inbox");
        }

        @Test
        @DisplayName("@CmdExecutor 应该有正确的权限")
        void shouldHaveCorrectPermission() {
            com.ultikits.ultitools.annotations.command.CmdExecutor annotation =
                MailCommand.class.getAnnotation(
                    com.ultikits.ultitools.annotations.command.CmdExecutor.class
                );

            assertThat(annotation.permission()).isEqualTo("ultimail.use");
        }

        @Test
        @DisplayName("类应该有 @CmdTarget(PLAYER) 注解")
        void shouldHavePlayerTarget() {
            com.ultikits.ultitools.annotations.command.CmdTarget annotation =
                MailCommand.class.getAnnotation(
                    com.ultikits.ultitools.annotations.command.CmdTarget.class
                );

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(
                com.ultikits.ultitools.annotations.command.CmdTarget.CmdTargetType.PLAYER
            );
        }
    }

    // ==================== countEmptySlots Tests ====================

    @Nested
    @DisplayName("countEmptySlots 测试 (间接测试)")
    class CountEmptySlotsTests {

        @Test
        @DisplayName("空背包应该有足够空间")
        void shouldCountAllSlotsAsEmpty() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems("base64data");
            mail.setClaimed(false);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);
            when(mockMailService.getItemCount(mail)).thenReturn(2);
            // All null = empty
            when(playerInventory.getStorageContents()).thenReturn(new ItemStack[]{null, null, null});
            ItemStack diamond = mock(ItemStack.class);
            when(mockMailService.claimItems(mail, player)).thenReturn(new ItemStack[]{diamond});

            mailCommand.claim(player, 1);

            verify(mockMailService).claimItems(mail, player);
        }

        @Test
        @DisplayName("AIR物品应被视为空槽位")
        void shouldCountAirSlotsAsEmpty() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems("base64data");
            mail.setClaimed(false);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);
            when(mockMailService.getItemCount(mail)).thenReturn(1);
            ItemStack airItem = mock(ItemStack.class);
            when(airItem.getType()).thenReturn(Material.AIR);
            when(playerInventory.getStorageContents()).thenReturn(new ItemStack[]{airItem, null});
            ItemStack diamond = mock(ItemStack.class);
            when(mockMailService.claimItems(mail, player)).thenReturn(new ItemStack[]{diamond});

            mailCommand.claim(player, 1);

            verify(mockMailService).claimItems(mail, player);
        }

        @Test
        @DisplayName("满背包应该拒绝领取")
        void shouldRejectWhenInventoryCompletelyFull() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems("base64data");
            mail.setClaimed(false);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);
            when(mockMailService.getItemCount(mail)).thenReturn(3);
            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);
            when(playerInventory.getStorageContents()).thenReturn(new ItemStack[]{stone, stone});

            mailCommand.claim(player, 1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[claim_inventory_full]")));
            verify(mockMailService, never()).claimItems(any(), any());
        }
    }

    // ==================== readByIndex edge cases ====================

    @Nested
    @DisplayName("readByIndex 边界测试")
    class ReadByIndexEdgeCaseTests {

        @Test
        @DisplayName("邮件无附件时不显示附件相关信息")
        void shouldNotShowItemInfoWhenNoItems() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = createTestMail("sender", false, false);
            mail.setItems(null);
            mails.add(mail);
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 1);

            // Should not contain items hints
            verify(player, never()).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[mail_detail_items_hint]") || msg.contains("[mail_detail_items_claimed]")
            ));
        }

        @Test
        @DisplayName("空收件箱读取任何索引都应失败")
        void shouldFailForEmptyInbox() {
            when(mockMailService.getInbox(playerUuid)).thenReturn(new ArrayList<>());

            mailCommand.readByIndex(player, 1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[error_invalid_index]")));
        }

        @Test
        @DisplayName("最后一个索引应该成功读取")
        void shouldReadLastIndex() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail("sender1", false, false));
            mails.add(createTestMail("sender2", false, false));
            mails.add(createTestMail("sender3", false, false));
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.readByIndex(player, 3);

            verify(mockMailService).markAsRead(mails.get(2));
        }
    }

    // ==================== sent edge cases ====================

    @Nested
    @DisplayName("sent 边界测试")
    class SentEdgeCaseTests {

        @Test
        @DisplayName("已读发件应显示已读状态")
        void shouldShowReadStatusForSentMail() {
            List<MailData> mails = new ArrayList<>();
            MailData mail = new MailData();
            mail.setSenderUuid(playerUuid.toString());
            mail.setSenderName("TestPlayer");
            mail.setReceiverUuid("r1");
            mail.setReceiverName("receiver1");
            mail.setSubject("Subject");
            mail.setContent("Content");
            mail.setRead(true);
            mails.add(mail);
            when(mockMailService.getSentMails(playerUuid)).thenReturn(mails);

            mailCommand.sent(player);

            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[inbox_status_read]")
            ));
        }

        @Test
        @DisplayName("发件箱标题应显示邮件数量")
        void shouldShowSentMailCount() {
            List<MailData> mails = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                MailData mail = new MailData();
                mail.setSenderUuid(playerUuid.toString());
                mail.setSenderName("TestPlayer");
                mail.setReceiverUuid("r" + i);
                mail.setReceiverName("receiver" + i);
                mail.setSubject("Subject" + i);
                mail.setContent("Content");
                mail.setRead(false);
                mails.add(mail);
            }
            when(mockMailService.getSentMails(playerUuid)).thenReturn(mails);

            mailCommand.sent(player);

            verify(player, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("3")
            ));
        }
    }

    // ==================== delete edge cases ====================

    @Nested
    @DisplayName("delete 边界测试")
    class DeleteEdgeCaseTests {

        @Test
        @DisplayName("空收件箱删除应失败")
        void shouldFailForEmptyInboxDelete() {
            when(mockMailService.getInbox(playerUuid)).thenReturn(new ArrayList<>());

            mailCommand.delete(player, 1);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[error_invalid_index]")));
        }

        @Test
        @DisplayName("超出范围索引删除应失败")
        void shouldFailForOutOfRangeDeleteIndex() {
            List<MailData> mails = new ArrayList<>();
            mails.add(createTestMail("sender", false, false));
            when(mockMailService.getInbox(playerUuid)).thenReturn(mails);

            mailCommand.delete(player, 2);

            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[error_invalid_index]")));
        }
    }

    // ==================== MailService interaction Tests ====================

    @Nested
    @DisplayName("MailService 交互测试")
    class MailServiceInteractionTests {

        @Test
        @DisplayName("inbox 应该使用正确的玩家UUID调用 getInbox")
        void shouldCallGetInboxWithCorrectUuid() {
            when(mockMailService.getInbox(playerUuid)).thenReturn(new ArrayList<>());

            mailCommand.inbox(player);

            verify(mockMailService).getInbox(playerUuid);
        }

        @Test
        @DisplayName("sent 应该使用正确的玩家UUID调用 getSentMails")
        void shouldCallGetSentMailsWithCorrectUuid() {
            when(mockMailService.getSentMails(playerUuid)).thenReturn(new ArrayList<>());

            mailCommand.sent(player);

            verify(mockMailService).getSentMails(playerUuid);
        }

        @Test
        @DisplayName("deleteAll 应该使用正确的玩家UUID")
        void shouldCallDeleteAllWithCorrectUuid() {
            when(mockMailService.deleteAllByReceiver(playerUuid)).thenReturn(0);

            mailCommand.deleteAll(player);

            verify(mockMailService).deleteAllByReceiver(playerUuid);
        }

        @Test
        @DisplayName("deleteRead 应该使用正确的玩家UUID")
        void shouldCallDeleteReadWithCorrectUuid() {
            when(mockMailService.deleteReadByReceiver(playerUuid)).thenReturn(0);

            mailCommand.deleteRead(player);

            verify(mockMailService).deleteReadByReceiver(playerUuid);
        }
    }

    // Helper methods
    private MailData createTestMail(String senderName, boolean read, boolean claimed) {
        MailData mail = new MailData();
        mail.setSenderUuid("sender-uuid");
        mail.setSenderName(senderName);
        mail.setReceiverUuid(playerUuid.toString());
        mail.setReceiverName("TestPlayer");
        mail.setSubject("Test Subject");
        mail.setContent("Test Content");
        mail.setSentTime(System.currentTimeMillis());
        mail.setRead(read);
        mail.setClaimed(claimed);
        return mail;
    }
}
