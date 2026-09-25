package com.ultikits.plugins.mail.commands;

import com.ultikits.plugins.mail.gui.AttachmentSelectorPage;
import com.ultikits.plugins.mail.service.MailService;
import com.ultikits.plugins.mail.util.ItemReturns;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

/**
 * Send mail command executor.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"sendmail", "sm"},
    permission = "ultimail.send",
    description = "command_description_sendmail"
)
public class SendMailCommand extends BaseCommandExecutor {

    private static final String ADMIN_PERMISSION = "ultimail.admin.multiattach";

    /** The word that ends the content prompt without sending, in any capitalisation. */
    private static final String CANCEL_WORD = "cancel";

    /**
     * Session key under which the conversation holds the attachment while it asks for the mail's
     * content.
     * <p>
     * <b>This entry is the conversation's custody of those items, and clearing it is how custody
     * is transferred.</b> It is set when the conversation starts, cleared by
     * {@link ContentPrompt#acceptInput} the moment the mail has accepted the items, and read by
     * {@link #settleEndedConversation} -- which hands back whatever is still here -- when the
     * conversation ends by any route at all.
     */
    private static final String SESSION_ATTACHMENT = "attachItems";

    /** Session key holding the {@link MailService} this conversation sends through. */
    private static final String SESSION_MAIL_SERVICE = "mailService";

    /** Session key holding the module instance this conversation localises text with. */
    private static final String SESSION_ULTI_PLUGIN = "ultiPlugin";

    /**
     * Session key set once the sender has been told the outcome of their send -- whether the mail
     * went out or the service refused it. Its only job is to keep
     * {@link #settleEndedConversation} from reporting a send that reached a decision as
     * "cancelled": a refusal has already been explained to the sender by
     * {@code MailService#sendMail}, and a success has already been confirmed.
     */
    private static final String SESSION_OUTCOME_REPORTED = "outcomeReported";

    @Autowired
    private UltiToolsPlugin ultiPlugin;

    private final MailService mailService;
    private final Plugin plugin;

    public SendMailCommand(MailService mailService, Plugin plugin) {
        this.mailService = mailService;
        this.plugin = plugin;
    }
    
    @CmdMapping(format = "<player> <subject>")
    public void sendMail(@CmdSender Player sender, @CmdParam("player") String receiver, @CmdParam("subject") String subject) {
        // Exactly the same conversation as the attachment path, carrying no attachment. Building
        // it here a second time was how the two paths came to differ (UltiKits/UltiMail#27's
        // gate-1 BL-01: only one of the two ever returned items).
        startContentConversation(sender, receiver, subject, null);
    }

    @CmdMapping(format = "<player> <subject> attach")
    public void sendMailWithItems(@CmdSender Player sender, @CmdParam("player") String receiver, @CmdParam("subject") String subject) {
        // Check if admin has multi-attach permission
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            // Open multi-attachment GUI for admins
            sender.sendMessage(ChatColor.GREEN + i18n("attachment_gui_hint"));
            
            int maxItems = 45; // Default max items for GUI
            AttachmentSelectorPage gui = new AttachmentSelectorPage(sender, maxItems, ultiPlugin,
                items -> {
                    // Filter out null items
                    if (items == null) {
                        startContentConversation(sender, receiver, subject, null);
                        return;
                    }
                    ItemStack[] validItems = Arrays.stream(items)
                        .filter(item -> item != null && !item.getType().isAir())
                        .toArray(ItemStack[]::new);
                    
                    if (validItems.length == 0) {
                        // No items selected, send without attachment
                        startContentConversation(sender, receiver, subject, null);
                    } else {
                        startContentConversation(sender, receiver, subject, validItems);
                    }
                },
                () -> {
                    sender.sendMessage(ChatColor.YELLOW + i18n("send_cancelled"));
                });
            gui.open();
        } else {
            // Regular players: use main hand item only
            ItemStack item = sender.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                sender.sendMessage(ChatColor.RED + i18n("error_no_item_in_hand"));
                return;
            }
            
            // Start conversation with single attachment
            ItemStack[] items = new ItemStack[]{item.clone()};
            sender.getInventory().setItemInMainHand(null);
            
            startContentConversation(sender, receiver, subject, items);
        }
    }
    
    /**
     * Starts the conversation that asks for the mail's content, holding {@code items} (which may
     * be {@code null}) in the conversation's own session data until either the mail takes them or
     * the conversation ends.
     *
     * @param sender   the player sending the mail
     * @param receiver the receiver's name, as typed
     * @param subject  the mail's subject, as typed
     * @param items    the attachment this send is carrying, or {@code null} for none
     */
    private void startContentConversation(Player sender, String receiver, String subject, ItemStack[] items) {
        ConversationFactory factory = new ConversationFactory(plugin)
            .withFirstPrompt(new ContentPrompt(receiver, subject))
            .withEscapeSequence(CANCEL_WORD)
            .withTimeout(120)
            .thatExcludesNonPlayersWithMessage(i18n("error_player_only"))
            .addConversationAbandonedListener(this::settleEndedConversation);

        Conversation conversation = factory.buildConversation(sender);
        conversation.getContext().setSessionData(SESSION_MAIL_SERVICE, mailService);
        conversation.getContext().setSessionData(SESSION_ULTI_PLUGIN, ultiPlugin);
        conversation.getContext().setSessionData(SESSION_ATTACHMENT, items);
        conversation.begin();
    }

    /**
     * Settles a content conversation that has ended, whatever ended it.
     * <p>
     * <b>The invariant this method exists to hold:</b> if a conversation that is holding an
     * attachment ends without the mail having taken it, the attachment goes back to its owner.
     * Nothing here asks <em>why</em> the conversation ended, and that is deliberate. The previous
     * shape returned the items only when {@code ConversationAbandonedEvent#gracefulExit()} was
     * false -- that method is literally {@code return canceller == null} -- which made the return
     * depend on which of the module's two independent "the sender cancelled" decisions had fired:
     * the escape sequence, which Bukkit implements as a case-SENSITIVE
     * {@code ExactMatchConversationCanceller}, or {@link ContentPrompt#acceptInput}'s own
     * case-INSENSITIVE test. Typing {@code Cancel} took the second, which abandons through
     * {@code Conversation#outputNextPrompt}'s no-canceller constructor, so
     * {@code gracefulExit()} was true and the attachment was destroyed
     * ({@code UltiKits/UltiMail#27}, gate-1 BL-01).
     * <p>
     * Wiring the return to the conversation ENDING instead covers every route at once, including
     * routes neither cancel decision knows about: the 120 s inactivity timeout, the owner
     * disconnecting, and another plugin calling {@code Conversation#abandon()}. All of them reach
     * {@code Conversation#abandon(ConversationAbandonedEvent)}, which notifies this listener and
     * is guarded by its own {@code abandoned} field, so this method runs at most once per
     * conversation. Custody is tracked in the session data rather than in a flag:
     * {@link #SESSION_ATTACHMENT} holds what the conversation still owes the sender, and the only
     * thing that clears it is the mail actually accepting the items.
     * <p>
     * Anything the sender's inventory can no longer hold -- they may well have filled it while
     * typing -- is dropped at their feet rather than discarded, see
     * {@link ItemReturns#giveOrDrop}.
     *
     * @param event the abandonment event Bukkit raised for the ended conversation
     */
    private void settleEndedConversation(ConversationAbandonedEvent event) {
        ConversationContext context = event.getContext();
        ItemStack[] held = (ItemStack[]) context.getSessionData(SESSION_ATTACHMENT);
        context.setSessionData(SESSION_ATTACHMENT, null);
        if (held != null && context.getForWhom() instanceof Player) {
            ItemReturns.giveOrDrop((Player) context.getForWhom(), held);
        }
        if (context.getSessionData(SESSION_OUTCOME_REPORTED) == null) {
            // sendRawMessage, not sendMessage: see ContentPrompt.acceptInput's own note. A
            // conversation can be abandoned from inside Conversation.acceptInput, while the
            // sender is still counted as conversing modally.
            context.getForWhom().sendRawMessage(ChatColor.RED + i18n("send_cancelled"));
        }
    }

    @CmdMapping(format = "")
    public void help(@CmdSender Player player) {
        handleHelp(player);
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + i18n("help_sendmail_title") + " ===");
        sender.sendMessage(ChatColor.YELLOW + "/sendmail <" + i18n("arg_player") + "> <" + i18n("arg_subject") + ">" 
            + ChatColor.WHITE + " - " + i18n("help_sendmail_text"));
        // help_sendmail_attach already holds the whole line ("/sendmail <player> <subject> attach - ..."),
        // so it is shown once, the command part yellow and the description white.
        String attach = i18n("help_sendmail_attach");
        int dash = attach.indexOf(" - ");
        sender.sendMessage(dash < 0 ? ChatColor.YELLOW + attach
            : ChatColor.YELLOW + attach.substring(0, dash) + ChatColor.WHITE + attach.substring(dash));
        sender.sendMessage(ChatColor.GRAY + i18n("help_cancel_hint"));
    }
    
    private String i18n(String key) {
        return ultiPlugin.i18n(key);
    }

    /**
     * Content input prompt.
     */
    private static class ContentPrompt extends StringPrompt {
        private final String receiver;
        private final String subject;

        public ContentPrompt(String receiver, String subject) {
            this.receiver = receiver;
            this.subject = subject;
        }

        @Override
        public String getPromptText(ConversationContext context) {
            UltiToolsPlugin p = (UltiToolsPlugin) context.getSessionData("ultiPlugin");
            return ChatColor.YELLOW + p.i18n("input_content_prompt");
        }

        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if (input == null || input.equalsIgnoreCase(CANCEL_WORD)) {
                // Deliberately does nothing else. Ending the conversation is what gives the
                // attachment back and what tells the sender they cancelled, in
                // SendMailCommand#settleEndedConversation -- so this branch cannot get either
                // of those wrong, and neither can the escape sequence, which never reaches here.
                return Prompt.END_OF_CONVERSATION;
            }

            Player sender = (Player) context.getForWhom();
            MailService service = (MailService) context.getSessionData(SESSION_MAIL_SERVICE);
            ItemStack[] items = (ItemStack[]) context.getSessionData(SESSION_ATTACHMENT);
            UltiToolsPlugin p = (UltiToolsPlugin) context.getSessionData(SESSION_ULTI_PLUGIN);

            boolean success = service.sendMail(sender, receiver, subject, input, items);

            // Either way the sender now knows the outcome: a success is confirmed just below, and
            // a refusal has already been explained from inside MailService.sendMail(...). Recording
            // that stops settleEndedConversation from also calling this send "cancelled".
            context.setSessionData(SESSION_OUTCOME_REPORTED, Boolean.TRUE);

            if (success) {
                // The mail owns the attachment now, so drop the conversation's custody of it --
                // this is the ONLY thing that stops settleEndedConversation handing a second copy
                // back when this conversation ends a moment later. None of MailService.sendMail's
                // refusal branches (cooldown, subject/content too long, unknown receiver, too many
                // items) touches `items`, which is why custody is kept on a false return: the
                // settlement returns them, dropping whatever no longer fits. The GUI attachment
                // path offers 45 slots while MailConfig.maxItems defaults to 27, so selecting
                // 28-45 items is guaranteed to hit the "too many items" refusal.
                context.setSessionData(SESSION_ATTACHMENT, null);

                // sendMessage(...) is a documented no-op while the player is still inside this
                // modal conversation (acceptInput runs before Prompt.END_OF_CONVERSATION is
                // processed) -- matching settleEndedConversation's own use of sendRawMessage, and
                // required here for the same reason.
                String msg = p.i18n("mail_sent_success")
                    .replace("{RECEIVER}", receiver);
                sender.sendRawMessage(ChatColor.GREEN + msg);
            }

            return Prompt.END_OF_CONVERSATION;
        }
    }
}
