package com.ultikits.plugins.mail.service;

import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.plugins.mail.util.Placeholders;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.annotations.Service;

/** Removes old, read mail only after all attached value has been settled. */
@Service
public class MailExpiryService {
    private final UltiToolsPlugin plugin;
    private final MailConfig config;

    public MailExpiryService(UltiToolsPlugin plugin, MailConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Permanently removes read, settled mail from both boxes after the configured number of
     * 24-hour days since sent_time. Unread mail and pending items/commands are always retained.
     * The old mail-expire-days key is never consulted. Disabled passes do not access storage.
     * <p>
     * The literal async task keeps the full-table scan off the server thread, and the framework
     * cancels it on module unload. Passes cannot overlap in this instance. Reloaded retention
     * applies to the next pass; no config file is written here. A failed scan retries next pass;
     * a failed deletion is logged and does not prevent the remaining eligible rows being removed.
     */
    @Scheduled(delay = 1200, period = 1200, async = true)
    public void purgeExpiredMail() {
        purgeExpiredMailAt(System.currentTimeMillis());
    }

    // Explicit time keeps boundary tests deterministic without adding a constructor dependency:
    // the framework injects the longest declared constructor, including non-public constructors.
    synchronized void purgeExpiredMailAt(long now) {
        int days = config.getExpiryRetentionDays();
        if (days <= 0 || days > 365) {
            return;
        }
        long cutoff = now - days * 86400000L;
        try {
            DataOperator<MailData> operator = plugin.getDataOperator(MailData.class);
            for (MailData mail : operator.getAll()) {
                if (!mail.isRead() || mail.getSentTime() <= 0 || mail.getSentTime() > cutoff
                        || (mail.hasItems() && !mail.isClaimed())
                        || (mail.hasCommands() && !mail.isCommandsExecuted())) {
                    continue;
                }
                try {
                    operator.delById(mail.getId());
                } catch (RuntimeException e) {
                    // Not only DataAccessException: a store closing at unload throws
                    // IllegalStateException, and one row must not end the pass (UltiKits/UltiMail#69).
                    plugin.getLogger().warn(Placeholders.fill(plugin.i18n("log_mail_expiry_delete_failed"),
                            "{MAIL}", mail.getId(), "{ERROR}", String.valueOf(e.getMessage())));
                }
            }
        } catch (DataAccessException e) {
            plugin.getLogger().warn(plugin.i18n("log_mail_expiry_scan_failed")
                    .replace("{ERROR}", String.valueOf(e.getMessage())));
        }
    }
}
