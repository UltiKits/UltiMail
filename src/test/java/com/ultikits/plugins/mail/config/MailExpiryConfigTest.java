package com.ultikits.plugins.mail.config;

import com.ultikits.plugins.mail.UltiMail;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.plugins.mail.service.MailExpiryService;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MailExpiryConfigTest {
    @TempDir
    Path folder;

    private Path write(String text) throws Exception {
        Path file = folder.resolve(MailConfig.CONFIG_FILE);
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    @SuppressWarnings("unchecked")
    void oldKeyDoesNotEnableExpiryAndItsOperatorValueIsPreserved() throws Exception {
        Path file = write("# operator retention from a previous install\nmail-expire-days: 30\n");
        UltiMail plugin = ConfigFileFixture.plugin(folder, "en");
        MailConfig config = ConfigFileFixture.load(plugin);
        DataOperator<MailData> operator = mock(DataOperator.class);
        when(plugin.getDataOperator(MailData.class)).thenReturn(operator);
        new MailExpiryService(plugin, config).purgeExpiredMail();
        assertThat(config.getExpiryRetentionDays()).isZero();
        verifyNoInteractions(operator);
        String onDisk = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertThat(onDisk).contains("# operator retention from a previous install", "mail-expire-days: 30");
        assertThat(YamlConfiguration.loadConfiguration(file.toFile()).getInt("expiry.retention-days")).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void anExplicitNewValueIsPreservedAndTheSameServiceObservesARealConfigReload() throws Exception {
        Path file = write("mail-expire-days: 30\nexpiry:\n  retention-days: 12\n");
        UltiMail plugin = ConfigFileFixture.plugin(folder, "en");
        MailConfig config = ConfigFileFixture.load(plugin);
        DataOperator<MailData> operator = mock(DataOperator.class);
        when(plugin.getDataOperator(MailData.class)).thenReturn(operator);
        MailData old = new MailData();
        old.setId("reload");
        old.setRead(true);
        old.setSentTime(System.currentTimeMillis() - 13 * 86400000L);
        when(operator.getAll()).thenReturn(Collections.singletonList(old));
        MailExpiryService service = new MailExpiryService(plugin, config);
        assertThat(config.getExpiryRetentionDays()).isEqualTo(12);
        assertThat(YamlConfiguration.loadConfiguration(file.toFile()).getInt("expiry.retention-days")).isEqualTo(12);
        service.purgeExpiredMail();
        verify(operator).delById("reload");
        write("mail-expire-days: 30\nexpiry:\n  retention-days: 0\n");
        config.init(plugin);
        service.purgeExpiredMail();
        verify(operator, times(1)).getAll();
    }

    @Test
    void aFreshFileHasTheDisabledNewKeyButNotTheRemovedKey() {
        MailConfig config = ConfigFileFixture.load(ConfigFileFixture.plugin(folder, "en"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(folder.resolve(MailConfig.CONFIG_FILE).toFile());
        assertThat(yaml.contains("expiry.retention-days")).isTrue();
        assertThat(config.getExpiryRetentionDays()).isZero();
        assertThat(yaml.contains("mail-expire-days")).isFalse();
    }
}
