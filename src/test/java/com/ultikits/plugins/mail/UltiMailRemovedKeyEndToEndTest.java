package com.ultikits.plugins.mail;

import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.manager.ConfigManager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * UltiKits/UltiMail#23, end to end (gate 1 WR-01). {@code UltiMailTest$RemovedKeyCheckWiring} stubs
 * {@code operatorConfigFile()}, so it proves the check is called but not that it reads the file the
 * framework actually manages: {@code operatorConfigFile()} could resolve any other path, the check
 * would return silently on a file that does not exist, and every upgraded server would look exactly
 * like a clean one. Here nothing about the file is stubbed: the module's folder is a temp directory,
 * a real {@link ConfigManager} registers the real {@link MailConfig} against the operator's
 * {@code config/mail.yml} (loading it and writing the missing declared defaults, as it does on a
 * real server before {@code registerSelf()} runs), and then the real {@code registerSelf()} and
 * {@code onReload()} resolve the file themselves. Only the logger and the enable line's catalogue
 * lookup are replaced.
 * <p>
 * This also pins the framework fact the warning depends on: loading and saving the configuration
 * keeps an undeclared key in the operator's file rather than dropping it.
 */
@DisplayName("the removed-key warning reads the operator's real mail.yml (UltiKits/UltiMail#23)")
class UltiMailRemovedKeyEndToEndTest {

    /**
     * An upgraded server's file, as on the shared UAT server: the three removed keys are present,
     * and some declared keys are missing so the framework has something to write.
     */
    private static final String UPGRADED_FILE =
            "max-items: 27\n"
            + "mail-expire-days: 30\n"
            + "notify-on-join: true\n"
            + "messages:\n"
            + "  new-mail: '&e[Mail] {COUNT}'\n"
            + "  mail-sent: '&aSent to {PLAYER}'\n"
            + "  mail-received: '&eFrom {SENDER}'\n";

    /** The same file with only the three removed keys taken out. */
    private static final String CLEAN_FILE =
            "max-items: 27\n"
            + "notify-on-join: true\n"
            + "messages:\n"
            + "  mail-received: '&eFrom {SENDER}'\n";

    @TempDir
    Path moduleFolder;

    private PluginLogger logger;

    /** A module whose folder is the temp directory, with the operator's file registered for real. */
    private UltiMail moduleWithOperatorFile(String body) throws Exception {
        File file = operatorFile();
        assertThat(file.getParentFile().mkdirs()).isTrue();
        Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));

        UltiMail plugin = mock(UltiMail.class, CALLS_REAL_METHODS);
        setField(UltiToolsPlugin.class, plugin, "resourceFolderPath", moduleFolder.toString());
        new ConfigManager().register(plugin, new MailConfig());

        logger = mock(PluginLogger.class);
        doReturn(logger).when(plugin).getLogger();
        doAnswer(inv -> inv.getArgument(0)).when(plugin).i18n(anyString());
        return plugin;
    }

    private File operatorFile() {
        return moduleFolder.resolve("config").resolve("mail.yml").toFile();
    }

    private List<String> warnings() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeast(0)).warn(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("POSITIVE CONTROL: after the framework loads the upgraded file, enabling the module warns once per removed key, naming that file")
    void enablingWarnsAboutTheRealFile() throws Exception {
        UltiMail plugin = moduleWithOperatorFile(UPGRADED_FILE);

        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(operatorFile());
        assertThat(onDisk.contains("send-cooldown"))
                .as("control: the framework loaded the file and wrote a missing declared default")
                .isTrue();
        assertThat(onDisk.contains("mail-expire-days") && onDisk.contains("messages.new-mail")
                && onDisk.contains("messages.mail-sent"))
                .as("the framework keeps the undeclared keys in the operator's file")
                .isTrue();

        assertThat(plugin.registerSelf()).isTrue();

        assertThat(warnings()).hasSize(3);
        assertThat(warnings()).allSatisfy(w -> assertThat(w)
                .startsWith(operatorFile().getPath())
                .contains("which this version of UltiMail no longer reads"));
        assertThat(String.join("\n", warnings()))
                .contains("'mail-expire-days'", "'messages.new-mail'", "'messages.mail-sent'");
    }

    @Test
    @DisplayName("POSITIVE CONTROL: reloading the module warns once per removed key, naming that file")
    void reloadingWarnsAboutTheRealFile() throws Exception {
        UltiMail plugin = moduleWithOperatorFile(UPGRADED_FILE);

        plugin.onReload();

        assertThat(warnings()).hasSize(3);
        assertThat(warnings()).allSatisfy(w -> assertThat(w).startsWith(operatorFile().getPath()));
    }

    @Test
    @DisplayName("the same flow on the same file without the removed keys warns at neither entry point")
    void cleanFileWarnsNowhere() throws Exception {
        UltiMail plugin = moduleWithOperatorFile(CLEAN_FILE);

        assertThat(plugin.registerSelf()).isTrue();
        plugin.onReload();

        assertThat(warnings()).isEmpty();
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
