package com.ultikits.plugins.mail.service;

import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiMail#43: a mail's attached commands, when the mail is clicked in the mailbox, run on the
 * tick after the click instead of inside the {@code InventoryClickEvent} handler.
 * <p>
 * Since UltiTools-Reborn#541 a module command body runs at the moment it is dispatched, so an attached
 * command that opens or closes an inventory ({@code /kits}, another module's menu) would otherwise run inside
 * the click, which Paper does not allow. The executed marker is still written first, in the click, exactly as
 * {@code executeMailCommands} does (UltiKits/UltiMail#31); only the dispatch loop moves.
 */
@DisplayName("MailService#executeMailCommandsDeferred (UltiKits/UltiMail#43)")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MailServiceDeferredCommandsTest {

    private MailService service;
    private DataOperator<MailData> operator;
    private PluginLogger logger;
    private Player reader;
    private BukkitScheduler scheduler;
    private MockedStatic<Bukkit> bukkit;
    private ConsoleCommandSender console;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiToolsPlugin plugin = TestHelper.mockUltiToolsPlugin();
        logger = plugin.getLogger();
        operator = mock(DataOperator.class);
        when(plugin.getDataOperator(MailData.class)).thenReturn(operator);
        reader = mock(Player.class);
        when(reader.getName()).thenReturn("Reader");
        scheduler = mock(BukkitScheduler.class);
        console = mock(ConsoleCommandSender.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
        service = new MailService();
        TestHelper.injectField(service, "plugin", plugin);
        TestHelper.injectField(service, "config", new MailConfig());
        TestHelper.injectField(service, "dataOperator", operator);
        TestHelper.injectField(service, "bukkitPlugin", mock(Plugin.class));
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
        TestHelper.cleanupMocks();
    }

    private static MailData mailWith(String commandsJson) {
        MailData mail = new MailData();
        mail.setId("mail-1");
        mail.setCommands(commandsJson);
        return mail;
    }

    /** The task the service handed to the scheduler, or a failure if it handed none. */
    private Runnable scheduledTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(any(Plugin.class), task.capture());
        return task.getValue();
    }

    @Test
    @DisplayName("the marker is written in the call, no command runs until the scheduled task runs, then each runs once")
    void recordsFirstThenDispatchesInTheTask() throws Exception {
        MailData mail = mailWith("[\"give %player% diamond 1\"]");

        service.executeMailCommandsDeferred(reader, mail);

        assertThat(mail.isCommandsExecuted()).isTrue();
        verify(operator).update(mail);
        verify(reader, never()).performCommand(anyString());

        scheduledTask().run();

        verify(reader, times(1)).performCommand("give Reader diamond 1");
    }

    @Test
    @DisplayName("player and console commands run in their written order, console: prefix and %player% handled as before")
    void keepsOrderAndPlaceholders() throws Exception {
        when(reader.performCommand(anyString())).thenReturn(true);
        bukkit.when(() -> Bukkit.dispatchCommand(any(), anyString())).thenReturn(true);
        MailData mail = mailWith("[\"a %player%\",\"console:b %player%\",\"c\"]");

        service.executeMailCommandsDeferred(reader, mail);
        scheduledTask().run();

        InOrder order = inOrder(reader);
        order.verify(reader).performCommand("a Reader");
        bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), eq("b Reader")));
        order.verify(reader).performCommand("c");
    }

    @Test
    @DisplayName("a refused marker write runs nothing and schedules nothing, so reading the mail again retries")
    void refusedWriteSchedulesNothing() throws Exception {
        doThrow(new DataAccessException("connection lost")).when(operator).update(any(MailData.class));
        MailData mail = mailWith("[\"give %player% diamond 1\"]");

        service.executeMailCommandsDeferred(reader, mail);

        assertThat(mail.isCommandsExecuted()).isFalse();
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
        verify(reader, never()).performCommand(anyString());
        verify(reader).sendMessage(contains("mail_commands_not_recorded"));
    }

    @Test
    @DisplayName("a command the server reports as not run is logged inside the task, naming the mail and the command, and the next still runs")
    void rejectedCommandIsLoggedAndTheRestRun() throws Exception {
        when(reader.performCommand("unknown")).thenReturn(false);
        when(reader.performCommand("next")).thenReturn(true);
        MailData mail = mailWith("[\"unknown\",\"next\"]");

        service.executeMailCommandsDeferred(reader, mail);
        verify(logger, never()).warn(contains("log_mail_command_rejected"));
        scheduledTask().run();

        verify(logger).warn(contains("log_mail_command_rejected"));
        verify(reader).performCommand("next");
    }

    @Test
    @DisplayName("a command that throws is logged and does not stop the commands after it")
    void throwingCommandDoesNotStopTheRest() throws Exception {
        when(reader.performCommand("boom")).thenThrow(new IllegalStateException("handler failed"));
        when(reader.performCommand("after")).thenReturn(true);
        MailData mail = mailWith("[\"boom\",\"after\"]");

        service.executeMailCommandsDeferred(reader, mail);
        scheduledTask().run();

        verify(logger).warn(contains("log_mail_command_failed"));
        verify(reader).performCommand("after");
    }

    @Test
    @DisplayName("a task the scheduler refuses is not claimed as run: the marker is cleared, the failure logged, and nothing ran")
    void refusedSchedulingClearsTheMarker() throws Exception {
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class)))
                .thenThrow(new IllegalPluginAccessException("Plugin attempted to register task while disabled"));
        MailData mail = mailWith("[\"give %player% diamond 1\"]");

        service.executeMailCommandsDeferred(reader, mail);

        assertThat(mail.isCommandsExecuted()).as("a later read must be able to run the commands").isFalse();
        verify(logger).error(contains("log_mail_commands_failed"));
        verify(reader, never()).performCommand(anyString());
        verify(operator, times(2)).update(mail);
    }

    @Test
    @DisplayName("nothing is scheduled for a mail without commands, with an empty list, or whose commands already ran")
    void nothingToRunSchedulesNothing() throws Exception {
        MailData none = mailWith(null);
        MailData empty = mailWith("[]");
        MailData done = mailWith("[\"x\"]");
        done.setCommandsExecuted(true);

        service.executeMailCommandsDeferred(reader, none);
        service.executeMailCommandsDeferred(reader, empty);
        service.executeMailCommandsDeferred(reader, done);

        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
        verify(operator, never()).update(any(MailData.class));
    }

    @Test
    @DisplayName("control: executeMailCommands, the typed /mail read path, still runs the commands in the call itself")
    void typedPathStillRunsInline() throws Exception {
        when(reader.performCommand(anyString())).thenReturn(true);
        MailData mail = mailWith("[\"give %player% diamond 1\"]");

        service.executeMailCommands(reader, mail);

        verify(reader).performCommand("give Reader diamond 1");
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }
}
