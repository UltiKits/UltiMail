package com.ultikits.plugins.mail.config;

import com.ultikits.plugins.mail.UltiMail;
import com.ultikits.plugins.mail.i18n.CatalogueText;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The five text settings of {@code config/mail.yml} ship blank and a blank value reads the language
 * file in the server's language; a value exactly equal to the default every earlier version shipped
 * is blanked on start-up and reload and the file saved; any other value is the operator's and is
 * kept (maintainer ruling 2026-09-24 (d)). The migration is reached by reflection, so this file
 * compiles against a tree that does not have it yet.
 */
@DisplayName("config/mail.yml text settings follow the language setting when blank")
class MailTextDefaultsTest {

    /** Each setting's path, its old shipped default, the getter every reader uses and its language-file key. */
    private static final String[][] SETTINGS = {
            {"messages.mail-received", "&e[邮件] &f你收到了来自 &a{SENDER} &f的新邮件！",
                    "getMailReceivedMessage", "notify_mail_received"},
            {"recall.subject", "[{SERVER}] 回归召唤", "getRecallSubject", "recall_subject"},
            {"recall.content", "亲爱的玩家，{SERVER}想念你了！\n\n快回来看看吧，我们期待与你重逢！\n\n发送者: {SENDER}",
                    "getRecallContent", "recall_content"},
            {"email.recall-subject", "[{SERVER}] 我们想念你！", "getRecallEmailSubject", "recall_email_subject"},
            {"email.recall-content", "亲爱的 {PLAYER}，\n\n{SERVER} 服务器想念你了！快回来看看吧，我们期待与你重逢！\n\n发送者: {SENDER}",
                    "getRecallEmailContent", "recall_email_content"},
    };

    @TempDir
    Path moduleFolder;

    private File file() {
        return moduleFolder.resolve("config").resolve("mail.yml").toFile();
    }

    /** A config loaded from a mail.yml holding {@code values}, bound to a plugin answering from {@code code}. */
    private MailConfig load(Map<String, String> values, String code) throws Exception {
        File file = file();
        file.getParentFile().mkdirs();
        Files.deleteIfExists(file.toPath());
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, String> e : values.entrySet()) {
            yaml.set(e.getKey(), e.getValue());
        }
        yaml.save(file);
        final org.mockito.stubbing.Answer<String> text = CatalogueText.answer(code);
        UltiToolsPlugin plugin = Mockito.mock(UltiToolsPlugin.class, invocation -> {
            String name = invocation.getMethod().getName();
            if ("getConfigFolder".equals(name)) {
                return moduleFolder.toString();
            }
            if ("getConfigFile".equals(name)) {
                return new File(moduleFolder.toFile(), invocation.<String>getArgument(0));
            }
            if ("i18n".equals(name)) {
                return text.answer(invocation);
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        MailConfig config = new MailConfig();
        config.init(plugin);
        return config;
    }

    private static Object migrate(MailConfig config) throws Exception {
        Method m = MailConfig.class.getMethod("migrateLegacyDefaults");
        return m.invoke(config);
    }

    private static String read(MailConfig config, String getter) throws Exception {
        return (String) MailConfig.class.getMethod(getter).invoke(config);
    }

    private static Map<String, String> shipped() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String[] s : SETTINGS) {
            values.put(s[0], s[1]);
        }
        return values;
    }

    @Test
    @DisplayName("A fresh install shows the language file's text in English and in Chinese")
    void freshInstallShowsTheLanguageFile() throws Exception {
        for (String code : new String[] {"en", "zh"}) {
            MailConfig config = load(new LinkedHashMap<String, String>(), code);
            for (String[] s : SETTINGS) {
                assertThat(read(config, s[2])).as("%s under language: %s", s[0], code)
                        .isEqualTo(CatalogueText.text(code, s[3]));
            }
        }
    }

    @Test
    @DisplayName("An upgraded file holding the old shipped defaults is blanked and saved, then shows the language file's text")
    void oldShippedDefaultsAreBlankedAndSaved() throws Exception {
        for (String code : new String[] {"en", "zh"}) {
            MailConfig config = load(shipped(), code);

            assertThat(migrate(config)).isEqualTo(true);
            config.save();

            YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file());
            for (String[] s : SETTINGS) {
                assertThat(onDisk.getString(s[0])).as("%s saved under language: %s", s[0], code).isEmpty();
                assertThat(read(config, s[2])).as("%s shown under language: %s", s[0], code)
                        .isEqualTo(CatalogueText.text(code, s[3]));
            }
            assertThat(migrate(config)).as("a blank value is not rewritten again").isEqualTo(false);
        }
    }

    @Test
    @DisplayName("A customised value is kept unchanged")
    void customisedValuesAreKept() throws Exception {
        Map<String, String> custom = new LinkedHashMap<>();
        for (String[] s : SETTINGS) {
            custom.put(s[0], "operator text for " + s[0]);
        }
        MailConfig config = load(custom, "en");

        assertThat(migrate(config)).isEqualTo(false);
        for (String[] s : SETTINGS) {
            assertThat(read(config, s[2])).isEqualTo("operator text for " + s[0]);
        }
    }

    @Test
    @DisplayName("The module blanks the old shipped defaults and saves the file when it is enabled and when it is reloaded")
    void moduleRunsTheMigrationOnEnableAndReload() throws Exception {
        UltiMail plugin = mock(UltiMail.class);
        when(plugin.getLogger()).thenReturn(mock(com.ultikits.ultitools.interfaces.impl.logger.PluginLogger.class));
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        when(plugin.registerSelf()).thenCallRealMethod();
        Method onReload = UltiMail.class.getDeclaredMethod("onReload");
        onReload.setAccessible(true); // NOPMD - protected lifecycle hook, called as the framework calls it
        onReload.invoke(Mockito.doCallRealMethod().when(plugin));
        MailConfig enabled = spy(load(shipped(), "en"));
        doReturn(enabled).when(plugin).getConfig(MailConfig.class);

        plugin.registerSelf();

        verify(enabled).save();
        assertThat(read(enabled, "getRecallSubject")).isEqualTo(CatalogueText.text("en", "recall_subject"));

        MailConfig reloaded = spy(load(shipped(), "en"));
        doReturn(reloaded).when(plugin).getConfig(MailConfig.class);

        onReload.invoke(plugin);

        verify(reloaded).save();
    }
}
