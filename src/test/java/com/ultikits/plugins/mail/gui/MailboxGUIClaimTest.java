package com.ultikits.plugins.mail.gui;

import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.plugins.mail.service.MailService;
import com.ultikits.plugins.mail.utils.MockBukkitHelper;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The mailbox GUI's click handler rendering the outcome of a claim, and re-running a mail's
 * attached commands after a refused record (UltiKits/UltiMail#31).
 * <p>
 * The handler is private and reached here by reflection, the same way a click reaches it through
 * the icon's callback; the player is a real MockBukkit player so the messages read are the ones the
 * player receives.
 */
@DisplayName("MailboxGUI claim rendering (UltiKits/UltiMail#31)")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MailboxGUIClaimTest {

    private ServerMock server;
    private PlayerMock player;
    private MailService mailService;
    private MailboxGUI gui;
    /** The page refresh after a click builds its navigation buttons through the framework instance. */
    private MockedStatic<UltiTools> framework;

    @BeforeEach
    void setUp() {
        server = MockBukkitHelper.bootstrapServer();
        UltiTools ultiTools = mock(UltiTools.class);
        when(ultiTools.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        framework = mockStatic(UltiTools.class);
        framework.when(UltiTools::getInstance).thenReturn(ultiTools);
        UltiToolsPlugin plugin = TestHelper.mockUltiToolsPlugin();
        player = server.addPlayer("reader");
        mailService = mock(MailService.class);
        when(mailService.getInbox(any())).thenReturn(new ArrayList<>());
        gui = new MailboxGUI(player, mailService, plugin);
        injectInventory(gui);
    }

    /**
     * The page is never opened here, so obliviate's {@code Gui} has no inventory yet; the refresh at
     * the end of a click writes its navigation row into one. A mock inventory of the page's size is
     * enough - these tests read what the player was told, not the rendered page.
     */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static void injectInventory(Object page) {
        org.bukkit.inventory.Inventory inventory = mock(org.bukkit.inventory.Inventory.class);
        when(inventory.getSize()).thenReturn(54);
        Class<?> type = page.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("inventory");
                field.setAccessible(true);
                field.set(page, inventory);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("no inventory field on " + page.getClass());
    }

    @AfterEach
    void tearDown() {
        framework.close();
        TestHelper.cleanupMocks();
        MockBukkitHelper.safeUnmock();
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void click(MailData mail) throws Exception {
        Method handler = MailboxGUI.class.getDeclaredMethod("handleMailClick", MailData.class);
        handler.setAccessible(true);
        handler.invoke(gui, mail);
    }

    private List<String> messages() {
        List<String> received = new ArrayList<>();
        String line;
        while ((line = player.nextMessage()) != null) {
            received.add(line);
        }
        return received;
    }

    private MailData unclaimedMailWithItems() {
        MailData mail = new MailData();
        mail.setId("mail-9");
        mail.setRead(true);
        mail.setItems("payload");
        mail.setClaimed(false);
        return mail;
    }

    @Test
    @DisplayName("a claim that could not be recorded is reported as refused, never as a success")
    void aClaimThatCouldNotBeRecordedIsReportedAsRefused() throws Exception {
        MailData mail = unclaimedMailWithItems();
        when(mailService.getItemCount(mail)).thenReturn(1);
        when(mailService.claimAttachment(mail, player)).thenReturn(MailService.ClaimResult.notRecorded());

        click(mail);

        List<String> received = messages();
        assertThat(received).anyMatch(m -> m.contains("[claim_not_recorded]"));
        assertThat(received).noneMatch(m -> m.contains("[claim_success]"));
    }

    @Test
    @DisplayName("control: a recorded claim still reports its success with the item count")
    void aRecordedClaimReportsSuccess() throws Exception {
        MailData mail = unclaimedMailWithItems();
        when(mailService.getItemCount(mail)).thenReturn(1);
        when(mailService.claimAttachment(mail, player))
                .thenReturn(MailService.ClaimResult.claimed(new ItemStack[]{new ItemStack(Material.DIAMOND)}));

        click(mail);

        assertThat(messages()).anyMatch(m -> m.contains("[claim_success]"));
    }

    /**
     * After a refused marker write the reader is told nothing ran and to read the mail again - which
     * has to be true in the GUI as well. So this drives a real {@link MailService} over a storage
     * stub and a player that records the commands it runs, and reads the commands that ran and the
     * mail's own flag: the first click's marker write fails, the second succeeds, a third click must
     * not run anything again.
     */
    @Test
    @DisplayName("a refused marker write is retried on the next click, and the commands run exactly once")
    @SuppressWarnings("unchecked")
    void refusedCommandsRunExactlyOnceAcrossClicks() throws Exception {
        List<String> ran = new ArrayList<>();
        PlayerMock reader = new PlayerMock(server, "commandreader") {
            @Override
            public boolean performCommand(String command) {
                ran.add(command);
                return true;
            }
        };
        server.addPlayer(reader);
        UltiToolsPlugin plugin = TestHelper.mockUltiToolsPlugin();
        DataOperator<MailData> operator = mock(DataOperator.class);
        Query<MailData> query = mock(Query.class);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenReturn(query);
        when(query.list()).thenReturn(new ArrayList<>());
        when(operator.query()).thenReturn(query);
        boolean[] markerWritesFail = {true};
        doAnswer(inv -> {
            if (markerWritesFail[0] && ((MailData) inv.getArgument(0)).isCommandsExecuted()) {
                throw new DataAccessException("connection lost");
            }
            return null;
        }).when(operator).update(any(MailData.class));
        when(plugin.getDataOperator(any())).thenReturn((DataOperator) operator);
        MailService service = new MailService();
        TestHelper.injectField(service, "plugin", plugin);
        TestHelper.injectField(service, "config", new MailConfig());
        service.init();
        MailboxGUI page = new MailboxGUI(reader, service, plugin);
        injectInventory(page);
        Method handler = MailboxGUI.class.getDeclaredMethod("handleMailClick", MailData.class);
        handler.setAccessible(true); // NOPMD - the click handler is private; a click reaches it the same way
        MailData mail = new MailData();
        mail.setId("mail-10");
        mail.setRead(false);
        mail.setCommands("[\"give %player% diamond 1\"]");

        handler.invoke(page, mail);
        assertThat(ran).isEmpty();
        assertThat(mail.isCommandsExecuted()).isFalse();
        markerWritesFail[0] = false;
        handler.invoke(page, mail);
        handler.invoke(page, mail);

        assertThat(ran).containsExactly("give commandreader diamond 1");
        assertThat(mail.isCommandsExecuted()).isTrue();
    }
}
