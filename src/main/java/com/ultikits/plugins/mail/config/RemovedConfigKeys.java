package com.ultikits.plugins.mail.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reports configuration keys this module no longer reads but which are still sitting in the
 * operator's own {@code config/mail.yml}.
 * <p>
 * Deleting a key from {@link MailConfig} stops the framework writing it into a fresh file, but it
 * does nothing to the files already on disk: the framework only ever writes a declared default for
 * a key that is <em>missing</em>, and saves every key it loaded, so an existing install keeps the
 * key, keeps whatever value the operator gave it, and gets no indication that the value means
 * nothing. This class is that indication -- one warning per leftover key, naming the module, the
 * file and the key, and saying where the setting went (UltiKits/UltiMail#23).
 *
 * @author wisdomme
 * @version 1.1.0
 */
public final class RemovedConfigKeys {

    /**
     * Every key removed from {@code config/mail.yml}, mapped to the language-file key of what an
     * operator should be told about it. Insertion order is the order the warnings are emitted in.
     * The values are informational: the text is read by {@link #reasonFor}, whose literal lookups the
     * language guard checks, so a key added here needs a case there too (a missing case fails loudly).
     */
    private static final Map<String, String> REMOVED;

    static {
        Map<String, String> removed = new LinkedHashMap<String, String>();
        removed.put("mail-expire-days", "removed_key_reason_mail_expire_days");
        removed.put("messages.new-mail", "removed_key_reason_new_mail");
        removed.put("messages.mail-sent", "removed_key_reason_mail_sent");
        REMOVED = Collections.unmodifiableMap(removed);
    }

    private RemovedConfigKeys() {
        // Utility class
    }

    /**
     * The guidance printed for one removed key, from the language file. Each removed key names its
     * own entry here, so a key added to {@link #REMOVED} without a case fails loudly instead of being
     * given another key's explanation.
     */
    private static String reasonFor(String removedKey, UltiToolsPlugin plugin) {
        switch (removedKey) {
            case "mail-expire-days":
                return plugin.i18n("removed_key_reason_mail_expire_days");
            case "messages.new-mail":
                return plugin.i18n("removed_key_reason_new_mail");
            case "messages.mail-sent":
                return plugin.i18n("removed_key_reason_mail_sent");
            default:
                throw new IllegalStateException("No guidance for removed key " + removedKey);
        }
    }

    /**
     * The keys this class knows about, in the order it reports them.
     *
     * @return an unmodifiable map of removed key path to the language-file key of the guidance
     *         printed for it
     */
    public static Map<String, String> removedKeys() {
        return REMOVED;
    }

    /**
     * Emit one warning per removed key that is still present in the operator's configuration file.
     * <p>
     * Silent when the file is absent or unreadable -- there is then nothing to report and nothing
     * to be sure of. A parse failure is deliberately not reported here: the framework's own config
     * loading already reports an unparseable file, and a second message from this check would only
     * add noise to it.
     *
     * @param configFile the operator's {@code config/mail.yml}; may be {@code null}
     * @param warn       where to send each warning, normally the module logger's warn method
     * @param plugin     the module, whose language file gives the warning its text
     */
    public static void warnAboutLeftovers(File configFile, Consumer<String> warn, UltiToolsPlugin plugin) {
        if (configFile == null || !configFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            return;
        }
        for (Map.Entry<String, String> entry : REMOVED.entrySet()) {
            if (yaml.contains(entry.getKey())) {
                // No "[UltiMail]" prefix: the module logger adds that itself, and the module is
                // still named in the sentence for any consumer that does not.
                warn.accept(plugin.i18n("removed_key_warning")
                        .replace("{FILE}", configFile.getPath())
                        .replace("{REASON}", reasonFor(entry.getKey(), plugin))
                        .replace("{KEY}", entry.getKey()));
            }
        }
    }
}
