package com.ultikits.plugins.mail.commands;

import com.ultikits.plugins.mail.service.MailService;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Free-text mail content and subject with a trailing flag word (UltiTools-API 6.3.0 Phase 19, maintainer ruling
 * of 2026-10-14). The documented syntax stays {@code /mail sendall <content> [items]} and
 * {@code /sendmail <player> <subject> [attach]}; content and subject may now hold several words, and a final word
 * {@code items} / {@code attach} selects the attachment path while the words before it are the text.
 */
@ExtendWith(MockitoExtension.class)
class FreeTextTrailingFlagTest {

    @Nested
    @DisplayName("TrailingFlag splits a trailing flag word off free text")
    class SplitTests {

        @Test
        @DisplayName("several words without the flag word are the whole text, no flag")
        void multiWordWithoutFlag() {
            TrailingFlag t = TrailingFlag.split(new String[]{"server", "restart", "tonight"}, "items");
            assertThat(t.getText()).isEqualTo("server restart tonight");
            assertThat(t.isFlagged()).isFalse();
        }

        @Test
        @DisplayName("several words ending with the flag word: the flag is set and the words before it are the text")
        void multiWordWithFlag() {
            TrailingFlag t = TrailingFlag.split(new String[]{"server", "restart", "tonight", "items"}, "items");
            assertThat(t.getText()).isEqualTo("server restart tonight");
            assertThat(t.isFlagged()).isTrue();
        }

        @Test
        @DisplayName("the flag word alone sets the flag and leaves an empty text")
        void flagWordAlone() {
            TrailingFlag t = TrailingFlag.split(new String[]{"attach"}, "attach");
            assertThat(t.getText()).isEmpty();
            assertThat(t.isFlagged()).isTrue();
        }

        @Test
        @DisplayName("no words (or null) is an empty text without the flag")
        void emptyText() {
            assertThat(TrailingFlag.split(new String[0], "items").getText()).isEmpty();
            assertThat(TrailingFlag.split(new String[0], "items").isFlagged()).isFalse();
            assertThat(TrailingFlag.split(null, "items").getText()).isEmpty();
        }

        @Test
        @DisplayName("only the final word counts as the flag; an earlier flag word stays text")
        void onlyTheFinalWordIsTheFlag() {
            TrailingFlag t = TrailingFlag.split(new String[]{"items", "for", "everyone"}, "items");
            assertThat(t.getText()).isEqualTo("items for everyone");
            assertThat(t.isFlagged()).isFalse();
            TrailingFlag u = TrailingFlag.split(new String[]{"items", "items"}, "items");
            assertThat(u.getText()).isEqualTo("items");
            assertThat(u.isFlagged()).isTrue();
        }

        @Test
        @DisplayName("the flag word matches regardless of letter case, like the command literal it replaces")
        void flagIgnoresCase() {
            assertThat(TrailingFlag.split(new String[]{"hi", "ITEMS"}, "items").isFlagged()).isTrue();
        }
    }

    @Nested
    @DisplayName("/mail sendall <content...>")
    class SendAllTests {

        @Mock
        private MailService mailService;
        @Mock
        private Player player;
        private MailCommand command;

        @BeforeEach
        void setUp() throws Exception {
            UltiToolsPlugin plugin = TestHelper.mockUltiToolsPlugin();
            command = spy(new MailCommand(mailService));
            TestHelper.injectField(command, "plugin", plugin);
            lenient().doNothing().when(command).sendAll(any(), anyString());
            lenient().doNothing().when(command).sendAllWithItems(any(), anyString());
        }

        @AfterEach
        void tearDown() {
            TestHelper.cleanupMocks();
        }

        @Test
        @DisplayName("the mapping is the framework's varargs form")
        void mappingIsVarargs() throws Exception {
            Method m = MailCommand.class.getDeclaredMethod("sendAllFreeText", Player.class, String[].class);
            assertThat(m.getAnnotation(CmdMapping.class).format()).isEqualTo("sendall <content...>");
            assertThat(MailCommand.class.getDeclaredMethod("sendAll", Player.class, String.class)
                    .getAnnotation(CmdMapping.class)).isNull();
            assertThat(MailCommand.class.getDeclaredMethod("sendAllWithItems", Player.class, String.class)
                    .getAnnotation(CmdMapping.class)).isNull();
        }

        @Test
        @DisplayName("multi-word content without the flag word sends text only")
        void multiWordText() {
            command.sendAllFreeText(player, new String[]{"server", "restart", "tonight"});
            verify(command).sendAll(player, "server restart tonight");
            verify(command, never()).sendAllWithItems(any(), anyString());
        }

        @Test
        @DisplayName("multi-word content ending with items opens the attachment path with the words before it")
        void multiWordWithItems() {
            command.sendAllFreeText(player, new String[]{"server", "restart", "tonight", "items"});
            verify(command).sendAllWithItems(player, "server restart tonight");
            verify(command, never()).sendAll(any(), anyString());
        }

        @Test
        @DisplayName("items alone has no content: nothing is sent and the sendall usage line is shown")
        void flagWordAlone() {
            command.sendAllFreeText(player, new String[]{"items"});
            verify(command, never()).sendAll(any(), anyString());
            verify(command, never()).sendAllWithItems(any(), anyString());
            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("help_sendall")));
        }

        @Test
        @DisplayName("empty content: nothing is sent and the sendall usage line is shown")
        void emptyContent() {
            command.sendAllFreeText(player, new String[0]);
            verify(command, never()).sendAll(any(), anyString());
            verify(command, never()).sendAllWithItems(any(), anyString());
            verify(player).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("help_sendall")));
        }
    }

    @Nested
    @DisplayName("/sendmail <player> <subject...>")
    class SendMailTests {

        @Mock
        private MailService mailService;
        @Mock
        private Plugin bukkitPlugin;
        @Mock
        private Player sender;
        private SendMailCommand command;

        @BeforeEach
        void setUp() throws Exception {
            UltiToolsPlugin plugin = TestHelper.mockUltiToolsPlugin();
            command = spy(new SendMailCommand(mailService, bukkitPlugin));
            TestHelper.injectField(command, "ultiPlugin", plugin);
            lenient().doNothing().when(command).sendMail(any(), anyString(), anyString());
            lenient().doNothing().when(command).sendMailWithItems(any(), anyString(), anyString());
        }

        @AfterEach
        void tearDown() {
            TestHelper.cleanupMocks();
        }

        @Test
        @DisplayName("the mapping is the framework's varargs form")
        void mappingIsVarargs() throws Exception {
            Method m = SendMailCommand.class.getDeclaredMethod("sendMailFreeText", Player.class, String.class,
                    String[].class);
            assertThat(m.getAnnotation(CmdMapping.class).format()).isEqualTo("<player> <subject...>");
            assertThat(SendMailCommand.class.getDeclaredMethod("sendMail", Player.class, String.class, String.class)
                    .getAnnotation(CmdMapping.class)).isNull();
            assertThat(SendMailCommand.class.getDeclaredMethod("sendMailWithItems", Player.class, String.class,
                    String.class).getAnnotation(CmdMapping.class)).isNull();
        }

        @Test
        @DisplayName("a multi-word subject without the flag word starts a text mail")
        void multiWordSubject() {
            command.sendMailFreeText(sender, "Alex", new String[]{"meeting", "at", "spawn"});
            verify(command).sendMail(sender, "Alex", "meeting at spawn");
            verify(command, never()).sendMailWithItems(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("a multi-word subject ending with attach starts the attachment path with the words before it")
        void multiWordSubjectWithAttach() {
            command.sendMailFreeText(sender, "Alex", new String[]{"meeting", "at", "spawn", "attach"});
            verify(command).sendMailWithItems(sender, "Alex", "meeting at spawn");
            verify(command, never()).sendMail(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("attach alone has no subject: nothing starts and the sendmail help is shown")
        void flagWordAlone() {
            command.sendMailFreeText(sender, "Alex", new String[]{"attach"});
            verify(command, never()).sendMail(any(), anyString(), anyString());
            verify(command, never()).sendMailWithItems(any(), anyString(), anyString());
            verify(sender).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("help_sendmail_title")));
        }

        @Test
        @DisplayName("an empty subject: nothing starts and the sendmail help is shown")
        void emptySubject() {
            command.sendMailFreeText(sender, "Alex", new String[0]);
            verify(command, never()).sendMail(any(), anyString(), anyString());
            verify(command, never()).sendMailWithItems(any(), anyString(), anyString());
            verify(sender).sendMessage(ArgumentMatchers.<String>argThat(msg -> msg.contains("help_sendmail_title")));
        }
    }
}
