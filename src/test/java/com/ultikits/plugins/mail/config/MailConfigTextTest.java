package com.ultikits.plugins.mail.config;

import com.ultikits.plugins.mail.UltiMail;
import com.ultikits.plugins.mail.commands.RecallCommand;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.plugins.mail.i18n.CatalogueText;
import com.ultikits.plugins.mail.service.MailService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.interfaces.ConfigChangeListener;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code config/mail.yml} holds the new-mail notice, the server name and the four recall texts in the
 * server's language, and the module shows exactly what the file holds (maintainer decision 2026-09-25;
 * UltiKits/UltiMail#21, UltiKits/UltiMail#22). A value that is still built-in text — any language's text
 * from this jar, or the default an earlier version shipped — follows {@code language} at enable and on
 * reload, in both directions; anything else is the operator's and is kept byte for byte. Every case runs
 * the framework's real {@code AbstractConfigEntity#init} on a temporary folder, the module's real
 * {@code registerSelf()} and {@code onReload()}, and answers {@code i18n} from the module's real catalogues.
 */
@DisplayName("mail.yml holds its text settings in the server's language (UltiKits/UltiMail#21, #22)")
class MailConfigTextTest {

    /** One text setting: its field, its path in mail.yml, its catalogue key, its shipped default. */
    private static final class Setting {
        final String field;
        final String path;
        final String key;
        final String shipped;

        Setting(String field, String path, String key, String shipped) {
            this.field = field;
            this.path = path;
            this.key = key;
            this.shipped = shipped;
        }

        String text(String code) {
            return CatalogueText.text(code, key);
        }

        String getter() {
            return "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        }
    }

    /**
     * The 6 settings, with the one default each shipped in every earlier version. The four whose
     * catalogue key already existed come first, so a check that fails reports the file's value before
     * a key this change adds.
     */
    private static final List<Setting> SETTINGS = Arrays.asList(
            new Setting("recallSubject", "recall.subject", "recall_subject", "[{SERVER}] 回归召唤"),
            new Setting("recallContent", "recall.content", "recall_content",
                    "亲爱的玩家，{SERVER}想念你了！\n\n快回来看看吧，我们期待与你重逢！\n\n发送者: {SENDER}"),
            new Setting("recallEmailSubject", "email.recall-subject", "recall_email_subject", "[{SERVER}] 我们想念你！"),
            new Setting("recallEmailContent", "email.recall-content", "recall_email_content",
                    "亲爱的 {PLAYER}，\n\n{SERVER} 服务器想念你了！快回来看看吧，我们期待与你重逢！\n\n发送者: {SENDER}"),
            new Setting("mailReceivedMessage", "messages.mail-received", "mail_received",
                    "&e[邮件] &f你收到了来自 &a{SENDER} &f的新邮件！"),
            new Setting("serverName", "recall.server-name", "recall_server_name", "Minecraft服务器"));

    /** The setting stored at {@code path}. */
    private static Setting setting(String path) {
        for (Setting s : SETTINGS) {
            if (s.path.equals(path)) {
                return s;
            }
        }
        throw new IllegalArgumentException(path);
    }

    /** The fields that carried {@code @NotEmpty} at origin/master: the 6 above plus the two SMTP addresses. */
    private static final Set<String> NOT_EMPTY_AT_MASTER = new TreeSet<>();

    static {
        for (Setting s : SETTINGS) {
            NOT_EMPTY_AT_MASTER.add(s.field);
        }
        NOT_EMPTY_AT_MASTER.addAll(Arrays.asList("smtpHost", "smtpFromEmail"));
    }

    private static final String[] LANGUAGES = {"en", "zh"};

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    @TempDir
    Path tempDir;

    private final String[] language = {"en"};

    private final PluginLogger logger = mock(PluginLogger.class);

    /** Catalogue texts an operator changed in the extracted language file on disk, answered by i18n first. */
    private final Map<String, String> diskOverrides = new LinkedHashMap<>();

    /** The configuration the module double returns from {@code getConfig(MailConfig.class)}. */
    private MailConfig current;

    private UltiMail plugin;

    @BeforeEach
    void setUp() {
        plugin = moduleDouble();
    }

    @AfterEach
    void tearDown() {
        current = null;
    }

    @Test
    @DisplayName("the catalogues give each setting English text under en and exactly its shipped default under zh")
    void catalogueTexts() {
        for (Setting s : SETTINGS) {
            assertThat(s.text("zh")).as(s.field).isEqualTo(s.shipped);
            assertThat(s.text("en")).as(s.field).doesNotMatch("(?s).*" + CJK.pattern() + ".*");
        }
    }

    @Test
    @DisplayName("fresh start under en: mail.yml holds every setting's English text, and each getter returns the file's value")
    void freshStartEnglish() throws Exception {
        language[0] = "en";
        MailConfig config = spy(load());

        start(config);

        YamlConfiguration disk = onDisk();
        for (Setting s : SETTINGS) {
            assertThat(disk.getString(s.path)).as(s.path).isEqualTo(s.text("en"));
            assertThat(get(config, s)).as(s.field).isEqualTo(disk.getString(s.path));
        }
        verify(config, times(1)).save();
    }

    @Test
    @DisplayName("fresh start under zh: mail.yml holds every setting's Chinese text, which is its shipped default, and the module writes nothing")
    void freshStartChinese() throws Exception {
        language[0] = "zh";
        MailConfig config = spy(load());
        byte[] afterFramework = bytes();

        start(config);

        YamlConfiguration disk = onDisk();
        for (Setting s : SETTINGS) {
            assertThat(disk.getString(s.path)).as(s.path).isEqualTo(s.shipped);
            assertThat(get(config, s)).as(s.field).isEqualTo(s.shipped);
        }
        verify(config, never()).save();
        assertThat(bytes()).isEqualTo(afterFramework);
    }

    @Test
    @DisplayName("every built-in text in the file (shipped default, jar en text, jar zh text) is replaced with the current language's text and saved, under en and zh")
    void everyTrackedValueFollowsTheLanguage() throws Exception {
        for (String code : LANGUAGES) {
            for (String member : new String[] {"shipped", "en", "zh"}) {
                language[0] = code;
                Map<String, String> values = new LinkedHashMap<>();
                for (Setting s : SETTINGS) {
                    values.put(s.path, "shipped".equals(member) ? s.shipped : s.text(member));
                }
                write(values);
                MailConfig config = spy(load());

                start(config);

                YamlConfiguration disk = onDisk();
                for (Setting s : SETTINGS) {
                    String what = "language " + code + ", file held the " + member + " text of " + s.path;
                    assertThat(disk.getString(s.path)).as(what).isEqualTo(s.text(code));
                    assertThat(get(config, s)).as(what).isEqualTo(s.text(code));
                }
                boolean alreadyCurrent = member.equals(code) || ("shipped".equals(member) && "zh".equals(code));
                verify(config, times(alreadyCurrent ? 0 : 1)).save();
            }
        }
    }

    @Test
    @DisplayName("an upgraded file holding the shipped defaults reads exactly the English text under en (pinned, not read from the catalogue)")
    void upgradedFileReadsExactEnglish() throws Exception {
        language[0] = "en";
        Map<String, String> values = new LinkedHashMap<>();
        for (Setting s : SETTINGS) {
            values.put(s.path, s.shipped);
        }
        write(values);
        MailConfig config = spy(load());

        start(config);

        YamlConfiguration disk = onDisk();
        assertThat(disk.getString("messages.mail-received")).isEqualTo("&e[Mail] &fYou received a new mail from &a{SENDER}&f!");
        assertThat(disk.getString("recall.server-name")).isEqualTo("Minecraft Server");
        assertThat(disk.getString("recall.subject")).isEqualTo("[{SERVER}] Come back to us");
        assertThat(disk.getString("email.recall-subject")).isEqualTo("[{SERVER}] We miss you!");
        assertThat(config.getMailReceivedMessage()).isEqualTo("&e[Mail] &fYou received a new mail from &a{SENDER}&f!");
        assertThat(config.getServerName()).isEqualTo("Minecraft Server");
        verify(config, times(1)).save();
    }

    @Test
    @DisplayName("a customised value, or built-in text changed by one character, is kept byte for byte under both languages and the file is not rewritten")
    void customisedValuesAreKept() throws Exception {
        for (String code : LANGUAGES) {
            for (String variant : new String[] {"shipped!", "en!", "zh!", "own"}) {
                language[0] = code;
                Map<String, String> values = new LinkedHashMap<>();
                for (Setting s : SETTINGS) {
                    String v;
                    if ("shipped!".equals(variant)) {
                        v = s.shipped + "!";
                    } else if ("en!".equals(variant)) {
                        v = s.text("en") + " ";
                    } else if ("zh!".equals(variant)) {
                        v = "!" + s.text("zh");
                    } else {
                        v = "&dOperator text for " + s.field;
                    }
                    values.put(s.path, v);
                }
                write(values);
                MailConfig config = spy(load());
                byte[] before = bytes();

                start(config);

                assertThat(bytes()).as(code + " " + variant).isEqualTo(before);
                for (Setting s : SETTINGS) {
                    assertThat(get(config, s)).as(code + " " + variant + " " + s.field).isEqualTo(values.get(s.path));
                }
                verify(config, never()).save();
            }
        }
    }

    @Test
    @DisplayName("a second enable with the same language writes nothing")
    void secondEnableWritesNothing() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            Map<String, String> values = new LinkedHashMap<>();
            for (Setting s : SETTINGS) {
                values.put(s.path, s.shipped);
            }
            write(values);
            start(load());
            byte[] afterFirst = bytes();

            MailConfig second = spy(load());
            start(second);

            assertThat(bytes()).as(code).isEqualTo(afterFirst);
            verify(second, never()).save();
        }
    }

    @Test
    @DisplayName("onReload() after a language switch rewrites every setting in the new language, in both directions")
    void reloadFollowsALanguageSwitchBothWays() throws Exception {
        for (String[] direction : new String[][] {{"en", "zh"}, {"zh", "en"}}) {
            language[0] = direction[0];
            Files.deleteIfExists(file().toPath());
            MailConfig config = load();
            start(config);
            for (Setting s : SETTINGS) {
                assertThat(onDisk().getString(s.path)).as("before the switch, " + s.path).isEqualTo(s.text(direction[0]));
            }

            language[0] = direction[1];
            config.init(plugin);
            reload();

            YamlConfiguration disk = onDisk();
            for (Setting s : SETTINGS) {
                String what = direction[0] + " -> " + direction[1] + ": " + s.path;
                assertThat(disk.getString(s.path)).as(what).isEqualTo(s.text(direction[1]));
                assertThat(get(config, s)).as(what).isEqualTo(s.text(direction[1]));
            }
        }
    }

    @Test
    @DisplayName("no configuration change listener rewrites the text (the framework fires them before it reloads the language)")
    void changeListenersDoNotMaterialize() throws Exception {
        language[0] = "en";
        MailConfig config = load();
        start(config);
        byte[] before = bytes();

        language[0] = "zh";
        // This module registers no change listener, and the framework registers none for it, so nothing
        // can write the file before the framework rebuilds the language; pinned so that adding one is seen.
        assertThat(config.getChangeListeners()).isEmpty();
        for (ConfigChangeListener listener : new ArrayList<>(config.getChangeListeners())) {
            listener.onConfigReload(config);
        }

        assertThat(bytes()).isEqualTo(before);
        assertThat(config.getRecallSubject()).isEqualTo(setting("recall.subject").text("en"));
    }

    @Test
    @DisplayName("an operator-edited language file on disk does not widen what counts as built-in text")
    void diskCatalogueDoesNotWidenTheTrackedSet() throws Exception {
        Path lang = Files.createDirectories(tempDir.resolve("lang"));
        StringBuilder yml = new StringBuilder();
        for (Setting s : SETTINGS) {
            yml.append(s.key).append(": \"Edited ").append(s.key).append("\"\n");
            // i18n answers the edited text too, so a tracked set built from i18n (or the disk file) would
            // contain the file's value and rewrite it; only the jar's own catalogue keeps this test green.
            diskOverrides.put(s.key, "Edited " + s.key);
        }
        for (String code : LANGUAGES) {
            Files.write(lang.resolve(code + ".yml"), yml.toString().getBytes(StandardCharsets.UTF_8));
        }
        for (String code : LANGUAGES) {
            language[0] = code;
            Map<String, String> values = new LinkedHashMap<>();
            for (Setting s : SETTINGS) {
                values.put(s.path, "Edited " + s.key);
            }
            write(values);
            MailConfig config = spy(load());

            start(config);

            for (Setting s : SETTINGS) {
                assertThat(onDisk().getString(s.path)).as(code + " " + s.path).isEqualTo(values.get(s.path));
            }
            verify(config, never()).save();
        }
    }

    @Test
    @DisplayName("an operator's edit of the extracted language file is not written into mail.yml, so each value keeps following a language switch")
    void diskCatalogueEditDoesNotReachTheFile() throws Exception {
        for (Setting s : SETTINGS) {
            diskOverrides.put(s.key, "Edited " + s.key);
        }
        language[0] = "en";
        Map<String, String> values = new LinkedHashMap<>();
        for (Setting s : SETTINGS) {
            values.put(s.path, s.shipped);
        }
        write(values);
        MailConfig config = load();
        start(config);

        for (Setting s : SETTINGS) {
            assertThat(onDisk().getString(s.path)).as("en, " + s.path + ": the jar's text, not the disk edit").isEqualTo(s.text("en"));
        }

        language[0] = "zh";
        config.init(plugin);
        reload();

        for (Setting s : SETTINGS) {
            assertThat(onDisk().getString(s.path)).as("after a switch to zh, " + s.path + " follows").isEqualTo(s.text("zh"));
        }
    }

    @Test
    @DisplayName("a file that cannot be saved is reported in the server's language naming the file, and the module still uses the new text")
    void saveFailureIsReported() throws Exception {
        language[0] = "en";
        MailConfig config = spy(load());
        IOException failure = new IOException("read-only");
        doThrow(failure).when(config).save();

        assertThat(start(config)).isTrue();

        String expected = CatalogueText.text("en", "log_config_default_save_failed").replace("{FILE}", "config/mail.yml");
        assertThat(expected).contains("config/mail.yml");
        verify(logger).warn(eq(failure), eq(expected));
        for (Setting s : SETTINGS) {
            assertThat(get(config, s)).as(s.field).isEqualTo(s.text("en"));
        }
    }

    @Test
    @DisplayName("MailService's new-mail notice is rendered from the file's messages.mail-received")
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    void newMailNoticeUsesTheFile() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            Files.deleteIfExists(file().toPath());
            MailConfig config = load();
            start(config);
            String fileText = onDisk().getString("messages.mail-received");
            assertThat(fileText).as(code).isEqualTo(setting("messages.mail-received").text(code));
            Player receiver = mock(Player.class);
            when(receiver.isOnline()).thenReturn(true);
            MailService service = new MailService();
            set(service, "config", config);
            Method notify = MailService.class.getDeclaredMethod("notifyReceiver", String.class, String.class);
            notify.setAccessible(true);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayerExact("Receiver")).thenReturn(receiver);
                notify.invoke(service, "Receiver", "Alice");
            }

            verify(receiver).sendMessage(ChatColor.translateAlternateColorCodes('&', fileText.replace("{SENDER}", "Alice")));
        }
    }

    @Test
    @DisplayName("RecallCommand's in-game recall mail takes its subject, content and sender name from the file, and an operator's own server name is kept on upgrade")
    @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "unchecked"})
    void recallGameMailUsesTheFile() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            Map<String, String> values = new LinkedHashMap<>();
            for (Setting s : SETTINGS) {
                values.put(s.path, s.shipped);
            }
            values.put("recall.server-name", "&bMy Survival");
            write(values);
            MailConfig config = load();
            start(config);
            YamlConfiguration disk = onDisk();
            assertThat(disk.getString("recall.server-name")).as(code).isEqualTo("&bMy Survival");
            assertThat(disk.getString("recall.subject")).as(code).isEqualTo(setting("recall.subject").text(code));

            UltiToolsPlugin module = mock(UltiToolsPlugin.class);
            DataOperator<MailData> mails = mock(DataOperator.class);
            when(module.getDataOperator(MailData.class)).thenReturn(mails);
            RecallCommand command = new RecallCommand();
            set(command, "config", config);
            set(command, "plugin", module);
            Method send = RecallCommand.class.getDeclaredMethod("sendGameMail", String.class, String.class, String.class, String.class);
            send.setAccessible(true);

            send.invoke(command, "uuid-1", "Bob", "Admin", null);

            ArgumentCaptor<MailData> sent = ArgumentCaptor.forClass(MailData.class);
            verify(mails).insert(sent.capture());
            assertThat(sent.getValue().getSubject()).as(code)
                    .isEqualTo(disk.getString("recall.subject").replace("{SERVER}", "&bMy Survival"));
            assertThat(sent.getValue().getContent()).as(code)
                    .isEqualTo(disk.getString("recall.content").replace("{SERVER}", "&bMy Survival").replace("{SENDER}", "Admin"));
            assertThat(sent.getValue().getSenderName()).as(code).isEqualTo("&bMy Survival");
        }
    }

    @Test
    @DisplayName("RecallCommand's recall email reads its subject, content and server name from the file's values")
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    void recallEmailReadsTheFile() throws Exception {
        language[0] = "en";
        Files.deleteIfExists(file().toPath());
        MailConfig loaded = load();
        start(loaded);
        loaded.setEmailEnabled(true);
        MailConfig config = spy(loaded);
        RecallCommand command = new RecallCommand();
        set(command, "config", config);
        set(command, "plugin", mock(UltiToolsPlugin.class));
        Method send = RecallCommand.class.getDeclaredMethod("sendRealEmail", String.class, String.class, String.class, String.class);
        send.setAccessible(true);

        // The test classpath has no javax.mail, so the send stops after the texts are rendered; what it
        // rendered them from is the configuration's getters, which return the file's values.
        Throwable thrown = catchThrowable(() -> send.invoke(command, "bob@example.com", "Bob", "Admin", null));

        assertThat(thrown).isInstanceOf(InvocationTargetException.class);
        verify(config).getRecallEmailSubject();
        verify(config).getRecallEmailContent();
        verify(config, atLeastOnce()).getServerName();
        YamlConfiguration disk = onDisk();
        assertThat(config.getRecallEmailSubject()).isEqualTo(disk.getString("email.recall-subject")).isEqualTo("[{SERVER}] We miss you!");
        assertThat(config.getRecallEmailContent()).isEqualTo(disk.getString("email.recall-content"));
        assertThat(config.getServerName()).isEqualTo(disk.getString("recall.server-name")).isEqualTo("Minecraft Server");
    }

    @Test
    @DisplayName("@NotEmpty is on exactly the fields that carried it at origin/master, and each text field's Java default is its shipped default")
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    void validationAndJavaDefaults() throws Exception {
        Set<String> notEmpty = new TreeSet<>();
        for (Field f : MailConfig.class.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConfigEntry.class) && f.isAnnotationPresent(NotEmpty.class)) {
                notEmpty.add(f.getName());
            }
        }
        assertThat(notEmpty).containsExactlyElementsOf(NOT_EMPTY_AT_MASTER);

        MailConfig fresh = new MailConfig();
        for (Setting s : SETTINGS) {
            Field f = MailConfig.class.getDeclaredField(s.field);
            f.setAccessible(true);
            assertThat(f.get(fresh)).as(s.field).isEqualTo(s.shipped);
            assertThat(f.getAnnotation(ConfigEntry.class).path()).as(s.field).isEqualTo(s.path);
        }
    }

    // ---- harness ----

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static String get(MailConfig config, Setting s) throws Exception {
        return (String) MailConfig.class.getMethod(s.getter()).invoke(config);
    }

    private File file() {
        return new File(tempDir.toFile(), MailConfig.CONFIG_FILE);
    }

    private byte[] bytes() throws IOException {
        return Files.readAllBytes(file().toPath());
    }

    private YamlConfiguration onDisk() {
        return YamlConfiguration.loadConfiguration(file());
    }

    /** Writes a mail.yml holding {@code values} (path to value), as an earlier version or an operator left it. */
    private void write(Map<String, String> values) throws IOException {
        Files.createDirectories(file().getParentFile().toPath());
        YamlConfiguration persisted = new YamlConfiguration();
        for (Map.Entry<String, String> e : values.entrySet()) {
            persisted.set(e.getKey(), e.getValue());
        }
        persisted.save(file());
    }

    /** The framework's own load: {@code init} fills missing keys with the Java defaults, saves, validates. */
    private MailConfig load() throws IOException {
        Files.createDirectories(file().getParentFile().toPath());
        MailConfig config = new MailConfig();
        config.init(plugin);
        return config;
    }

    /** The module's enable path: {@code UltiMail#registerSelf()} with {@code config} as the module's configuration. */
    private boolean start(MailConfig config) {
        current = config;
        return plugin.registerSelf();
    }

    /** The module's {@code onReload()} (protected), as the framework calls it after rebuilding the language. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void reload() throws Exception {
        Method onReload = UltiMail.class.getDeclaredMethod("onReload");
        onReload.setAccessible(true);
        onReload.invoke(plugin);
    }

    /**
     * A module double whose {@code registerSelf()} and {@code onReload()} are the real ones, whose
     * configuration folder is the temporary directory, whose {@code i18n} answers from the module's real
     * catalogue for the language in {@link #language} (read at call time, after {@link #diskOverrides}),
     * and whose {@code getConfig(MailConfig.class)} is {@link #current}.
     */
    private UltiMail moduleDouble() {
        return Mockito.mock(UltiMail.class, this::moduleAnswer);
    }

    private Object moduleAnswer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
        String name = invocation.getMethod().getName();
        switch (name) {
            case "registerSelf":
            case "onReload":
                return invocation.callRealMethod();
            case "getConfigFolder":
                return tempDir.toString();
            case "getConfigFile":
                return new File(tempDir.toFile(), invocation.<String>getArgument(0));
            case "operatorConfigFile":
                return file();
            case "i18n": {
                String key = invocation.getArgument(invocation.getArguments().length - 1);
                return diskOverrides.containsKey(key) ? diskOverrides.get(key) : CatalogueText.answer(language[0]).answer(invocation);
            }
            case "getLanguageCode":
                return language[0];
            case "getLogger":
                return logger;
            case "getConfig":
                return invocation.getArguments().length == 1 && invocation.getArgument(0) == MailConfig.class
                        ? current : Answers.RETURNS_DEFAULTS.answer(invocation);
            default:
                return Answers.RETURNS_DEFAULTS.answer(invocation);
        }
    }
}
