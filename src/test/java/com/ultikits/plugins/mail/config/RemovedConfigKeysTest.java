package com.ultikits.plugins.mail.config;

import com.ultikits.ultitools.annotations.ConfigEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UltiKits/UltiMail#23. Removing {@code mail-expire-days}, {@code messages.new-mail} and
 * {@code messages.mail-sent} from {@link MailConfig} stops the framework writing them into a fresh
 * {@code mail.yml} and does nothing to the files already on disk: the framework writes a declared
 * default only for a key that is missing and never removes one, so every upgraded server keeps all
 * three, with whatever values its operator gave them. This check is the only thing that tells that
 * operator the values mean nothing.
 * <p>
 * The positive controls come first on purpose: a check that never fires and a server with no
 * leftover key print the same empty console, so the silent cases below prove nothing on their own.
 */
@DisplayName("RemovedConfigKeys (UltiKits/UltiMail#23)")
class RemovedConfigKeysTest {

    /**
     * The shape the framework wrote on every server that ran an earlier version, as read from the
     * shared UAT server's own {@code mail.yml}: all three removed keys sit among their siblings.
     */
    private static final String FILE_WITH_THE_REMOVED_KEYS =
            "max-items: 27\n"
            + "mail-expire-days: 30\n"
            + "notify-on-join: true\n"
            + "messages:\n"
            + "  new-mail: '&e[邮件] &f你有 &a{COUNT} &f封未读邮件！使用 /mail inbox 查看'\n"
            + "  mail-sent: '&a邮件已发送给 {PLAYER}！'\n"
            + "  mail-received: '&e[邮件] &f你收到了来自 &a{SENDER} &f的新邮件！'\n";

    /** The same file with only the three removed keys taken out. */
    private static final String FILE_WITHOUT_THE_REMOVED_KEYS =
            "max-items: 27\n"
            + "notify-on-join: true\n"
            + "messages:\n"
            + "  mail-received: '&e[邮件] &f你收到了来自 &a{SENDER} &f的新邮件！'\n";

    private static File write(File dir, String body) throws IOException {
        File file = new File(dir, "mail.yml");
        Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static List<String> warningsFor(File file) {
        return warningsFor(file, "en");
    }

    /**
     * Runs the check with a module whose language file is the real {@code language} catalogue. The
     * check takes the module since its text moved to the language file; it is called by reflection,
     * whichever signature this tree has, so this file compiles against both.
     */
    private static List<String> warningsFor(File file, String language) {
        List<String> warnings = new ArrayList<>();
        java.util.function.Consumer<String> sink = warnings::add;
        com.ultikits.ultitools.abstracts.UltiToolsPlugin plugin =
                org.mockito.Mockito.mock(com.ultikits.ultitools.abstracts.UltiToolsPlugin.class);
        org.mockito.Mockito.when(plugin.i18n(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(com.ultikits.plugins.mail.i18n.CatalogueText.answer(language));
        try {
            for (java.lang.reflect.Method m : RemovedConfigKeys.class.getMethods()) {
                if (m.getName().equals("warnAboutLeftovers")) {
                    if (m.getParameterCount() == 3) {
                        m.invoke(null, file, sink, plugin);
                    } else {
                        m.invoke(null, file, sink);
                    }
                    return warnings;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("no warnAboutLeftovers method");
    }

    @Test
    @DisplayName("Under language: zh the warning is the Chinese catalogue text, naming the file and the key")
    void warningFollowsTheLanguageSetting(@TempDir File dir) throws IOException {
        File file = write(dir, "mail-expire-days: 30\n");
        String expected = com.ultikits.plugins.mail.i18n.CatalogueText.text("zh", "removed_key_warning").replace("{FILE}", file.getPath())
                .replace("{REASON}", com.ultikits.plugins.mail.i18n.CatalogueText.text("zh", "removed_key_reason_mail_expire_days"))
                .replace("{KEY}", "mail-expire-days");

        assertThat(warningsFor(file, "zh")).containsExactly(expected);
    }

    @Test
    @DisplayName("POSITIVE CONTROL: an upgraded server's file produces one warning per removed key, each naming the module, the file and the key")
    void warnsAboutEveryLeftoverKey(@TempDir File dir) throws IOException {
        File file = write(dir, FILE_WITH_THE_REMOVED_KEYS);

        List<String> warnings = warningsFor(file);

        assertThat(warnings).hasSize(3);
        assertThat(warnings.get(0)).contains("'mail-expire-days'");
        assertThat(warnings.get(1)).contains("'messages.new-mail'");
        assertThat(warnings.get(2)).contains("'messages.mail-sent'");
        assertThat(warnings).allSatisfy(warning -> assertThat(warning)
                .contains("UltiMail")
                .contains(file.getPath())
                .contains("no longer reads")
                .contains("UltiKits/UltiMail#23")
                .contains("Delete the key"));
    }

    /**
     * Each key on its own, so that dropping any single entry from the check -- or pointing its
     * guidance at the wrong place -- turns exactly one case red, instead of hiding behind the other
     * two in a combined file.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "mail-expire-days: 30                         | mail-expire-days   | UltiKits/UltiMail#34",
        "messages:\\n  new-mail: 'x'                    | messages.new-mail  | notify_new_mail",
        "messages:\\n  mail-sent: 'x'                   | messages.mail-sent | mail_sent_success",
    })
    @DisplayName("POSITIVE CONTROL: each removed key alone produces exactly its own warning, pointing where the setting went")
    void warnsAboutEachKeyOnItsOwn(String body, String key, String whereItWent, @TempDir File dir)
            throws IOException {
        File file = write(dir, body.replace("\\n", "\n"));

        List<String> warnings = warningsFor(file);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("'" + key + "'").contains(whereItWent);
    }

    @Test
    @DisplayName("the two message warnings name the language file the text lives in")
    void messageWarningsNameTheLanguageFile(@TempDir File dir) throws IOException {
        File file = write(dir, FILE_WITH_THE_REMOVED_KEYS);

        List<String> warnings = warningsFor(file);

        assertThat(warnings.get(1)).contains("lang/");
        assertThat(warnings.get(2)).contains("lang/");
    }

    /**
     * The removed keys used {@code {COUNT}} and {@code {PLAYER}}; the catalogue entries they point
     * to use {@code {0}} and {@code {RECEIVER}}. An operator who pastes their old text into the
     * catalogue as told would have it refused by the framework's placeholder check and replaced
     * with the bundled text, so each message warning names the placeholder to use and the one it
     * replaces.
     * <p>
     * The join notification does not yet substitute {@code {0}} (it replaces {@code {COUNT}},
     * UltiKits/UltiMail#24), so the new-mail warning must not claim that {@code {0}} renders
     * the unread count; it names #24 instead. {@code {RECEIVER}} is substituted today.
     */
    @Test
    @DisplayName("the two message warnings name the catalogue placeholder, and the old one it replaces")
    void messageWarningsNameThePlaceholderChange(@TempDir File dir) throws IOException {
        File file = write(dir, FILE_WITH_THE_REMOVED_KEYS);

        List<String> warnings = warningsFor(file);

        assertThat(warnings.get(1)).contains("{0}").contains("{COUNT}")
                .contains("UltiKits/UltiMail#24")
                .doesNotContain("{0} for the unread count");
        assertThat(warnings.get(2)).contains("{RECEIVER}").contains("{PLAYER}");
        // Control: the expiry warning is about no placeholder at all.
        assertThat(warnings.get(0)).doesNotContain("{0}", "{RECEIVER}");
    }

    @Test
    @DisplayName("an empty leftover value is still a leftover key")
    void warnsAboutAnEmptyLeftoverValue(@TempDir File dir) throws IOException {
        File file = write(dir, "messages:\n  mail-sent: ''\n");

        assertThat(warningsFor(file)).hasSize(1);
    }

    @Test
    @DisplayName("no warning for the same file with only the removed keys taken out")
    void silentWithoutTheKeys(@TempDir File dir) throws IOException {
        File file = write(dir, FILE_WITHOUT_THE_REMOVED_KEYS);

        assertThat(warningsFor(file)).isEmpty();
    }

    @Test
    @DisplayName("no warning, and no exception, for a missing file or no file at all")
    void silentWithoutAFile(@TempDir File dir) {
        assertThat(warningsFor(new File(dir, "absent.yml"))).isEmpty();
        assertThat(warningsFor(null)).isEmpty();
    }

    @Test
    @DisplayName("no warning, and no exception, for a file the YAML parser rejects")
    void silentOnAnUnparseableFile(@TempDir File dir) throws IOException {
        // The framework's own config load already reports an unparseable file as SEVERE; a second
        // message from this advisory check would only add noise to it.
        File file = write(dir, "messages: [unclosed\n  new-mail: 'x'\n");

        assertThat(warningsFor(file)).isEmpty();
    }

    @Test
    @DisplayName("the check knows exactly the three removed keys, none of which MailConfig still declares")
    void knowsOnlyUndeclaredKeys() {
        List<String> declared = new ArrayList<>();
        for (Field field : MailConfig.class.getDeclaredFields()) {
            ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
            if (entry != null) {
                declared.add(entry.path());
            }
        }

        assertThat(declared).as("control: the scan sees MailConfig's keys").contains("max-items");
        assertThat(RemovedConfigKeys.removedKeys().keySet())
                .containsExactly("mail-expire-days", "messages.new-mail", "messages.mail-sent")
                .doesNotContainAnyElementsOf(declared);
    }
}
