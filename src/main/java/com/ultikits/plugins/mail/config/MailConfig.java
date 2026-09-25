package com.ultikits.plugins.mail.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Range;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for UltiMail.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Getter
@Setter
@ConfigEntity(MailConfig.CONFIG_FILE)
public class MailConfig extends AbstractConfigEntity {

    /**
     * This entity's file, relative to the module's folder. The one place the path is written: the
     * annotation above, the constructor and the removed-key check in {@code UltiMail} all read it
     * from here, so they cannot drift apart (UltiKits/UltiMail#23).
     */
    public static final String CONFIG_FILE = "config/mail.yml";
    
    @ConfigEntry(path = "max-items", comment = "每封邮件最多附带物品数量")
    @Range(min = 1, max = 54)
    private int maxItems = 27;

    @ConfigEntry(path = "notify-on-join", comment = "玩家登录时通知未读邮件")
    private boolean notifyOnJoin = true;

    @ConfigEntry(path = "notify-delay", comment = "登录通知延迟（秒）")
    @Range(min = 0, max = 60)
    private int notifyDelay = 3;

    @ConfigEntry(path = "max-subject-length", comment = "邮件标题最大长度")
    @Range(min = 10, max = 200)
    private int maxSubjectLength = 50;

    @ConfigEntry(path = "max-content-length", comment = "邮件内容最大长度")
    @Range(min = 50, max = 5000)
    private int maxContentLength = 500;

    @ConfigEntry(path = "send-cooldown", comment = "发送邮件冷却时间（秒）")
    @Range(min = 0, max = 300)
    private int sendCooldown = 10;
    
    // The five text settings below ship blank: a blank value shows the language file's text in the
    // server's language (the getters at the end of this class). Not @NotEmpty, which would refuse a
    // blank value (maintainer ruling 2026-09-24 (d)).
    @ConfigEntry(path = "messages.mail-received", comment = "收到新邮件（{SENDER} 为发件人；留空则使用语言文件中的文本）")
    private String mailReceivedMessage = "";
    
    // ========== 召回玩家功能配置 ==========
    
    @ConfigEntry(path = "recall.server-name", comment = "服务器名称，用于召回邮件显示")
    @NotEmpty
    private String serverName = "Minecraft服务器";

    @ConfigEntry(path = "recall.subject", comment = "游戏内召回邮件标题（留空则使用语言文件中的文本）")
    private String recallSubject = "";

    @ConfigEntry(path = "recall.content", comment = "游戏内召回邮件内容（留空则使用语言文件中的文本）")
    private String recallContent = "";
    
    // ========== 真实邮件发送配置 ==========
    
    @ConfigEntry(path = "email.enabled", comment = "是否启用真实邮件发送功能")
    private boolean emailEnabled = false;
    
    @ConfigEntry(path = "email.smtp-host", comment = "SMTP服务器地址")
    @NotEmpty
    private String smtpHost = "smtp.example.com";

    @ConfigEntry(path = "email.smtp-port", comment = "SMTP端口")
    @Range(min = 1, max = 65535)
    private int smtpPort = 587;

    @ConfigEntry(path = "email.smtp-username", comment = "SMTP用户名")
    private String smtpUsername = "";

    @ConfigEntry(path = "email.smtp-password", comment = "SMTP密码")
    private String smtpPassword = "";

    @ConfigEntry(path = "email.smtp-from-email", comment = "发件人邮箱地址")
    @NotEmpty
    private String smtpFromEmail = "noreply@example.com";
    
    @ConfigEntry(path = "email.smtp-ssl", comment = "是否使用SSL加密")
    private boolean smtpSsl = false;
    
    @ConfigEntry(path = "email.smtp-starttls", comment = "是否使用STARTTLS加密")
    private boolean smtpStartTls = true;
    
    @ConfigEntry(path = "email.recall-subject", comment = "召回电子邮件标题（留空则使用语言文件中的文本）")
    private String recallEmailSubject = "";

    @ConfigEntry(path = "email.recall-content", comment = "召回电子邮件内容（留空则使用语言文件中的文本）")
    private String recallEmailContent = "";
    
    public MailConfig() {
        super(CONFIG_FILE);
    }

    // The default each text setting had in every earlier version, read from this class's history (one
    // value per key). They are here only to be recognised in an upgraded operator's file and blanked;
    // they are never shown.
    static final String SHIPPED_MAIL_RECEIVED = "&e[邮件] &f你收到了来自 &a{SENDER} &f的新邮件！";
    static final String SHIPPED_RECALL_SUBJECT = "[{SERVER}] 回归召唤";
    static final String SHIPPED_RECALL_CONTENT = "亲爱的玩家，{SERVER}想念你了！\n\n快回来看看吧，我们期待与你重逢！\n\n发送者: {SENDER}";
    static final String SHIPPED_RECALL_EMAIL_SUBJECT = "[{SERVER}] 我们想念你！";
    static final String SHIPPED_RECALL_EMAIL_CONTENT = "亲爱的 {PLAYER}，\n\n{SERVER} 服务器想念你了！快回来看看吧，我们期待与你重逢！\n\n发送者: {SENDER}";

    /**
     * Blanks every text setting that is exactly the default an earlier version shipped, so the
     * language file's text takes over in the server's language; any other value is the operator's and
     * is kept. Idempotent: a blank value matches no shipped default. The caller saves the file when
     * this returns true (maintainer ruling 2026-09-24 (d)).
     *
     * @return whether any value was rewritten
     */
    public boolean migrateLegacyDefaults() {
        boolean changed = false;
        if (SHIPPED_MAIL_RECEIVED.equals(mailReceivedMessage)) {
            mailReceivedMessage = "";
            changed = true;
        }
        if (SHIPPED_RECALL_SUBJECT.equals(recallSubject)) {
            recallSubject = "";
            changed = true;
        }
        if (SHIPPED_RECALL_CONTENT.equals(recallContent)) {
            recallContent = "";
            changed = true;
        }
        if (SHIPPED_RECALL_EMAIL_SUBJECT.equals(recallEmailSubject)) {
            recallEmailSubject = "";
            changed = true;
        }
        if (SHIPPED_RECALL_EMAIL_CONTENT.equals(recallEmailContent)) {
            recallEmailContent = "";
            changed = true;
        }
        return changed;
    }

    // ---- Text settings: the configured value, or the language file's when it is blank ----
    // Resolved each time a setting is read, never while the configuration reloads: the framework
    // reloads configuration before it rebuilds the language, so a value resolved during a reload
    // would come from the old language (maintainer ruling 2026-09-24 (d)).

    /** {@code configured}, or {@code languageText} when {@code configured} is null, empty or only whitespace. */
    static String configuredOr(String configured, String languageText) {
        return configured == null || configured.trim().isEmpty() ? languageText : configured;
    }

    /**
     * The language file's text for {@code key}, read through the plugin this configuration was bound
     * to at load. Before that binding there is no language to read, so the key itself is returned, as
     * the framework renders a missing key.
     */
    private String i18n(String key) {
        UltiToolsPlugin plugin = getUltiToolsPlugin();
        return plugin == null ? key : plugin.i18n(key);
    }

    /**
     * {@code messages.mail-received}, or the language file's {@code notify_mail_received} when blank.
     * A configured value names the sender {@code {SENDER}}; the language file's names it {@code {0}}.
     *
     * @return the notice a receiver gets when a mail arrives
     */
    public String getMailReceivedMessage() {
        return configuredOr(mailReceivedMessage, i18n("notify_mail_received"));
    }

    /** @return {@code recall.subject}, or the language file's {@code recall_subject} when blank */
    public String getRecallSubject() {
        return configuredOr(recallSubject, i18n("recall_subject"));
    }

    /** @return {@code recall.content}, or the language file's {@code recall_content} when blank */
    public String getRecallContent() {
        return configuredOr(recallContent, i18n("recall_content"));
    }

    /** @return {@code email.recall-subject}, or the language file's {@code recall_email_subject} when blank */
    public String getRecallEmailSubject() {
        return configuredOr(recallEmailSubject, i18n("recall_email_subject"));
    }

    /** @return {@code email.recall-content}, or the language file's {@code recall_email_content} when blank */
    public String getRecallEmailContent() {
        return configuredOr(recallEmailContent, i18n("recall_email_content"));
    }
}
