package com.ultikits.plugins.mail.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.plugins.mail.util.ItemReturns;
import com.ultikits.plugins.mail.util.Placeholders;
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
            // The setting names the sender {SENDER}, and so does the text the module writes into it; a
            // literal {0} in an operator's text is shown as written, as in every earlier version.
            String message = config.getMailReceivedMessage().replace("{SENDER}", senderName);
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
        // reader needs to see during a storage outage (UltiKits/UltiMail#31).
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
    public ClaimResult claimAttachment(MailData mail, Player player) {
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
     * Claims a mail's attachment, returning what was handed over.
     * <p>
     * Kept with its original signature for plugins compiled against an earlier version: this class is
     * advertised to other plugins, and changing the return type would change the method's descriptor.
     * It is {@link #claimAttachment} with the outcome reduced to the items: a claim refused because it
     * could not be recorded returns an empty array and hands nothing over, exactly like a claim with
     * nothing to claim. Call {@link #claimAttachment} to tell the two apart.
     * <p>
     * 保留原签名以兼容按旧版本编译的插件；需要区分「无可领取」与「记录失败被拒绝」时请使用 {@link #claimAttachment}。
     *
     * @return the items handed over, or an empty array / 已发放的物品，或空数组
     */
    public ItemStack[] claimItems(MailData mail, Player player) {
        return claimAttachment(mail, player).getItems();
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
     * After a successful write each command runs in its own guard: one that throws, or that the server
     * reports as not run ({@code false}: an unknown command, or one whose handler reported failure), is logged at WARNING,
     * naming the mail and the command, and is not retried - the marker is already written - and it
     * does not stop the commands after it. Before, the marker was written after every command
     * had run, so a failed write or a command that threw part-way ran the earlier commands again on
     * the next read.
     * <p>
     * 附带命令先写入「已执行」标记再执行；标记写入失败则不执行任何命令并提示读者。
     */
    public void executeMailCommands(Player player, MailData mail) {
        List<String> commands = recordCommandsExecuted(player, mail);
        if (commands != null) {
            dispatchCommands(player, mail, commands);
        }
    }

    /**
     * {@link #executeMailCommands}, with the commands themselves run on the next server tick
     * (UltiKits/UltiMail#43). For a caller that is itself inside an inventory click handler, as the
     * mailbox GUI is: since UltiTools-Reborn#541 a module command body runs at the moment it is
     * dispatched, so an attached command that opens or closes an inventory ({@code /kits}, another
     * module's menu) would run inside the {@code InventoryClickEvent}, which Paper does not allow.
     * <p>
     * Everything that decides whether the commands run is still done in this call, exactly as in
     * {@link #executeMailCommands}: the commands are parsed and the executed marker is written
     * <b>before</b> anything is scheduled, and when it cannot be written nothing is scheduled and the
     * reader is told (UltiKits/UltiMail#31). Only the dispatch loop moves, with its per-command guard and
     * its rejected-command log line unchanged. When the scheduler refuses the task (the module is being
     * unloaded) no command ran, so the marker is cleared again and the failure logged: the mail's commands
     * run when it is read again, rather than being recorded as run and lost.
     * <p>
     * 与 {@link #executeMailCommands} 相同，但命令本身推迟到下一个服务器 tick 执行，供在背包点击处理中调用。
     *
     * @param player the reader the commands run for
     * @param mail   the mail whose attached commands are run
     */
    public void executeMailCommandsDeferred(Player player, MailData mail) {
        List<String> commands = recordCommandsExecuted(player, mail);
        if (commands == null) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(bukkitPlugin, () -> dispatchCommands(player, mail, commands));
        } catch (RuntimeException e) {
            mail.setCommandsExecuted(false);
            writeFailure(mail);
            plugin.getLogger().error(plugin.i18n("log_mail_commands_failed").replace("{ERROR}", String.valueOf(e.getMessage())));
        }
    }

    /**
     * Parses a mail's attached commands and writes the executed marker, before any command runs.
     *
     * @return the commands to run, or {@code null} when there is nothing to run or the marker could not be
     *         written (the failure is logged and, for an unwritable marker, the reader is told)
     */
    private List<String> recordCommandsExecuted(Player player, MailData mail) {
        if (!mail.hasCommands() || mail.isCommandsExecuted()) {
            return null;
        }

        List<String> commands;
        try {
            commands = GSON.fromJson(mail.getCommands(), STRING_LIST_TYPE);
        } catch (RuntimeException e) {
            plugin.getLogger().error(plugin.i18n("log_mail_commands_failed").replace("{ERROR}", String.valueOf(e.getMessage())));
            return null;
        }
        if (commands == null || commands.isEmpty()) {
            return null;
        }

        mail.setCommandsExecuted(true);
        String failure = writeFailure(mail);
        if (failure != null) {
            mail.setCommandsExecuted(false);
            plugin.getLogger().error(plugin.i18n("log_mail_commands_failed").replace("{ERROR}", failure));
            player.sendMessage(ChatColor.RED + plugin.i18n("mail_commands_not_recorded"));
            return null;
        }
        return commands;
    }

    /** Runs the commands in order, each in its own guard, once the marker is written. */
    private void dispatchCommands(Player player, MailData mail, List<String> commands) {
        for (String command : commands) {
            // Everything per command is inside the guard - a null entry from an API caller included - so
            // one bad entry can neither stop the commands after it nor escape after the marker was
            // written.
            String processedCmd = String.valueOf(command);
            try {
                // Replace placeholders
                processedCmd = command.replace("%player%", player.getName());
                // Check if console command
                boolean ran;
                if (processedCmd.toLowerCase().startsWith("console:")) {
                    String consoleCmd = processedCmd.substring(8).trim();
                    ran = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCmd);
                } else {
                    ran = player.performCommand(processedCmd);
                }
                // The server answers false for an unknown command, or one whose handler reported failure,
                // instead of throwing; it is not retried either, so it is logged the same way. A command
                // that runs and fails without reporting it (a denied permission) answers true.
                if (!ran) {
                    plugin.getLogger().warn(fillOnce(plugin.i18n("log_mail_command_rejected"),
                            "{MAIL}", String.valueOf(mail.getId()),
                            "{COMMAND}", processedCmd));
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
        return Placeholders.fill(template, namesAndValues);
    }
    
    /**
     * Delete mail (soft delete).
     * <p>
     * Kept with its original signature for plugins compiled against an earlier version; call
     * {@link #recordDeletion} to learn whether the deletion was recorded.
     */
    public void deleteMail(MailData mail, UUID playerUuid) {
        recordDeletion(mail, playerUuid);
    }

    /**
     * Deletes a mail for {@code playerUuid} (soft delete; removed for good once both sides deleted it).
     * <p>
     * A write the storage cannot make is caught, logged with {@code log_update_mail_failed} and
     * reported as not recorded, and the mail keeps the flags it had, so it is still there and can be
     * deleted again. Both write failures are caught: {@code update}'s declared
     * {@code IllegalAccessException} and the unchecked {@link DataAccessException} the relational
     * backends throw on any SQL error, which used to escape and abort {@code /mail delete},
     * {@code delall} and {@code delread} part-way (UltiKits/UltiMail#38).
     * <p>
     * 存储无法写入时记录日志并返回「未记录」，邮件保持原状态，不再抛出异常中断命令。
     *
     * @return whether the deletion was recorded / 删除是否已记录
     */
    public boolean recordDeletion(MailData mail, UUID playerUuid) {
        boolean wasDeletedBySender = mail.isDeletedBySender();
        boolean wasDeletedByReceiver = mail.isDeletedByReceiver();
        if (mail.getSenderUuid().equals(playerUuid.toString())) {
            mail.setDeletedBySender(true);
        }
        if (mail.getReceiverUuid().equals(playerUuid.toString())) {
            mail.setDeletedByReceiver(true);
        }

        String failure;
        if (mail.isDeletedBySender() && mail.isDeletedByReceiver()) {
            // Both sides deleted it: remove it for good.
            failure = deleteFailure(mail);
        } else {
            failure = writeFailure(mail);
        }
        if (failure != null) {
            mail.setDeletedBySender(wasDeletedBySender);
            mail.setDeletedByReceiver(wasDeletedByReceiver);
            plugin.getLogger().error(plugin.i18n("log_update_mail_failed").replace("{ERROR}", failure));
            return false;
        }
        return true;
    }

    /**
     * Removes a mail for good and returns {@code null}, or the failure's message instead of throwing.
     */
    private String deleteFailure(MailData mail) {
        try {
            dataOperator.delById(mail.getId());
            return null;
        } catch (DataAccessException e) {
            return String.valueOf(e.getMessage());
        }
    }

    /**
     * What a batch delete did: how many mails it deleted, and how many it could not record.
     */
    public static final class DeleteResult {
        private final int deleted;
        private final int notRecorded;

        public DeleteResult(int deleted, int notRecorded) {
            this.deleted = deleted;
            this.notRecorded = notRecorded;
        }

        /** @return the number of mails deleted */
        public int getDeleted() {
            return deleted;
        }

        /** @return the number of mails whose deletion could not be recorded; they are still there */
        public int getNotRecorded() {
            return notRecorded;
        }
    }

    /**
     * Delete all mails for a player (receiver side).
     *
     * @return number of mails deleted
     */
    public int deleteAllByReceiver(UUID playerUuid) {
        return deleteAllFromInbox(playerUuid).getDeleted();
    }

    /**
     * Deletes every inbox mail of a player that has no unclaimed attachment.
     *
     * @return how many were deleted and how many could not be recorded
     */
    public DeleteResult deleteAllFromInbox(UUID playerUuid) {
        return deleteFromInbox(playerUuid, false);
    }

    /**
     * Delete all read mails for a player (receiver side).
     *
     * @return number of mails deleted
     */
    public int deleteReadByReceiver(UUID playerUuid) {
        return deleteReadFromInbox(playerUuid).getDeleted();
    }

    /**
     * Deletes every read inbox mail of a player that has no unclaimed attachment.
     *
     * @return how many were deleted and how many could not be recorded
     */
    public DeleteResult deleteReadFromInbox(UUID playerUuid) {
        return deleteFromInbox(playerUuid, true);
    }

    private DeleteResult deleteFromInbox(UUID playerUuid, boolean readOnly) {
        List<MailData> mails = getInbox(playerUuid);
        int deleted = 0;
        int notRecorded = 0;

        for (MailData mail : mails) {
            if (readOnly && !mail.isRead()) {
                continue;
            }
            // Skip if has unclaimed items
            if (mail.hasItems() && !mail.isClaimed()) {
                continue;
            }
            if (recordDeletion(mail, playerUuid)) {
                deleted++;
            } else {
                notRecorded++;
            }
        }

        return new DeleteResult(deleted, notRecorded);
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
