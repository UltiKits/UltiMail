package com.ultikits.plugins.mail.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UltiKits/UltiMail#23. {@code messages.new-mail} and {@code messages.mail-sent} are removed from
 * {@code config/mail.yml} on the ground that their text already lives in the language catalogue,
 * where it follows the server's {@code language} setting (the maintainer's message-text decision
 * of 2026-09-22). That ground is a claim about the catalogue, so it is checked here rather than
 * assumed: both entries must exist, non-blank, in BOTH shipped languages, and the two languages
 * must actually differ -- otherwise removing the config keys would leave one language with no text,
 * or with the other language's text.
 * <p>
 * The readers of the two entries are pinned elsewhere: {@code MailNotifyListenerTest}
 * ({@code notify_new_mail}, the join notification) and {@code SendMailCommandTest} /
 * {@code SendMailCommandConversationIntegrationTest} ({@code mail_sent_success}).
 */
@DisplayName("the removed message keys' text lives in the language catalogue (UltiKits/UltiMail#23)")
class RemovedMessageTextCatalogueTest {

    private static YamlConfiguration catalogue(String language) throws IOException {
        try (InputStream in = RemovedMessageTextCatalogueTest.class
                .getResourceAsStream("/lang/" + language + ".yml")) {
            assertThat(in).as("lang/%s.yml is packaged", language).isNotNull();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"notify_new_mail", "mail_sent_success"})
    @DisplayName("each replacement entry exists, non-blank, in en and zh, and the two differ")
    void presentInBothLanguages(String key) throws IOException {
        YamlConfiguration en = catalogue("en");
        YamlConfiguration zh = catalogue("zh");

        assertThat(en.isString(key)).as("en has %s", key).isTrue();
        assertThat(zh.isString(key)).as("zh has %s", key).isTrue();
        assertThat(en.getString(key)).isNotBlank();
        assertThat(zh.getString(key)).isNotBlank();
        // Translated, not copied: the zh entry carries Chinese text and the en entry does not.
        assertThat(zh.getString(key)).containsPattern("[\\u4e00-\\u9fff]");
        assertThat(en.getString(key)).doesNotContainPattern("[\\u4e00-\\u9fff]");
    }

    @Test
    @DisplayName("POSITIVE CONTROL: the same lookup reports an entry that does not exist as absent")
    void lookupCanSayNo() throws IOException {
        YamlConfiguration en = catalogue("en");

        // Without this, a loader that silently returned an all-true configuration would pass the
        // test above; the removed config paths themselves must not have leaked into the catalogue.
        assertThat(en.getKeys(false)).as("control: the catalogue was actually read").isNotEmpty();
        assertThat(en.isString("messages.new-mail")).isFalse();
        assertThat(en.isString("mail-expire-days")).isFalse();
    }
}
