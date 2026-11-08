package com.ultikits.plugins.mail.service;

import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MailExpiryServiceTest {
    private static final long NOW = 1800000000000L;
    private static final long DAY = 86400000L;
    private UltiToolsPlugin plugin;
    private MailConfig config;
    private PluginLogger logger;
    private DataOperator<MailData> operator;
    private MailExpiryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        plugin = mock(UltiToolsPlugin.class);
        logger = mock(PluginLogger.class);
        operator = mock(DataOperator.class);
        when(plugin.getDataOperator(MailData.class)).thenReturn(operator);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.i18n(anyString())).thenAnswer(call -> call.getArgument(0) + " {MAIL} {ERROR}");
        config = new MailConfig();
        config.setExpiryRetentionDays(30);
        service = new MailExpiryService(plugin, config);
    }

    private MailData oldMail(String id) {
        MailData mail = new MailData();
        mail.setId(id);
        mail.setRead(true);
        mail.setSentTime(NOW - 31 * DAY);
        return mail;
    }

    private void stored(MailData... mails) {
        when(operator.getAll()).thenReturn(Arrays.asList(mails));
    }

    @Test
    void expiryIsDisabledByDefaultWithoutAnyStorageAccess() {
        MailConfig defaults = new MailConfig();
        new MailExpiryService(plugin, defaults).purgeExpiredMail();
        assertThat(defaults.getExpiryRetentionDays()).isZero();
        verify(plugin, never()).getDataOperator(MailData.class);
        verifyNoInteractions(operator);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 366, Integer.MAX_VALUE})
    void disabledOrInvalidInMemorySettingsNeverPurge(int days) {
        config.setExpiryRetentionDays(days);
        service.purgeExpiredMailAt(NOW);
        verifyNoInteractions(operator);
    }

    @Test
    void deletesTheStoredRowRegardlessOfWhichSideDeletedIt() {
        MailData neither = oldMail("neither");
        MailData senderOnly = oldMail("sender-only");
        senderOnly.setDeletedBySender(true);
        MailData receiverOnly = oldMail("receiver-only");
        receiverOnly.setDeletedByReceiver(true);
        stored(neither, senderOnly, receiverOnly);
        service.purgeExpiredMailAt(NOW);
        verify(operator).delById("neither");
        verify(operator).delById("sender-only");
        verify(operator).delById("receiver-only");
        verify(operator, never()).updateCounted(any());
    }

    @ParameterizedTest
    @CsvSource({"-1,true", "0,true", "1,false"})
    void exactCutoffUsesSentTimeInTwentyFourHourDays(long offset, boolean expired) {
        MailData mail = oldMail("boundary");
        mail.setSentTime(NOW - 30 * DAY + offset);
        stored(mail);
        service.purgeExpiredMailAt(NOW);
        verify(operator, times(expired ? 1 : 0)).delById("boundary");
    }

    @Test
    void unreadMailNeverExpiresEvenWithoutAttachments() {
        MailData mail = oldMail("unread");
        mail.setRead(false);
        stored(mail);
        service.purgeExpiredMailAt(NOW);
        verify(operator, never()).delById(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE, NOW, NOW + DAY})
    void retainsInvalidAndFutureTimestamps(long sentTime) {
        MailData mail = oldMail("timestamp");
        mail.setSentTime(sentTime);
        stored(mail);
        service.purgeExpiredMailAt(NOW);
        verify(operator, never()).delById(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"encoded-items", "corrupt payload"})
    void neverDeserializesOrDeletesUnclaimedItems(String items) {
        MailData mail = oldMail("items");
        mail.setItems(items);
        stored(mail);
        service.purgeExpiredMailAt(NOW);
        verify(operator, never()).delById(any());
        verifyNoInteractions(logger);
    }

    @Test
    void alreadyClaimedItemsMayExpire() {
        MailData mail = oldMail("claimed");
        mail.setItems("encoded-items");
        mail.setClaimed(true);
        stored(mail);
        service.purgeExpiredMailAt(NOW);
        verify(operator).delById("claimed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[\"console:give %player% diamond\"]", "broken-json", "null"})
    void neverParsesDispatchesOrDeletesPendingCommands(String commands) {
        MailData mail = oldMail("commands");
        mail.setCommands(commands);
        stored(mail);
        service.purgeExpiredMailAt(NOW);
        verify(operator, never()).delById(any());
        verifyNoInteractions(logger);
    }

    @Test
    void recordedCommandsMayExpire() {
        MailData mail = oldMail("executed");
        mail.setCommands("[\"console:give %player% diamond\"]");
        mail.setCommandsExecuted(true);
        stored(mail);
        service.purgeExpiredMailAt(NOW);
        verify(operator).delById("executed");
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "true,false,false", "false,true,false", "true,true,true"})
    void bothKindsOfAttachmentMustBeSettled(boolean claimed, boolean executed, boolean expired) {
        MailData mail = oldMail("mixed");
        mail.setItems("items");
        mail.setCommands("[\"command\"]");
        mail.setClaimed(claimed);
        mail.setCommandsExecuted(executed);
        stored(mail);
        service.purgeExpiredMailAt(NOW);
        verify(operator, times(expired ? 1 : 0)).delById("mixed");
    }

    @Test
    void emptyAttachmentRepresentationsDoNotKeepTextMailForever() {
        MailData mail = oldMail("empty");
        mail.setItems("");
        mail.setCommands("[]");
        stored(mail);
        service.purgeExpiredMailAt(NOW);
        verify(operator).delById("empty");
    }

    @Test
    void reloadChangesAreReadOnEveryPass() {
        MailData mail = oldMail("reload");
        stored(mail);
        config.setExpiryRetentionDays(0);
        service.purgeExpiredMailAt(NOW);
        config.setExpiryRetentionDays(32);
        service.purgeExpiredMailAt(NOW);
        verify(operator, never()).delById(any());
        config.setExpiryRetentionDays(30);
        service.purgeExpiredMailAt(NOW);
        verify(operator).delById("reload");
        config.setExpiryRetentionDays(0);
        service.purgeExpiredMailAt(NOW);
        verify(operator, times(2)).getAll();
    }

    @Test
    void aFailedDeleteIsLoggedAndDoesNotStopLaterMailsOrTheNextPass() {
        stored(oldMail("failed"), oldMail("next"));
        doThrow(new DataAccessException("database offline")).doNothing().when(operator).delById("failed");
        assertThatCode(() -> service.purgeExpiredMailAt(NOW)).doesNotThrowAnyException();
        verify(logger).warn("log_mail_expiry_delete_failed failed database offline");
        verify(operator).delById("next");
        service.purgeExpiredMailAt(NOW);
        verify(operator, times(2)).delById("failed");
        verify(operator, times(2)).delById("next");
    }

    @Test
    void anyRuntimeFailureOfOneDeleteIsLoggedAndDoesNotStopLaterMails() {
        // UltiKits/UltiMail#69: a store that is closing at unload throws IllegalStateException,
        // not DataAccessException; the documented "a failed deletion does not stop the remaining
        // rows" must still hold.
        stored(oldMail("closing"), oldMail("next"));
        doThrow(new IllegalStateException("store closed")).when(operator).delById("closing");
        assertThatCode(() -> service.purgeExpiredMailAt(NOW)).doesNotThrowAnyException();
        verify(logger).warn("log_mail_expiry_delete_failed closing store closed");
        verify(operator).delById("next");
    }

    @Test
    void aFailedScanIsLoggedAndRetriedNextPass() {
        when(operator.getAll()).thenThrow(new DataAccessException("scan offline"))
                .thenReturn(Collections.singletonList(oldMail("retry")));
        assertThatCode(() -> service.purgeExpiredMailAt(NOW)).doesNotThrowAnyException();
        verify(logger).warn("log_mail_expiry_scan_failed {MAIL} scan offline");
        service.purgeExpiredMailAt(NOW);
        verify(operator).delById("retry");
    }

    @Test
    void emptyStorageIsHarmless() {
        stored();
        service.purgeExpiredMailAt(NOW);
        verify(operator).getAll();
        verify(operator, never()).delById(any());
    }

    @Test
    void frameworkCanConstructManagedServiceWithoutAClockBean() {
        com.ultikits.ultitools.context.SimpleContainer container =
                new com.ultikits.ultitools.context.SimpleContainer();
        try {
            container.registerType(UltiToolsPlugin.class, plugin);
            container.registerType(MailConfig.class, config);
            container.registerBean(MailExpiryService.class);
            assertThat(container.getBean(MailExpiryService.class)).isNotNull();
        } finally {
            container.close();
        }
    }

    @Test
    void managedTaskIsAsyncWithOneMinuteDelayAndPeriod() throws Exception {
        assertThat(MailExpiryService.class.isAnnotationPresent(Service.class)).isTrue();
        Scheduled scheduled = MailExpiryService.class.getMethod("purgeExpiredMail").getAnnotation(Scheduled.class);
        assertThat(scheduled.async()).isTrue();
        assertThat(scheduled.delay()).isEqualTo(1200);
        assertThat(scheduled.period()).isEqualTo(1200);
    }
}
