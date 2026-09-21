package com.ultikits.plugins.mail.commands;

import com.ultikits.plugins.mail.service.MailService;
import com.ultikits.plugins.mail.utils.MockBukkitHelper;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Material;
import org.bukkit.conversations.Conversation;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.Field;
import java.util.List;
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

    private ServerMock server;
    private PlayerMock sender;
    private SendMailCommand command;
    private MailService mockMailService;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkitHelper.bootstrapServer();
        // An attachment that can no longer fit in the sender's inventory is dropped at their
        // location, which needs a world to drop into.
        server.addSimpleWorld("world");
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

    /**
     * Starts the single-attachment (non-admin) send path with one copper ingot in the sender's
     * main hand, then fills every inventory slot -- the state a player reaches by picking things
     * up while typing the mail's content, which is the only way an attachment return can find no
     * room. Both attachment-return paths in this class ({@code ContentPrompt.acceptInput}'s
     * refusal branch and the conversation-abandoned listener) are the same defect class as
     * {@code UltiKits/UltiMail#27}: the module hands the items back with
     * {@code Inventory#addItem} and discards what could not fit.
     */
    private void startSingleAttachmentSendAndFillTheInventory() {
        sender.getInventory().setItemInMainHand(new ItemStack(Material.COPPER_INGOT, 1));

        command.sendMailWithItems(sender, "ReceiverName", "TestSubject");

        assertThat(sender.getInventory().getItemInMainHand().getType())
            .as("the attachment path takes the item out of the sender's hand immediately")
            .isNotEqualTo(Material.COPPER_INGOT);
        assertThat(sender.isConversing())
            .as("the content conversation must have started, otherwise this test proves nothing")
            .isTrue();

        for (int slot = 0; slot < sender.getInventory().getSize(); slot++) {
            sender.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }
        assertThat(sender.getInventory().firstEmpty())
            .as("the sender must really have no room for the returned attachment")
            .isEqualTo(-1);
    }

    private int countDroppedCopper() {
        int total = 0;
        for (Item item : sender.getWorld().getEntitiesByClass(Item.class)) {
            if (item.getItemStack().getType() == Material.COPPER_INGOT) {
                total += item.getItemStack().getAmount();
            }
        }
        return total;
    }

    /** Every copper ingot anywhere in the sender's inventory, including armour and off-hand. */
    private int countCopperHeldBySender() {
        int total = 0;
        for (ItemStack stack : sender.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.COPPER_INGOT) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Drains the sender's queued messages and reports whether any of them was the "send
     * cancelled" notice. The queue also holds the prompt text and the local echo of whatever was
     * typed, so this looks for the marker rather than reading one particular position.
     */
    private boolean aCancelledNoticeReachedTheSender() {
        boolean found = false;
        Component next = sender.nextComponentMessage();
        while (next != null) {
            if (PlainTextComponentSerializer.plainText().serialize(next).contains("[send_cancelled]")) {
                found = true;
            }
            next = sender.nextComponentMessage();
        }
        return found;
    }

    @Test
    @DisplayName("发送被拒绝且背包已满时附件应掉落而不是被销毁")
    void aRefusedSendDropsTheAttachmentWhenTheSenderHasNoRoomLeft() {
        when(mockMailService.sendMail(any(Player.class), anyString(), anyString(), anyString(), any()))
            .thenReturn(false);
        startSingleAttachmentSendAndFillTheInventory();

        sender.acceptConversationInput("邮件内容");

        assertThat(countDroppedCopper())
            .as("a refusal is the last point that still holds the attachment, so an item that no "
                + "longer fits must be dropped rather than destroyed")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("取消对话且背包已满时附件应掉落而不是被销毁")
    void anAbandonedConversationDropsTheAttachmentWhenTheSenderHasNoRoomLeft() {
        startSingleAttachmentSendAndFillTheInventory();

        sender.acceptConversationInput("cancel");

        assertThat(sender.isConversing())
            .as("the typed cancel must really have ended the conversation")
            .isFalse();
        assertThat(countDroppedCopper())
            .as("a conversation that ends still holding the attachment must give it back, dropping "
                + "what no longer fits rather than destroying it")
            .isEqualTo(1);
    }

    /**
     * Every way the content conversation can end, measured against the item-conservation
     * invariant: <em>a conversation that is holding an attachment and ends without the mail having
     * taken it hands it back to its owner.</em>
     * <p>
     * <b>Why this is a nest of its own rather than one more cancel test.</b> The module used to
     * decide "the sender cancelled" in two independent places -- the conversation's own escape
     * sequence, which Bukkit implements as {@code ExactMatchConversationCanceller} and whose
     * {@code cancelBasedOnInput} is {@code input.equals(escapeSequence)}, i.e. case-SENSITIVE
     * ({@code javap -p -c} of that class in {@code paper-api-1.21.11}: the compare is
     * {@code invokevirtual java/lang/String.equals}), and {@code ContentPrompt.acceptInput}'s own
     * {@code equalsIgnoreCase("cancel")}. The attachment return was wired to the first of the two.
     * So {@code cancel} returned the item and {@code Cancel} destroyed it: the canceller did not
     * match, {@code acceptInput} ended the conversation itself, and
     * {@code Conversation.outputNextPrompt} then abandoned it with the no-canceller constructor,
     * making {@code ConversationAbandonedEvent.gracefulExit()} -- which is literally
     * {@code return canceller == null} -- true, which was the condition the return sat behind.
     * <p>
     * The tests below therefore do not check that the two cancel decisions agree on case. They
     * check the invariant on <em>every</em> termination path Bukkit has, because
     * {@code Conversation#abandon(ConversationAbandonedEvent)} is the single funnel all of them go
     * through (measured on the same jar: the canceller branch of {@code acceptInput} at offset 98,
     * {@code outputNextPrompt}'s no-canceller abandon at offset 16,
     * {@code InactivityConversationCanceller$1.run}'s timeout abandon at offset 81, and
     * {@code ConversationTracker.abandonAllConversations}'s abandon at offset 56 for both a
     * disconnect and a plugin-initiated abandon -- and that funnel is guarded by the
     * {@code abandoned} field at its offset 0, so it runs at most once per conversation).
     */
    @Nested
    @DisplayName("对话终止的每一条路径都必须归还附件")
    class EveryConversationExitPathTests {

        /**
         * Starts the single-attachment (non-admin) send path with one copper ingot, leaving the
         * sender's inventory otherwise empty so a returned attachment lands in it rather than on
         * the ground. The two pre-assertions are what stop every test in this nest from passing
         * vacuously: the ingot really left the sender, and the conversation really started.
         */
        private void startSingleAttachmentSendWithRoomToSpare() {
            sender.getInventory().clear();
            sender.getInventory().setItemInMainHand(new ItemStack(Material.COPPER_INGOT, 1));

            command.sendMailWithItems(sender, "ReceiverName", "TestSubject");

            assertThat(countCopperHeldBySender())
                .as("the command takes the attachment out of the sender's hand immediately, so "
                    + "before the conversation ends the sender holds none of it -- without this "
                    + "the assertions below could pass on an ingot that never left")
                .isZero();
            assertThat(sender.isConversing())
                .as("the content conversation must have started, otherwise nothing is being tested")
                .isTrue();
        }

        /**
         * The live conversation the sender is in. Bukkit exposes no accessor for it --
         * {@code Conversable} only lets a caller abandon a conversation it already holds -- so
         * MockBukkit's own tracker queue is read reflectively. This is needed for exactly one
         * exit path, the one a plugin owns rather than the player.
         */
        @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
        private Conversation conversationOfTheSender() throws Exception {
            Field trackerField = PlayerMock.class.getDeclaredField("conversationTracker");
            trackerField.setAccessible(true);
            Object tracker = trackerField.get(sender);
            Field queueField = tracker.getClass().getDeclaredField("conversationQueue");
            queueField.setAccessible(true);
            List<?> queue = (List<?>) queueField.get(tracker);
            assertThat(queue)
                .as("the sender must actually be in a tracked conversation")
                .isNotEmpty();
            return (Conversation) queue.get(0);
        }

        /**
         * Asserts the invariant: the conversation is over and the one copper ingot it was holding
         * exists again, either in the sender's inventory or on the ground. Counting both is what
         * makes this the conservation invariant rather than a symptom -- a fix that dropped the
         * ingot instead of inserting it would still pass, and a fix that destroyed it cannot.
         */
        private void assertTheConversationEndedAndTheAttachmentCameBack() {
            assertThat(sender.isConversing())
                .as("the conversation must really have ended")
                .isFalse();
            assertThat(countCopperHeldBySender() + countDroppedCopper())
                .as("the attachment left the sender when the command ran, so the only way it can "
                    + "exist now is if the ended conversation handed it back")
                .isEqualTo(1);
        }

        @Test
        @DisplayName("输入小写 cancel 结束对话：附件归还")
        void theCancelWordInLowerCaseGivesTheAttachmentBack() {
            startSingleAttachmentSendWithRoomToSpare();

            sender.acceptConversationInput("cancel");

            assertTheConversationEndedAndTheAttachmentCameBack();
            assertThat(aCancelledNoticeReachedTheSender())
                .as("a sender who cancelled must be told so")
                .isTrue();
        }

        @Test
        @DisplayName("输入首字母大写 Cancel 结束对话：附件归还（BL-01 的具体故障）")
        void theCancelWordCapitalisedGivesTheAttachmentBack() {
            startSingleAttachmentSendWithRoomToSpare();

            sender.acceptConversationInput("Cancel");

            assertTheConversationEndedAndTheAttachmentCameBack();
            assertThat(aCancelledNoticeReachedTheSender())
                .as("this spelling ends the conversation exactly like the lower-case one, so it "
                    + "must also tell the sender their send was cancelled")
                .isTrue();
        }

        @Test
        @DisplayName("输入全大写 CANCEL 结束对话：附件归还")
        void theCancelWordInUpperCaseGivesTheAttachmentBack() {
            startSingleAttachmentSendWithRoomToSpare();

            sender.acceptConversationInput("CANCEL");

            assertTheConversationEndedAndTheAttachmentCameBack();
            assertThat(aCancelledNoticeReachedTheSender()).isTrue();
        }

        @Test
        @DisplayName("内容提示超时结束对话：附件归还")
        void aTimedOutContentPromptGivesTheAttachmentBack() {
            startSingleAttachmentSendWithRoomToSpare();

            // ConversationFactory#withTimeout(120) installs an InactivityConversationCanceller
            // whose timer is scheduled as a sync delayed task of 120 * 20 ticks.
            server.getScheduler().performTicks(120L * 20L + 1L);

            assertTheConversationEndedAndTheAttachmentCameBack();
        }

        @Test
        @DisplayName("对话进行中掉线：附件归还")
        void disconnectingMidConversationGivesTheAttachmentBack() {
            startSingleAttachmentSendWithRoomToSpare();

            // PlayerMock#disconnect() calls ConversationTracker#abandonAllConversations() and then
            // fires PlayerQuitEvent. A real server does both too, in the opposite order:
            // PlayerList#remove(ServerPlayer, Component) on Paper 1.21.11 fires PlayerQuitEvent at
            // offset 57 and calls CraftPlayer#disconnect() -- which is where
            // abandonAllConversations() lives -- at offset 66. Either order conserves the item on a
            // real server, because PlayerList#save(ServerPlayer) is only reached at offset 183.
            sender.disconnect();

            assertTheConversationEndedAndTheAttachmentCameBack();
        }

        @Test
        @DisplayName("其他插件放弃该对话：附件归还")
        void anotherPluginAbandoningTheConversationGivesTheAttachmentBack() throws Exception {
            startSingleAttachmentSendWithRoomToSpare();

            conversationOfTheSender().abandon();

            assertTheConversationEndedAndTheAttachmentCameBack();
        }

        @Test
        @DisplayName("发送成功后结束对话：不得再归还一次（否则附件被复制）")
        void aSuccessfulSendDoesNotAlsoGiveTheAttachmentBack() {
            when(mockMailService.sendMail(any(Player.class), anyString(), anyString(), anyString(), any()))
                .thenReturn(true);
            startSingleAttachmentSendWithRoomToSpare();

            sender.acceptConversationInput("邮件内容");

            assertThat(sender.isConversing()).isFalse();
            assertThat(countCopperHeldBySender() + countDroppedCopper())
                .as("the mail took the attachment, so handing it back as well would duplicate it")
                .isZero();
            assertThat(aCancelledNoticeReachedTheSender())
                .as("a send that succeeded must not also report itself cancelled")
                .isFalse();
        }

        @Test
        @DisplayName("发送被拒绝后结束对话：附件恰好归还一次")
        void aRefusedSendGivesTheAttachmentBackExactlyOnce() {
            when(mockMailService.sendMail(any(Player.class), anyString(), anyString(), anyString(), any()))
                .thenReturn(false);
            startSingleAttachmentSendWithRoomToSpare();

            sender.acceptConversationInput("邮件内容");

            assertTheConversationEndedAndTheAttachmentCameBack();
            assertThat(aCancelledNoticeReachedTheSender())
                .as("MailService has already told the sender why it refused; a second notice would "
                    + "make one refusal look like two events")
                .isFalse();
        }
    }
}
