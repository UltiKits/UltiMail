package com.ultikits.plugins.mail.commands;

import com.ultikits.plugins.mail.service.MailService;
import com.ultikits.plugins.mail.utils.MockBukkitHelper;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test driving {@link SendMailCommand} through a real MockBukkit
 * {@link org.bukkit.conversations.Conversation}, added for the phase 13 review's WR-02: every
 * other test touching {@code ContentPrompt.acceptInput} (see {@link SendMailCommandTest} and
 * {@code MailServiceTest}) uses a pure Mockito {@code Player} whose {@code beginConversation(...)}
 * is itself an unstubbed mock -- the {@code Conversation} object is captured and inspected, or
 * {@code acceptInput} is invoked directly via reflection, but the real Bukkit conversation
 * lifecycle (prompt output, local echo, modal-conversation gating) never actually runs. Those
 * tests prove "the correct API was called"; this one proves "the message a real client would see
 * actually arrives."
 * <p>
 * {@link PlayerMock#sendMessage(String)} is disassembled-confirmed (see the phase 13 review) to be
 * an unconditional no-op while {@code ConversationTracker.isConversingModaly()} is {@code true};
 * {@link PlayerMock#sendRawMessage(String)} bypasses that check unconditionally. Because
 * {@code ConversationTracker.acceptConversationInput(...)} only removes a finished conversation
 * from its queue *after* {@code Conversation.acceptInput(...)} returns, the player is still
 * conversing modally for the entire duration of {@code ContentPrompt.acceptInput}'s own body --
 * so a message observed here could only have arrived via {@code sendRawMessage}.
 */
@DisplayName("SendMailCommand 真实 Conversation 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SendMailCommandConversationIntegrationTest {

    private PlayerMock sender;
    private SendMailCommand command;
    private MailService mockMailService;

    @BeforeEach
    void setUp() throws Exception {
        ServerMock server = MockBukkitHelper.bootstrapServer();
        PluginMock plugin = MockBukkit.createMockPlugin();
        sender = server.addPlayer("sender");

        mockMailService = mock(MailService.class);
        UltiToolsPlugin ultiPlugin = TestHelper.mockUltiToolsPlugin();

        command = new SendMailCommand(mockMailService, plugin);
        TestHelper.injectField(command, "ultiPlugin", ultiPlugin);
    }

    @AfterEach
    void tearDown() {
        TestHelper.cleanupMocks();
        MockBukkitHelper.safeUnmock();
    }

    /**
     * Polls the next queued message and fails loudly if none was queued, rather than returning
     * {@code null} and letting a later assertion produce a confusing failure.
     */
    private String nextPlainMessage() {
        Component component = sender.nextComponentMessage();
        assertThat(component).as("expected a queued message but found none").isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    @DisplayName("发送成功的确认消息应在对话进行中真正送达玩家")
    void successMessageReachesThePlayerWhileConversing() {
        when(mockMailService.sendMail(any(Player.class), anyString(), anyString(), anyString(), any()))
            .thenReturn(true);

        command.sendMail(sender, "ReceiverName", "TestSubject");
        assertThat(sender.isConversing())
            .as("beginConversation() on a real Conversable must actually start the conversation")
            .isTrue();

        // Conversation.begin() -> outputNextPrompt() already queued the prompt text
        // ("[input_content_prompt]") before the sender has typed anything.
        assertThat(nextPlainMessage()).contains("[input_content_prompt]");

        sender.acceptConversationInput("邮件内容");

        // ConversationFactory defaults localEchoEnabled=true, so Conversation.acceptInput(...)
        // echoes the raw input back to the sender before handing it to ContentPrompt.acceptInput.
        assertThat(nextPlainMessage()).contains("邮件内容");

        // This is the property the switch from sendMessage to sendRawMessage exists to
        // guarantee: at the moment ContentPrompt.acceptInput ran, the sender was still first in
        // ConversationTracker's queue and Conversation.isModal() defaults true, so
        // isConversingModaly() was true -- exactly the state that makes sendMessage a no-op.
        // A message reaching this queue could only have used sendRawMessage.
        String outcome = nextPlainMessage();
        assertThat(outcome)
            .as("the success message must actually reach the player, not merely be attempted")
            .contains("[mail_sent_success]");
    }

    @Test
    @DisplayName("发送失败时不应在 acceptInput 中额外送达任何消息")
    void noExtraMessageReachesThePlayerOnFailure() {
        when(mockMailService.sendMail(any(Player.class), anyString(), anyString(), anyString(), any()))
            .thenReturn(false);

        command.sendMail(sender, "ReceiverName", "TestSubject");
        nextPlainMessage(); // the prompt text

        sender.acceptConversationInput("邮件内容");
        nextPlainMessage(); // the local echo of the typed input

        // MailService.sendMail(...) is mocked here and queues nothing on its own; acceptInput's
        // own contract is to add nothing further on a refusal (see
        // SendMailCommandTest#shouldNotSendSuccessMessageOnFailure for the Mockito-level version
        // of this same assertion) -- so no further message should have been queued.
        assertThat(sender.nextComponentMessage())
            .as("a refusal must not queue any message from acceptInput itself")
            .isNull();
    }
}
