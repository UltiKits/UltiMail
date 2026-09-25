package com.ultikits.plugins.mail.config;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Range;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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
    
    // Each text setting's Java default is the one it shipped with in every earlier version, which the
    // framework writes for a missing key; materializeText() then writes the jar's text in the server's
    // language while the value is still built-in text (maintainer decision 2026-09-25).
    @ConfigEntry(path = "messages.mail-received", comment = "收到新邮件")
    @NotEmpty
    private String mailReceivedMessage = SHIPPED_MAIL_RECEIVED;
    
    // ========== 召回玩家功能配置 ==========
    
    @ConfigEntry(path = "recall.server-name", comment = "服务器名称，用于召回邮件显示")
    @NotEmpty
    private String serverName = SHIPPED_SERVER_NAME;

    @ConfigEntry(path = "recall.subject", comment = "游戏内召回邮件标题")
    @NotEmpty
    private String recallSubject = SHIPPED_RECALL_SUBJECT;

    @ConfigEntry(path = "recall.content", comment = "游戏内召回邮件内容")
    @NotEmpty
    private String recallContent = SHIPPED_RECALL_CONTENT;
    
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
    
    @ConfigEntry(path = "email.recall-subject", comment = "召回电子邮件标题")
    @NotEmpty
    private String recallEmailSubject = SHIPPED_RECALL_EMAIL_SUBJECT;

    @ConfigEntry(path = "email.recall-content", comment = "召回电子邮件内容")
    @NotEmpty
    private String recallEmailContent = SHIPPED_RECALL_EMAIL_CONTENT;
    
    public MailConfig() {
        super(CONFIG_FILE);
    }

    // The default each text setting had in every earlier version, read from this class's history (one
    // value per key). Each is the field's Java default and one of the values materializeText()
    // recognises as built-in text in an operator's file, compared byte for byte.
    static final String SHIPPED_MAIL_RECEIVED = "&e[邮件] &f你收到了来自 &a{SENDER} &f的新邮件！";
    static final String SHIPPED_SERVER_NAME = "Minecraft服务器";
    static final String SHIPPED_RECALL_SUBJECT = "[{SERVER}] 回归召唤";
    static final String SHIPPED_RECALL_CONTENT = "亲爱的玩家，{SERVER}想念你了！\n\n快回来看看吧，我们期待与你重逢！\n\n发送者: {SENDER}";
    static final String SHIPPED_RECALL_EMAIL_SUBJECT = "[{SERVER}] 我们想念你！";
    static final String SHIPPED_RECALL_EMAIL_CONTENT = "亲爱的 {PLAYER}，\n\n{SERVER} 服务器想念你了！快回来看看吧，我们期待与你重逢！\n\n发送者: {SENDER}";

    /**
     * Writes every text setting in the server's language (maintainer decision 2026-09-25,
     * UltiKits/UltiMail#21, UltiKits/UltiMail#22): each setting whose value is still built-in text --
     * the default an earlier version shipped, or this jar's text for it in any language -- and differs
     * from the current text is replaced with {@code text}'s current text, when that text fits the
     * setting's own limits. Any other value is the operator's and is kept. Idempotent. Must run after the
     * module's language is loaded ({@code registerSelf()} and {@code onReload()}), never from a change
     * listener; the caller saves the file when this returns {@code true}.
     *
     * @param text catalogue key to text in the server's language, from this jar's own catalogue
     *             ({@code ConfigTextDefaults#jarLanguage}), so every value written is in the tracked set
     * @return whether any value was rewritten
     */
    public boolean materializeText(Function<String, String> text) {
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(MailConfig.class);
        boolean[] changed = {false};
        mailReceivedMessage = follow("mailReceivedMessage", mailReceivedMessage, text, jar, "mail_received", SHIPPED_MAIL_RECEIVED, changed);
        serverName = follow("serverName", serverName, text, jar, "recall_server_name", SHIPPED_SERVER_NAME, changed);
        recallSubject = follow("recallSubject", recallSubject, text, jar, "recall_subject", SHIPPED_RECALL_SUBJECT, changed);
        recallContent = follow("recallContent", recallContent, text, jar, "recall_content", SHIPPED_RECALL_CONTENT, changed);
        recallEmailSubject = follow("recallEmailSubject", recallEmailSubject, text, jar, "recall_email_subject", SHIPPED_RECALL_EMAIL_SUBJECT, changed);
        recallEmailContent = follow("recallEmailContent", recallEmailContent, text, jar, "recall_email_content", SHIPPED_RECALL_EMAIL_CONTENT, changed);
        return changed[0];
    }

    /**
     * {@code value}, or {@code text}'s current text for {@code key} when {@code value} is still built-in
     * text other than that and the new text fits {@code field}'s constraints; sets {@code changed[0]}
     * when it replaces.
     */
    private static String follow(String field, String value, Function<String, String> text,
                                 Map<String, Map<String, String>> jar, String key, String shipped, boolean[] changed) {
        String result = ConfigTextDefaults.materialize(MailConfig.class, field, value,
                ConfigTextDefaults.currentText(text, "", key), ConfigTextDefaults.tracked(jar, "", key, shipped));
        if (!Objects.equals(result, value)) {
            changed[0] = true;
        }
        return result;
    }
}
