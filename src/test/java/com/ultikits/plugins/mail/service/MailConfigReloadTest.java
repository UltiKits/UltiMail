package com.ultikits.plugins.mail.service;

import com.ultikits.plugins.mail.UltiMail;
import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiMail#20: {@code /ul reload UltiMail} re-reads {@code config/mail.yml} through
 * {@code ConfigManager#reloadConfigs}, which calls {@code init(plugin)} again on the SAME
 * {@link MailConfig} instance the container injected into {@link MailService}. This test proves
 * the service observes such an in-place re-init on the very next send, i.e. it does not cache a
 * config value when it is created.
 * <p>
 * Both sends are refused before any Bukkit or database access: the first by the content-length
 * check (proving the 20-character subject passed the original 50-character limit), the second by
 * the subject-length check with the reloaded limit of 10.
 */
@DisplayName("MailService observes an in-place MailConfig reload (UltiKits/UltiMail#20)")
class MailConfigReloadTest {

    private static final String SUBJECT = "ReloadCheckSubject01";

    @TempDir
    Path moduleFolder;

    @AfterEach
    void tearDown() {
        TestHelper.cleanupMocks();
    }

    @Test
    @DisplayName("editing max-subject-length and re-initialising the same config refuses the next long subject")
    void inPlaceReloadOfMaxSubjectLengthIsObserved() throws Exception {
        assertThat(SUBJECT).hasSize(20);
        File configFile = moduleFolder.resolve("config").resolve("mail.yml").toFile();
        assertThat(configFile.getParentFile().mkdirs()).isTrue();
        write(configFile, "max-subject-length: 50\nmax-content-length: 50\n");

        UltiToolsPlugin configPlugin = mock(UltiMail.class, CALLS_REAL_METHODS);
        setResourceFolderPath(configPlugin, moduleFolder.toString());

        MailConfig config = new MailConfig();
        config.init(configPlugin);
        assertThat(config.getMaxSubjectLength()).isEqualTo(50);

        MailService service = new MailService();
        setField(service, "config", config);
        setField(service, "plugin", TestHelper.mockUltiToolsPlugin());

        Player sender = mock(Player.class);
        when(sender.getUniqueId()).thenReturn(UUID.randomUUID());
        String tooLongContent = repeat('x', 51);

        assertThat(service.sendMail(sender, "Receiver", SUBJECT, tooLongContent, null)).isFalse();
        verify(sender).sendRawMessage(contains("[send_content_too_long]"));
        verify(sender, never()).sendRawMessage(contains("[send_subject_too_long]"));

        write(configFile, "max-subject-length: 10\nmax-content-length: 50\n");
        config.init(configPlugin);

        assertThat(service.sendMail(sender, "Receiver", SUBJECT, tooLongContent, null)).isFalse();
        verify(sender).sendRawMessage(contains("[send_subject_too_long]"));
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private static void write(File file, String content) throws Exception {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // simulates @Autowired field injection
    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = MailService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setResourceFolderPath(UltiToolsPlugin plugin, String path) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        field.setAccessible(true);
        field.set(plugin, path);
    }
}
