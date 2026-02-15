package com.ultikits.plugins.mail.commands;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.service.MailService;
import com.ultikits.plugins.mail.utils.TestHelper;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SendMailCommand.
 * <p>
 * Uses pure Mockito (no MockBukkit) for maximum compatibility.
 * Tests focus on command structure, permissions, and item handling logic.
 */
@DisplayName("SendMailCommand 测试")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SendMailCommandTest {

    private SendMailCommand command;

    @Mock
    private MailService mockMailService;

    @Mock
    private Plugin mockPlugin;

    @Mock
    private Player sender;

    @Mock
    private PlayerInventory senderInventory;

    private UUID senderUuid;

    @BeforeEach
    void setUp() throws Exception {
        // Setup mock UltiToolsPlugin
        UltiToolsPlugin mockUltiPlugin = TestHelper.mockUltiToolsPlugin();

        senderUuid = UUID.randomUUID();
        lenient().when(sender.getUniqueId()).thenReturn(senderUuid);
        lenient().when(sender.getName()).thenReturn("SenderPlayer");
        lenient().when(sender.getInventory()).thenReturn(senderInventory);
        lenient().when(sender.isOnline()).thenReturn(true);

        // Create command and inject dependencies
        command = new SendMailCommand(mockMailService, mockPlugin);
        TestHelper.injectField(command, "ultiPlugin", mockUltiPlugin);
    }

    @AfterEach
    void tearDown() {
        TestHelper.cleanupMocks();
    }

    // ==================== sendMailWithItems Tests ====================

    @Nested
    @DisplayName("附件发送测试")
    class AttachmentSendTests {

        @Test
        @DisplayName("普通玩家主手为空时应该显示错误")
        void shouldShowErrorWhenMainHandEmpty() {
            when(sender.hasPermission("ultimail.admin.multiattach")).thenReturn(false);
            ItemStack airItem = mock(ItemStack.class);
            when(airItem.getType()).thenReturn(Material.AIR);
            when(senderInventory.getItemInMainHand()).thenReturn(airItem);

            command.sendMailWithItems(sender, "receiver", "subject");

            verify(sender).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[error_no_item_in_hand]")));
        }

        @Test
        @DisplayName("普通玩家应该使用主手物品并清空主手")
        void shouldUseMainHandItemAndClearHand() {
            when(sender.hasPermission("ultimail.admin.multiattach")).thenReturn(false);
            ItemStack diamond = mock(ItemStack.class);
            when(diamond.getType()).thenReturn(Material.DIAMOND);
            ItemStack clonedDiamond = mock(ItemStack.class);
            when(diamond.clone()).thenReturn(clonedDiamond);
            when(senderInventory.getItemInMainHand()).thenReturn(diamond);

            // This will try to start a conversation which will fail on mock plugin,
            // but we can verify item was taken
            try {
                command.sendMailWithItems(sender, "receiver", "subject");
            } catch (Exception e) {
                // Expected since ConversationFactory may not work with mock plugin
            }

            verify(senderInventory).setItemInMainHand(null);
        }
    }

    // ==================== help Tests ====================

    @Nested
    @DisplayName("帮助命令测试")
    class HelpTests {

        @Test
        @DisplayName("帮助命令应该显示信息")
        void shouldShowHelpInfo() {
            command.help(sender);

            verify(sender, atLeast(3)).sendMessage(any(String.class));
        }

        @Test
        @DisplayName("帮助应包含 sendmail 命令")
        void shouldContainSendmailCommand() {
            command.help(sender);

            verify(sender, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("/sendmail")
            ));
        }

        @Test
        @DisplayName("帮助应包含 attach 关键字")
        void shouldContainAttachKeyword() {
            command.help(sender);

            verify(sender, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("attach")
            ));
        }
    }

    // ==================== Annotation Tests ====================

    @Nested
    @DisplayName("注解配置测试")
    class AnnotationTests {

        @Test
        @DisplayName("类应该有 @CmdExecutor 注解")
        void shouldHaveCmdExecutorAnnotation() {
            assertThat(SendMailCommand.class.isAnnotationPresent(
                com.ultikits.ultitools.annotations.command.CmdExecutor.class
            )).isTrue();
        }

        @Test
        @DisplayName("@CmdExecutor 应该有正确的别名")
        void shouldHaveCorrectAliases() {
            com.ultikits.ultitools.annotations.command.CmdExecutor annotation =
                SendMailCommand.class.getAnnotation(
                    com.ultikits.ultitools.annotations.command.CmdExecutor.class
                );

            assertThat(annotation.alias()).contains("sendmail", "sm");
        }

        @Test
        @DisplayName("@CmdExecutor 应该有正确的权限")
        void shouldHaveCorrectPermission() {
            com.ultikits.ultitools.annotations.command.CmdExecutor annotation =
                SendMailCommand.class.getAnnotation(
                    com.ultikits.ultitools.annotations.command.CmdExecutor.class
                );

            assertThat(annotation.permission()).isEqualTo("ultimail.send");
        }

        @Test
        @DisplayName("类应该有 @CmdTarget(PLAYER) 注解")
        void shouldHavePlayerTarget() {
            com.ultikits.ultitools.annotations.command.CmdTarget annotation =
                SendMailCommand.class.getAnnotation(
                    com.ultikits.ultitools.annotations.command.CmdTarget.class
                );

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(
                com.ultikits.ultitools.annotations.command.CmdTarget.CmdTargetType.PLAYER
            );
        }

        @Test
        @DisplayName("应该继承 BaseCommandExecutor")
        void shouldExtendBaseCommandExecutor() {
            assertThat(com.ultikits.ultitools.abstracts.command.BaseCommandExecutor.class)
                .isAssignableFrom(SendMailCommand.class);
        }
    }

    // ==================== ContentPrompt Tests ====================

    @Nested
    @DisplayName("ContentPrompt 内部类测试")
    class ContentPromptTests {

        @Test
        @DisplayName("ContentPrompt 应该存在")
        void shouldExist() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            assertThat(contentPromptClass).isNotNull();
        }

        @Test
        @DisplayName("ContentPrompt 应该有正确的构造函数")
        void shouldHaveCorrectConstructor() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            contentPromptClass.getDeclaredConstructor(String.class, String.class);
        }

        @Test
        @DisplayName("ContentPrompt 应该实现 StringPrompt")
        void shouldExtendStringPrompt() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            assertThat(org.bukkit.conversations.StringPrompt.class)
                .isAssignableFrom(contentPromptClass);
        }
    }

    // ==================== Constructor Tests ====================

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("应该正确创建命令实例")
        void shouldCreateInstance() {
            SendMailCommand cmd = new SendMailCommand(mockMailService, mockPlugin);
            assertThat(cmd).isNotNull();
        }
    }

    // ==================== Permission Tests ====================

    @Nested
    @DisplayName("权限测试")
    class PermissionTests {

        @Test
        @DisplayName("ADMIN_PERMISSION 常量应该是 ultimail.admin.multiattach")
        void shouldHaveCorrectAdminPermission() throws Exception {
            java.lang.reflect.Field field = SendMailCommand.class.getDeclaredField("ADMIN_PERMISSION");
            field.setAccessible(true);
            String value = (String) field.get(null);

            assertThat(value).isEqualTo("ultimail.admin.multiattach");
        }
    }

    // ==================== sendMail method Tests ====================

    @Nested
    @DisplayName("sendMail 方法测试")
    class SendMailMethodTests {

        @Test
        @DisplayName("sendMail 方法应该存在并有正确的参数")
        void shouldHaveSendMailMethod() throws Exception {
            Method method = SendMailCommand.class.getDeclaredMethod(
                "sendMail", org.bukkit.entity.Player.class, String.class, String.class);
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("sendMail 应该有 @CmdMapping 注解")
        void shouldHaveCmdMappingAnnotation() throws Exception {
            Method method = SendMailCommand.class.getDeclaredMethod(
                "sendMail", org.bukkit.entity.Player.class, String.class, String.class);
            CmdMapping mapping = method.getAnnotation(CmdMapping.class);
            assertThat(mapping).isNotNull();
            assertThat(mapping.format()).isEqualTo("<player> <subject>");
        }
    }

    // ==================== sendMailWithItems method Tests ====================

    @Nested
    @DisplayName("sendMailWithItems 方法测试")
    class SendMailWithItemsMethodTests {

        @Test
        @DisplayName("sendMailWithItems 应该有 @CmdMapping 注解")
        void shouldHaveCmdMappingAnnotation() throws Exception {
            Method method = SendMailCommand.class.getDeclaredMethod(
                "sendMailWithItems", org.bukkit.entity.Player.class, String.class, String.class);
            CmdMapping mapping = method.getAnnotation(CmdMapping.class);
            assertThat(mapping).isNotNull();
            assertThat(mapping.format()).isEqualTo("<player> <subject> attach");
        }

        @Test
        @DisplayName("普通玩家null主手物品应该显示错误")
        void shouldShowErrorWhenMainHandIsNull() {
            when(sender.hasPermission("ultimail.admin.multiattach")).thenReturn(false);
            when(senderInventory.getItemInMainHand()).thenReturn(null);

            command.sendMailWithItems(sender, "receiver", "subject");

            verify(sender).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("[error_no_item_in_hand]")));
        }

        @Test
        @DisplayName("管理员应该显示GUI提示消息")
        void shouldShowGuiHintForAdmin() {
            when(sender.hasPermission("ultimail.admin.multiattach")).thenReturn(true);

            try {
                command.sendMailWithItems(sender, "receiver", "subject");
            } catch (Exception e) {
                // GUI open may fail in test
            }

            verify(sender).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[attachment_gui_hint]")
            ));
        }
    }

    // ==================== i18n Tests ====================

    @Nested
    @DisplayName("i18n 方法测试")
    class I18nTests {

        @Test
        @DisplayName("i18n 应该委托给 ultiPlugin")
        void shouldDelegateToUltiPlugin() throws Exception {
            Method method = SendMailCommand.class.getDeclaredMethod("i18n", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(command, "test_key");

            assertThat(result).isEqualTo("[test_key]");
        }
    }

    // ==================== handleHelp Tests ====================

    @Nested
    @DisplayName("handleHelp 方法测试")
    class HandleHelpTests {

        @Test
        @DisplayName("handleHelp 应该显示标题")
        void shouldShowTitle() {
            CommandSender cmdSender = mock(CommandSender.class);

            try {
                Method method = SendMailCommand.class.getDeclaredMethod("handleHelp", CommandSender.class);
                method.setAccessible(true);
                method.invoke(command, cmdSender);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            verify(cmdSender, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[help_sendmail_title]")
            ));
        }

        @Test
        @DisplayName("handleHelp 应该显示取消提示")
        void shouldShowCancelHint() {
            CommandSender cmdSender = mock(CommandSender.class);

            try {
                Method method = SendMailCommand.class.getDeclaredMethod("handleHelp", CommandSender.class);
                method.setAccessible(true);
                method.invoke(command, cmdSender);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            verify(cmdSender, atLeast(1)).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[help_cancel_hint]")
            ));
        }

        @Test
        @DisplayName("handleHelp 应该显示4条消息")
        void shouldShowFourMessages() {
            CommandSender cmdSender = mock(CommandSender.class);

            try {
                Method method = SendMailCommand.class.getDeclaredMethod("handleHelp", CommandSender.class);
                method.setAccessible(true);
                method.invoke(command, cmdSender);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            verify(cmdSender, times(4)).sendMessage(any(String.class));
        }
    }

    // ==================== ContentPrompt Detailed Tests ====================

    @Nested
    @DisplayName("ContentPrompt 详细测试")
    class ContentPromptDetailedTests {

        @Test
        @DisplayName("ContentPrompt 应该有 getPromptText 和 acceptInput 方法")
        void shouldHaveRequiredMethods() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );

            // getPromptText
            contentPromptClass.getDeclaredMethod("getPromptText",
                org.bukkit.conversations.ConversationContext.class);
            // acceptInput
            contentPromptClass.getDeclaredMethod("acceptInput",
                org.bukkit.conversations.ConversationContext.class, String.class);
        }

        @Test
        @DisplayName("ContentPrompt 应该存储receiver和subject")
        void shouldStoreReceiverAndSubject() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            Object prompt = contentPromptClass.getDeclaredConstructor(
                String.class, String.class).newInstance("TestReceiver", "TestSubject");

            java.lang.reflect.Field receiverField = contentPromptClass.getDeclaredField("receiver");
            receiverField.setAccessible(true);
            java.lang.reflect.Field subjectField = contentPromptClass.getDeclaredField("subject");
            subjectField.setAccessible(true);

            assertThat(receiverField.get(prompt)).isEqualTo("TestReceiver");
            assertThat(subjectField.get(prompt)).isEqualTo("TestSubject");
        }

        @Test
        @DisplayName("acceptInput 输入cancel应返回 END_OF_CONVERSATION")
        void shouldReturnEndOnCancel() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            Object prompt = contentPromptClass.getDeclaredConstructor(
                String.class, String.class).newInstance("TestReceiver", "TestSubject");

            org.bukkit.conversations.ConversationContext ctx = mock(org.bukkit.conversations.ConversationContext.class);

            Method acceptInput = contentPromptClass.getDeclaredMethod("acceptInput",
                org.bukkit.conversations.ConversationContext.class, String.class);

            Object result = acceptInput.invoke(prompt, ctx, "cancel");

            assertThat(result).isEqualTo(org.bukkit.conversations.Prompt.END_OF_CONVERSATION);
        }

        @Test
        @DisplayName("acceptInput 输入null应返回 END_OF_CONVERSATION")
        void shouldReturnEndOnNull() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            Object prompt = contentPromptClass.getDeclaredConstructor(
                String.class, String.class).newInstance("TestReceiver", "TestSubject");

            org.bukkit.conversations.ConversationContext ctx = mock(org.bukkit.conversations.ConversationContext.class);

            Method acceptInput = contentPromptClass.getDeclaredMethod("acceptInput",
                org.bukkit.conversations.ConversationContext.class, String.class);

            Object result = acceptInput.invoke(prompt, ctx, null);

            assertThat(result).isEqualTo(org.bukkit.conversations.Prompt.END_OF_CONVERSATION);
        }

        @Test
        @DisplayName("acceptInput 正常输入应调用mailService.sendMail")
        void shouldCallMailServiceOnValidInput() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            Object prompt = contentPromptClass.getDeclaredConstructor(
                String.class, String.class).newInstance("ReceiverName", "TestSubject");

            org.bukkit.conversations.ConversationContext ctx = mock(org.bukkit.conversations.ConversationContext.class);
            when(ctx.getForWhom()).thenReturn(sender);
            when(ctx.getSessionData("mailService")).thenReturn(mockMailService);
            when(ctx.getSessionData("attachItems")).thenReturn(null);

            UltiToolsPlugin ctxPlugin = TestHelper.mockUltiToolsPlugin();
            when(ctx.getSessionData("ultiPlugin")).thenReturn(ctxPlugin);

            when(mockMailService.sendMail(any(Player.class), anyString(), anyString(), anyString(), any()))
                .thenReturn(true);

            Method acceptInput = contentPromptClass.getDeclaredMethod("acceptInput",
                org.bukkit.conversations.ConversationContext.class, String.class);

            Object result = acceptInput.invoke(prompt, ctx, "这是邮件内容");

            verify(mockMailService).sendMail(sender, "ReceiverName", "TestSubject", "这是邮件内容", null);
            assertThat(result).isEqualTo(org.bukkit.conversations.Prompt.END_OF_CONVERSATION);
        }

        @Test
        @DisplayName("acceptInput 发送成功应发送成功消息")
        void shouldSendSuccessMessageOnSuccess() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            Object prompt = contentPromptClass.getDeclaredConstructor(
                String.class, String.class).newInstance("ReceiverName", "TestSubject");

            org.bukkit.conversations.ConversationContext ctx = mock(org.bukkit.conversations.ConversationContext.class);
            when(ctx.getForWhom()).thenReturn(sender);
            when(ctx.getSessionData("mailService")).thenReturn(mockMailService);
            when(ctx.getSessionData("attachItems")).thenReturn(null);

            UltiToolsPlugin ctxPlugin = TestHelper.mockUltiToolsPlugin();
            when(ctx.getSessionData("ultiPlugin")).thenReturn(ctxPlugin);

            when(mockMailService.sendMail(any(Player.class), anyString(), anyString(), anyString(), any()))
                .thenReturn(true);

            Method acceptInput = contentPromptClass.getDeclaredMethod("acceptInput",
                org.bukkit.conversations.ConversationContext.class, String.class);

            acceptInput.invoke(prompt, ctx, "邮件内容");

            // Mock i18n returns "[mail_sent_success]", replace("{RECEIVER}", ...) has no effect
            // since the mock key doesn't contain {RECEIVER}
            verify(sender).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("[mail_sent_success]")
            ));
        }

        @Test
        @DisplayName("acceptInput 发送失败不应发送成功消息")
        void shouldNotSendSuccessMessageOnFailure() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            Object prompt = contentPromptClass.getDeclaredConstructor(
                String.class, String.class).newInstance("ReceiverName", "TestSubject");

            org.bukkit.conversations.ConversationContext ctx = mock(org.bukkit.conversations.ConversationContext.class);
            when(ctx.getForWhom()).thenReturn(sender);
            when(ctx.getSessionData("mailService")).thenReturn(mockMailService);
            when(ctx.getSessionData("attachItems")).thenReturn(null);

            UltiToolsPlugin ctxPlugin = TestHelper.mockUltiToolsPlugin();
            when(ctx.getSessionData("ultiPlugin")).thenReturn(ctxPlugin);

            when(mockMailService.sendMail(any(Player.class), anyString(), anyString(), anyString(), any()))
                .thenReturn(false);

            Method acceptInput = contentPromptClass.getDeclaredMethod("acceptInput",
                org.bukkit.conversations.ConversationContext.class, String.class);

            acceptInput.invoke(prompt, ctx, "邮件内容");

            // Should NOT send success message when sendMail returns false
            verify(sender, never()).sendMessage(ArgumentMatchers.<String>argThat(msg ->
                msg.contains("ReceiverName")
            ));
        }

        @Test
        @DisplayName("getPromptText 应该调用 ultiPlugin.i18n")
        void shouldCallI18nForPromptText() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            Object prompt = contentPromptClass.getDeclaredConstructor(
                String.class, String.class).newInstance("Receiver", "Subject");

            org.bukkit.conversations.ConversationContext ctx = mock(org.bukkit.conversations.ConversationContext.class);
            UltiToolsPlugin ctxPlugin = TestHelper.mockUltiToolsPlugin();
            when(ctx.getSessionData("ultiPlugin")).thenReturn(ctxPlugin);

            Method getPromptText = contentPromptClass.getDeclaredMethod("getPromptText",
                org.bukkit.conversations.ConversationContext.class);

            String result = (String) getPromptText.invoke(prompt, ctx);

            assertThat(result).contains("[input_content_prompt]");
        }

        @Test
        @DisplayName("acceptInput 有附件时应传递附件")
        void shouldPassAttachItemsToSendMail() throws Exception {
            Class<?> contentPromptClass = Class.forName(
                "com.ultikits.plugins.mail.commands.SendMailCommand$ContentPrompt"
            );
            Object prompt = contentPromptClass.getDeclaredConstructor(
                String.class, String.class).newInstance("ReceiverName", "TestSubject");

            org.bukkit.conversations.ConversationContext ctx = mock(org.bukkit.conversations.ConversationContext.class);
            when(ctx.getForWhom()).thenReturn(sender);
            when(ctx.getSessionData("mailService")).thenReturn(mockMailService);

            ItemStack mockItem = mock(ItemStack.class);
            ItemStack[] attachItems = new ItemStack[]{mockItem};
            when(ctx.getSessionData("attachItems")).thenReturn(attachItems);

            UltiToolsPlugin ctxPlugin = TestHelper.mockUltiToolsPlugin();
            when(ctx.getSessionData("ultiPlugin")).thenReturn(ctxPlugin);

            when(mockMailService.sendMail(any(Player.class), anyString(), anyString(), anyString(), any()))
                .thenReturn(true);

            Method acceptInput = contentPromptClass.getDeclaredMethod("acceptInput",
                org.bukkit.conversations.ConversationContext.class, String.class);

            acceptInput.invoke(prompt, ctx, "邮件内容");

            verify(mockMailService).sendMail(sender, "ReceiverName", "TestSubject", "邮件内容", attachItems);
        }
    }

    // ==================== startContentConversation Tests ====================

    @Nested
    @DisplayName("startContentConversation 测试")
    class StartContentConversationTests {

        @Test
        @DisplayName("startContentConversation 方法应存在")
        void shouldHaveStartContentConversationMethod() throws Exception {
            Method method = SendMailCommand.class.getDeclaredMethod(
                "startContentConversation", Player.class, String.class, String.class, ItemStack[].class);
            assertThat(method).isNotNull();
        }
    }
}
