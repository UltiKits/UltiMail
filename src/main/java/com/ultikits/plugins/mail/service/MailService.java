package com.ultikits.plugins.mail.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.plugins.mail.util.ItemReturns;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.PostConstruct;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing mail system.
 * <p>
 * Provides mail sending, receiving, claiming items, executing commands,
 * and batch operations.
 *
 * @author wisdomme
 * @version 1.1.0
 */
@Service
public class MailService {
    
    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private MailConfig config;

    private Plugin bukkitPlugin;
    private DataOperator<MailData> dataOperator;

    // Cooldown tracking
    private final Map<UUID, Long> sendCooldowns = new ConcurrentHashMap<>();

    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>(){}.getType();
    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("\\{[A-Z]+}");

    /**
     * Initialize the mail service.
     */
    @PostConstruct
    public void init() {
        dataOperator = plugin.getDataOperator(MailData.class);
        bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
    }
    
    /**
     * Get the mail configuration.
     */
    public MailConfig getConfig() {
        return config;
    }
    
    /**
     * Send a mail to a player.
     * 
     * @param sender Sender player
     * @param receiverName Receiver name
     * @param subject Mail subject
     * @param content Mail content
     * @param items Attached items (can be null)
     * @return true if sent successfully
     */
    public boolean sendMail(Player sender, String receiverName, String subject, String content, ItemStack[] items) {
        return sendMail(sender, receiverName, subject, content, items, null);
    }
    
    /**
     * Send a mail to a player with optional commands.
     * This is the full API method for third-party plugins.
     * 
     * @param sender Sender player
     * @param receiverName Receiver name
     * @param subject Mail subject
     * @param content Mail content
     * @param items Attached items (can be null)
     * @param commands Commands to execute when read (can be null)
     * @return true if sent successfully
     */
    public boolean sendMail(Player sender, String receiverName, String subject, String content,
                           ItemStack[] items, List<String> commands) {
        // Every refusal below uses sendRawMessage rather than sendMessage: this method is called
        // from SendMailCommand.ContentPrompt.acceptInput while sender is still inside a modal
        // Bukkit conversation, and Player#sendMessage is a documented no-op in that state (only
        // sendRawMessage always delivers). Unconditional for every caller, not just the
        // conversation one: outside a conversation the two are behaviourally identical.
        // Check cooldown
        if (isOnCooldown(sender.getUniqueId())) {
            sender.sendRawMessage(ChatColor.RED + i18n("send_cooldown"));
            return false;
        }
        
        // Validate subject and content
        if (subject.length() > config.getMaxSubjectLength()) {
            sender.sendRawMessage(ChatColor.RED + i18n("send_subject_too_long")
                .replace("{0}", String.valueOf(config.getMaxSubjectLength())));
            return false;
        }
        if (content.length() > config.getMaxContentLength()) {
            sender.sendRawMessage(ChatColor.RED + i18n("send_content_too_long")
                .replace("{0}", String.valueOf(config.getMaxContentLength())));
            return false;
        }
        
        // Get receiver UUID (may be offline)
        String receiverUuid = getPlayerUuid(receiverName);
        if (receiverUuid == null) {
            sender.sendRawMessage(ChatColor.RED + i18n("send_player_not_found")
                .replace("{0}", receiverName));
            return false;
        }
        
        // Create mail data
        MailData mail = createMailData(sender.getUniqueId().toString(), sender.getName(),
            receiverUuid, receiverName, subject, content, items, commands);
        
        if (mail == null) {
            sender.sendRawMessage(ChatColor.RED + i18n("send_items_too_many")
                .replace("{0}", String.valueOf(config.getMaxItems())));
            return false;
        }
        
        // Save to database
        dataOperator.insert(mail);
        
        // Set cooldown
        sendCooldowns.put(sender.getUniqueId(), System.currentTimeMillis());
        
        // Notify receiver if online
        notifyReceiver(receiverName, sender.getName());
        
        return true;
    }
    
    /**
     * Send mail to all players (broadcast).
     * 
     * @param sender Sender player
     * @param content Mail content
     * @param items Attached items (can be null, will be cloned for each player)
     */
    public void sendToAll(Player sender, String content, ItemStack[] items) {
        String subject = i18n("sendall_success");
        String senderUuid = sender.getUniqueId().toString();
        String senderName = sender.getName();
        
        new BukkitRunnable() {
            @Override
            public void run() {
                OfflinePlayer[] players = Bukkit.getOfflinePlayers();
                int total = players.length;
                int sent = 0;
                
                for (OfflinePlayer offline : players) {
                    if (offline.getUniqueId().equals(sender.getUniqueId())) {
                        continue; // Skip sender
                    }
                    
                    MailData mail = createMailData(senderUuid, senderName,
                        offline.getUniqueId().toString(), 
                        offline.getName() != null ? offline.getName() : "Unknown",
                        subject, content, items, null);
                    
                    if (mail != null) {
                        dataOperator.insert(mail);
                        sent++;
                        
                        // Notify if online
                        if (offline.isOnline()) {
                            Player onlinePlayer = offline.getPlayer();
                            if (onlinePlayer != null) {
                                notifyReceiver(onlinePlayer.getName(), senderName);
                            }
                        }
                    }
                    
                    // Progress update every 50 players
                    if (sent % 50 == 0) {
                        final int currentSent = sent;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                sender.sendMessage(ChatColor.YELLOW + i18n("sendall_progress")
                                    .replace("{0}", String.valueOf(currentSent))
                                    .replace("{1}", String.valueOf(total)));
                            }
                        }.runTask(bukkitPlugin);
                    }
                }
                
                // Final notification
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        sender.sendMessage(ChatColor.GREEN + i18n("sendall_success"));
                    }
                }.runTask(bukkitPlugin);
            }
        }.runTaskAsynchronously(bukkitPlugin);
    }
    
    /**
     * Creates a MailData object with the given parameters.
     */
    private MailData createMailData(String senderUuid, String senderName, 
                                    String receiverUuid, String receiverName,
                                    String subject, String content,
                                    ItemStack[] items, List<String> commands) {
        MailData mail = new MailData();
        mail.setSenderUuid(senderUuid);
        mail.setSenderName(senderName);
        mail.setReceiverUuid(receiverUuid);
        mail.setReceiverName(receiverName);
        mail.setSubject(subject);
        mail.setContent(content);
        mail.setSentTime(System.currentTimeMillis());
        
        // Serialize items if any
        if (items != null && items.length > 0) {
            List<ItemStack> validItems = new ArrayList<>();
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    validItems.add(item);
                }
            }
            if (!validItems.isEmpty()) {
                if (validItems.size() > config.getMaxItems()) {
                    return null; // Too many items
                }
                mail.setItems(serializeItems(validItems.toArray(new ItemStack[0])));
            }
        }
        
        // Serialize commands if any
        if (commands != null && !commands.isEmpty()) {
            mail.setCommands(GSON.toJson(commands));
        }
        
        return mail;
    }
    
    /**
     * Notify receiver about new mail.
     */
    private void notifyReceiver(String receiverName, String senderName) {
        Player receiver = Bukkit.getPlayerExact(receiverName);
        if (receiver != null && receiver.isOnline()) {
            // A configured message names the sender {SENDER}; the language file's notify_mail_received,
            // shown while the setting is blank, names it {0}.
            String message = config.getMailReceivedMessage().replace("{SENDER}", senderName)
                    .replace("{0}", senderName);
            receiver.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }
    
    /**
     * Get inbox mails for a player.
     *
     * @param playerUuid Player UUID
     * @return List of received mails
     */
    public List<MailData> getInbox(UUID playerUuid) {
        List<MailData> mails = dataOperator.query()
            .where("receiver_uuid").eq(playerUuid.toString())
            .list();

        // Filter out deleted
        List<MailData> result = new ArrayList<>();
        for (MailData mail : mails) {
            if (!mail.isDeletedByReceiver()) {
                result.add(mail);
            }
        }

        // Sort by time descending
        result.sort((a, b) -> Long.compare(b.getSentTime(), a.getSentTime()));
        return result;
    }
    
    /**
     * Get sent mails for a player.
     *
     * @param playerUuid Player UUID
     * @return List of sent mails
     */
    public List<MailData> getSentMails(UUID playerUuid) {
        List<MailData> mails = dataOperator.query()
            .where("sender_uuid").eq(playerUuid.toString())
            .list();

        // Filter out deleted
        List<MailData> result = new ArrayList<>();
        for (MailData mail : mails) {
            if (!mail.isDeletedBySender()) {
                result.add(mail);
            }
        }

        result.sort((a, b) -> Long.compare(b.getSentTime(), a.getSentTime()));
        return result;
    }
    
    /**
     * Get unread mail count.
     */
    public int getUnreadCount(UUID playerUuid) {
        List<MailData> inbox = getInbox(playerUuid);
        int count = 0;
        for (MailData mail : inbox) {
            if (!mail.isRead()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Mark mail as read.
     */
    public void markAsRead(MailData mail) {
        mail.setRead(true);
        // A read flag is not a one-time hand-over, so a failed write is logged and the read goes on.
        // It must not throw: both hand-overs run right after it, and their refusal replies are what the
        // reader needs to see during a storage outage (UltiKits/UltiMail#31, gate-1 WR-03).
        String failure = writeFailure(mail);
        if (failure != null) {
            plugin.getLogger().error(plugin.i18n("log_mark_read_failed").replace("{ERROR}", failure));
        }
    }
    
    /**
     * Get number of items in a mail.
     */
    public int getItemCount(MailData mail) {
        if (!mail.hasItems()) {
            return 0;
        }
        ItemStack[] items = deserializeItems(mail.getItems());
        return items != null ? items.length : 0;
    }
    
    /**
     * Outcome of claiming a mail's attachment.
     * <p>
     * Typed because the caller must tell the player three different things: the items arrived; there
     * was nothing to claim; or the claim was refused because it could not be recorded - in which case
     * nothing was given and the player may try again (UltiKits/UltiMail#31). An empty array could not
     * tell the last two apart, and the command reported a refused claim as a success.
     * <p>
     * 领取附件的结果：已领取、没有可领取的物品、或因记录无法写入而被拒绝（未发放任何物品）。
     */
    public static final class ClaimResult {

        /** What happened to the claim. */
        public enum Status {
            /** The claimed flag was written and the attachment handed over. */
            CLAIMED,
            /** Already claimed, no attachment, or an attachment that could not be read. */
            NOTHING_TO_CLAIM,
            /** The claimed flag could not be written, so nothing was handed over. */
            NOT_RECORDED
        }

        private static final ItemStack[] NONE = new ItemStack[0];
        private static final ClaimResult NOTHING = new ClaimResult(Status.NOTHING_TO_CLAIM, NONE);
        private static final ClaimResult REFUSED = new ClaimResult(Status.NOT_RECORDED, NONE);

        private final Status status;
        private final ItemStack[] items;

        private ClaimResult(Status status, ItemStack[] items) {
            this.status = status;
            this.items = items;
        }

        /** The attachment was recorded as claimed and handed over. */
        public static ClaimResult claimed(ItemStack[] items) {
            return new ClaimResult(Status.CLAIMED, items);
        }

        /** There was nothing to claim. */
        public static ClaimResult nothingToClaim() {
            return NOTHING;
        }

        /** The claim could not be recorded; nothing was handed over. */
        public static ClaimResult notRecorded() {
            return REFUSED;
        }

        public Status getStatus() {
            return status;
        }

        /** The items handed over - as the mail stored them - or an empty array unless claimed. */
        public ItemStack[] getItems() {
            return items;
        }
    }

    /**
     * Claims a mail's attachment: records it as claimed first, and hands it over only once that
     * record was written.
     * <p>
     * The order is the maintainer's decision of 2026-09-24 for a one-time claim whose record cannot be
     * written - write the record first and refuse the claim when the write fails. The previous order
     * handed the items over first and only logged a failed write, and because {@link #getInbox}
     * re-reads the table on every command the same attachment could then be claimed again at once
     * (UltiKits/UltiMail#31). On a failed write the in-memory flag is restored, nothing is handed over,
     * and the result tells the caller so. Both write failures are caught: {@code update}'s declared
     * {@code IllegalAccessException} and the unchecked {@link DataAccessException} the relational
     * backends throw on any SQL error. On the JSON storage backend a write only reaches an in-memory
     * cache that a timer flushes to disk, so a disk failure there cannot be seen at claim time - the
     * framework's storage contract, not changed here.
     * <p>
     * Does NOT check for inventory space - caller should check first; anything that does not fit is
     * dropped at the player's feet by {@link ItemReturns#giveOrDrop}.
     * <p>
     * 先写入「已领取」记录，写入成功后才发放附件；写入失败时拒绝领取，不发放任何物品。
     *
     * @return the outcome, never null / 结果，不为 null
     */
    public ClaimResult claimItems(MailData mail, Player player) {
        if (mail.isClaimed() || mail.getItems() == null || mail.getItems().isEmpty()) {
            return ClaimResult.nothingToClaim();
        }

        ItemStack[] items = deserializeItems(mail.getItems());
        if (items == null || items.length == 0) {
            return ClaimResult.nothingToClaim();
        }

        mail.setClaimed(true);
        String failure = writeFailure(mail);
        if (failure != null) {
            mail.setClaimed(false);
            plugin.getLogger().error(plugin.i18n("log_claim_failed").replace("{ERROR}", failure));
            return ClaimResult.notRecorded();
        }

        // Hand the attachment over through the module's one return helper rather than repeating its
        // addItem/dropItemNaturally pair here: ItemReturns#giveOrDrop declares itself the single
        // place this module hands items back, and it also skips the null and air entries a stored
        // Base64 payload round-trips faithfully -- passing those straight to Inventory#addItem threw
        // out of this method and aborted the whole claim.
        ItemReturns.giveOrDrop(player, items);
        return ClaimResult.claimed(items);
    }

    /**
     * Writes a mail's flags and returns {@code null}, or the failure's message instead of throwing:
     * the relational backends throw the unchecked {@link DataAccessException} on any SQL error, and
     * {@code update} declares {@code IllegalAccessException}.
     */
    private String writeFailure(MailData mail) {
        try {
            dataOperator.update(mail);
            return null;
        } catch (IllegalAccessException | DataAccessException e) {
            return String.valueOf(e.getMessage());
        }
    }

    /**
     * Execute commands attached to a mail.
     * Supports mixed mode: normal commands run as player, console: prefixed commands run as console.
     * Supports %player% placeholder.
     * <p>
     * The commands are a one-time hand-over like an attachment, so they follow the same decision
     * (UltiKits/UltiMail#31): the executed marker is written <b>before</b> any command runs. When it
     * cannot be written, no command runs and the reader is told, so reading the mail again retries.
     * After a successful write each command runs in its own guard: one that throws is logged at
     * WARNING, naming the mail and the command, and is not retried - the marker is already written -
     * and it does not stop the commands after it. Before, the marker was written after every command
     * had run, so a failed write or a command that threw part-way ran the earlier commands again on
     * the next read.
     * <p>
     * 附带命令先写入「已执行」标记再执行；标记写入失败则不执行任何命令并提示读者。
     */
    public void executeMailCommands(Player player, MailData mail) {
        if (!mail.hasCommands() || mail.isCommandsExecuted()) {
            return;
        }

        List<String> commands;
        try {
            commands = GSON.fromJson(mail.getCommands(), STRING_LIST_TYPE);
        } catch (RuntimeException e) {
            plugin.getLogger().error(plugin.i18n("log_mail_commands_failed").replace("{ERROR}", String.valueOf(e.getMessage())));
            return;
        }
        if (commands == null || commands.isEmpty()) {
            return;
        }

        mail.setCommandsExecuted(true);
        String failure = writeFailure(mail);
        if (failure != null) {
            mail.setCommandsExecuted(false);
            plugin.getLogger().error(plugin.i18n("log_mail_commands_failed").replace("{ERROR}", failure));
            player.sendMessage(ChatColor.RED + plugin.i18n("mail_commands_not_recorded"));
            return;
        }

        for (String command : commands) {
            // Replace placeholders
            String processedCmd = command.replace("%player%", player.getName());
            try {
                // Check if console command
                if (processedCmd.toLowerCase().startsWith("console:")) {
                    String consoleCmd = processedCmd.substring(8).trim();
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCmd);
                } else {
                    player.performCommand(processedCmd);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warn(fillOnce(plugin.i18n("log_mail_command_failed"),
                        "{MAIL}", String.valueOf(mail.getId()),
                        "{COMMAND}", processedCmd,
                        "{ERROR}", String.valueOf(e.getMessage())));
            }
        }
    }

    /**
     * Fills {@code {NAME}} placeholders in one pass, so a value that itself contains a placeholder
     * token (a command or an error text can) is inserted as written and not expanded again.
     */
    private static String fillOnce(String template, String... namesAndValues) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            values.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        java.util.regex.Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String value = values.get(m.group());
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }
    
    /**
     * Delete mail (soft delete).
     */
    public void deleteMail(MailData mail, UUID playerUuid) {
        if (mail.getSenderUuid().equals(playerUuid.toString())) {
            mail.setDeletedBySender(true);
        }
        if (mail.getReceiverUuid().equals(playerUuid.toString())) {
            mail.setDeletedByReceiver(true);
        }
        
        // If both deleted, really delete
        if (mail.isDeletedBySender() && mail.isDeletedByReceiver()) {
            dataOperator.delById(mail.getId());
        } else {
            try {
                dataOperator.update(mail);
            } catch (IllegalAccessException e) {
                plugin.getLogger().error(plugin.i18n("log_update_mail_failed").replace("{ERROR}", String.valueOf(e.getMessage())));
            }
        }
    }
    
    /**
     * Delete all mails for a player (receiver side).
     * 
     * @return number of mails deleted
     */
    public int deleteAllByReceiver(UUID playerUuid) {
        List<MailData> mails = getInbox(playerUuid);
        int count = 0;
        
        for (MailData mail : mails) {
            // Skip if has unclaimed items
            if (mail.hasItems() && !mail.isClaimed()) {
                continue;
            }
            deleteMail(mail, playerUuid);
            count++;
        }
        
        return count;
    }
    
    /**
     * Delete all read mails for a player (receiver side).
     * 
     * @return number of mails deleted
     */
    public int deleteReadByReceiver(UUID playerUuid) {
        List<MailData> mails = getInbox(playerUuid);
        int count = 0;
        
        for (MailData mail : mails) {
            if (!mail.isRead()) {
                continue;
            }
            // Skip if has unclaimed items
            if (mail.hasItems() && !mail.isClaimed()) {
                continue;
            }
            deleteMail(mail, playerUuid);
            count++;
        }
        
        return count;
    }
    
    /**
     * Get mail by ID.
     */
    public MailData getMail(String id) {
        return dataOperator.getById(id);
    }
    
    /**
     * Internal method for sending mail programmatically without player sender.
     * Used by GameMailService integration for cross-module mail.
     * 
     * @param senderUuid Sender UUID (can be null for system mail)
     * @param senderName Sender name
     * @param receiverName Receiver name
     * @param subject Mail subject
     * @param content Mail content
     * @param items Attached items (can be null)
     * @return true if sent successfully
     */
    public boolean sendMailInternal(UUID senderUuid, String senderName, String receiverName, 
                                    String subject, String content, ItemStack[] items) {
        // Get receiver UUID (may be offline)
        String receiverUuid = getPlayerUuid(receiverName);
        if (receiverUuid == null) {
            return false;
        }
        
        // Create mail data
        MailData mail = createMailData(
            senderUuid != null ? senderUuid.toString() : null,
            senderName,
            receiverUuid, 
            receiverName, 
            subject, 
            content, 
            items, 
            null
        );
        
        if (mail == null) {
            return false;
        }
        
        // Save to database
        dataOperator.insert(mail);
        
        // Notify receiver if online
        notifyReceiver(receiverName, senderName);
        
        return true;
    }
    
    /**
     * Check if player is on send cooldown.
     */
    private boolean isOnCooldown(UUID playerUuid) {
        Long lastSend = sendCooldowns.get(playerUuid);
        if (lastSend == null) {
            return false;
        }
        return System.currentTimeMillis() - lastSend < config.getSendCooldown() * 1000L;
    }
    
    /**
     * Get player UUID by name (handles offline players).
     */
    private String getPlayerUuid(String name) {
        // Check online first
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            return player.getUniqueId().toString();
        }
        
        // Check offline
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId().toString();
        }
        
        return null;
    }
    
    /**
     * Serialize ItemStack array to Base64.
     */
    private String serializeItems(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();
            
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warn(plugin.i18n("log_serialize_failed").replace("{ERROR}", String.valueOf(e.getMessage())));
            return null;
        }
    }
    
    /**
     * Deserialize ItemStack array from Base64.
     */
    private ItemStack[] deserializeItems(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            
            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();
            
            return items;
        } catch (Exception e) {
            plugin.getLogger().warn(plugin.i18n("log_deserialize_failed").replace("{ERROR}", String.valueOf(e.getMessage())));
            return new ItemStack[0];
        }
    }
    
    /**
     * Shortcut for i18n.
     */
    private String i18n(String key) {
        return plugin.i18n(key);
    }
}
