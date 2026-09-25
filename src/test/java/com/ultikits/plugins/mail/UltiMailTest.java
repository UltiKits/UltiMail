package com.ultikits.plugins.mail;

import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.listener.AttachmentGUIListener;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UltiMail plugin main class.
 * <p>
 * Tests the enable hook, the lifecycle template-method contract and annotation configuration.
 */
@DisplayName("UltiMail 测试")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@SuppressWarnings("PMD.AvoidAccessibilityAlteration")
class UltiMailTest {

    @Nested
    @DisplayName("registerSelf 测试")
    class RegisterSelfTests {

        @Test
        @DisplayName("registerSelf 应该返回 true")
        void shouldReturnTrue() {
            UltiMail plugin = mock(UltiMail.class);
            PluginLogger logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
            when(plugin.registerSelf()).thenCallRealMethod();

            boolean result = plugin.registerSelf();

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("registerSelf 应该记录启用消息")
        void shouldLogEnableMessage() {
            UltiMail plugin = mock(UltiMail.class);
            PluginLogger logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
            when(plugin.registerSelf()).thenCallRealMethod();

            plugin.registerSelf();

            verify(logger).info(anyString());
        }
    }

    @Nested
    @DisplayName("Lifecycle template methods (UltiKits/UltiMail#20)")
    class LifecycleTemplateMethodTests {

        /**
         * UltiTools 6.3.0 makes {@code unregisterSelf()} and {@code reloadSelf()} final
         * template methods that always run the framework's own steps (config reload, language
         * refresh, command and listener unregistration) before or after the module hook. This
         * module's former overrides only logged a line and never called {@code super}, so a
         * reload re-read nothing (UltiKits/UltiMail#20). They are deleted outright; this test
         * pins that neither is declared again.
         */
        @Test
        @DisplayName("UltiMail declares neither framework template method")
        void declaresNeitherTemplateMethod() {
            List<String> declared = new ArrayList<>();
            for (Method method : UltiMail.class.getDeclaredMethods()) {
                declared.add(method.getName());
            }

            assertThat(declared).doesNotContain("unregisterSelf", "reloadSelf");
        }

        /**
         * The unload hook point. {@code unregisterSelf()} is {@code final} in UltiTools 6.3.0 and
         * always runs the framework's own command and listener unregistration, so a module with
         * unload work of its own overrides {@code onUnregister()} -- which that template method
         * calls BEFORE the framework tears the module's listeners down, so the module's own beans
         * are still alive while it runs.
         */
        @Test
        @DisplayName("UltiMail declares the unload hook, not the final template method")
        void declaresTheUnloadHookRatherThanTheTemplateMethod() throws Exception {
            Method hook = UltiMail.class.getDeclaredMethod("onUnregister");

            assertThat(hook).isNotNull();
            assertThat(java.lang.reflect.Modifier.isFinal(
                    com.ultikits.ultitools.abstracts.UltiToolsPlugin.class
                        .getMethod("unregisterSelf").getModifiers()))
                .as("the framework method this hook exists instead of must really be final, which "
                    + "is why overriding it is not an option here")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("卸载钩子测试 (UltiKits/UltiMail#27)")
    class UnloadHookTests {

        /**
         * The hook's whole job is to reach the module's own attachment-selector listener while it
         * is still alive and have it hand back every item an open selector is holding -- see
         * {@code AttachmentGUIListenerTest.ModuleUnloadTests} for the behaviour itself, measured
         * against real inventories. What is pinned here is the delegation: the listener is a
         * container bean (it carries {@code @EventListener}, which
         * {@code ComponentScanner#isComponent} treats as a component), so the hook resolves it
         * from this module's own container.
         */
        @Test
        @DisplayName("onUnregister 应经由模块容器取到监听器并要求它归还物品")
        void returnsOpenSelectorsThroughTheListenerBean() {
            UltiMail plugin = mock(UltiMail.class);
            SimpleContainer container = mock(SimpleContainer.class);
            AttachmentGUIListener listener = mock(AttachmentGUIListener.class);
            when(plugin.getContext()).thenReturn(container);
            when(container.getBean(AttachmentGUIListener.class)).thenReturn(listener);
            doCallRealMethod().when(plugin).onUnregister();

            plugin.onUnregister();

            verify(listener).returnEveryOpenSelector();
        }

        /**
         * {@code SimpleContainer#getBean(Class)} returns {@code null} on a total miss rather than
         * throwing, and {@code getContext()} is {@code null} for an instance that never went
         * through {@code PluginManager#register(...)}. Neither may turn an unload into a
         * NullPointerException: the framework surfaces a throwing hook to its caller, so throwing
         * here would make an unrelated failure look like this module's.
         */
        @Test
        @DisplayName("没有容器或没有该监听器 bean 时 onUnregister 不得抛异常")
        void survivesAMissingContainerAndAMissingBean() {
            UltiMail withoutContainer = mock(UltiMail.class);
            when(withoutContainer.getContext()).thenReturn(null);
            doCallRealMethod().when(withoutContainer).onUnregister();

            UltiMail withoutBean = mock(UltiMail.class);
            SimpleContainer container = mock(SimpleContainer.class);
            when(withoutBean.getContext()).thenReturn(container);
            when(container.getBean(AttachmentGUIListener.class)).thenReturn(null);
            doCallRealMethod().when(withoutBean).onUnregister();

            assertThatCode(withoutContainer::onUnregister).doesNotThrowAnyException();
            assertThatCode(withoutBean::onUnregister).doesNotThrowAnyException();
        }
    }

    /**
     * UltiKits/UltiMail#23. {@code RemovedConfigKeysTest} guards the check's predicate; these tests
     * guard its WIRING, which is a separate claim: with a call site deleted the predicate tests stay
     * green, and a server with leftover keys prints nothing, exactly like a server without them.
     * Both entry points are covered -- module enable and a reload of this module -- because a guard
     * on one would leave the other free to lose its call silently.
     * <p>
     * The operator's file is reached through {@code operatorConfigFile()}, a package-private seam:
     * the framework's {@code getConfigFile} is {@code protected final}, so this package can neither
     * call nor stub it, and a mocked plugin returns {@code null} from it.
     */
    @Nested
    @DisplayName("the removed-key check is actually called (UltiKits/UltiMail#23)")
    class RemovedKeyCheckWiring {

        private static final String FILE_WITH_THE_REMOVED_KEYS =
                "mail-expire-days: 30\nmessages:\n  new-mail: 'x'\n  mail-sent: 'x'\n"
                + "  mail-received: 'ok'\n";

        private static final String FILE_WITHOUT_THE_REMOVED_KEYS =
                "max-items: 27\nmessages:\n  mail-received: 'ok'\n";

        private PluginLogger logger;

        private UltiMail pluginReading(File dir, String body) throws IOException {
            File file = new File(dir, "mail.yml");
            Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));

            UltiMail plugin = mock(UltiMail.class);
            logger = mock(PluginLogger.class);
            lenient().when(plugin.getLogger()).thenReturn(logger);
            // The warnings come from the language file; answered from the real en catalogue.
            lenient().when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.mail.i18n.CatalogueText.answer("en"));
            when(plugin.operatorConfigFile()).thenReturn(file);
            return plugin;
        }

        private List<String> warnings() {
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(logger, atLeast(0)).warn(captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("POSITIVE CONTROL: enabling the module warns about each leftover key")
        void registerSelfWarns(@TempDir File dir) throws IOException {
            UltiMail plugin = pluginReading(dir, FILE_WITH_THE_REMOVED_KEYS);
            when(plugin.registerSelf()).thenCallRealMethod();

            assertThat(plugin.registerSelf()).isTrue();

            assertThat(warnings()).hasSize(3);
            assertThat(String.join("\n", warnings()))
                    .contains("mail-expire-days", "messages.new-mail", "messages.mail-sent");
        }

        @Test
        @DisplayName("POSITIVE CONTROL: a reload of this module warns about each leftover key")
        void onReloadWarns(@TempDir File dir) throws IOException {
            UltiMail plugin = pluginReading(dir, FILE_WITH_THE_REMOVED_KEYS);
            doCallRealMethod().when(plugin).onReload();

            plugin.onReload();

            assertThat(warnings()).hasSize(3);
            assertThat(String.join("\n", warnings()))
                    .contains("mail-expire-days", "messages.new-mail", "messages.mail-sent");
        }

        @Test
        @DisplayName("the check reads the same file MailConfig declares, from one source")
        void readsTheFileMailConfigDeclares() {
            // Every other test here stubs operatorConfigFile(), so if the path the check resolves
            // ever drifted from the file MailConfig binds, the production check would read a file
            // that does not exist, return silently, and look exactly like a server with no leftover
            // key. The path the check uses must equal both places MailConfig names its file: the
            // @ConfigEntity value and the constructor argument.
            UltiMail plugin = mock(UltiMail.class);
            when(plugin.operatorConfigPath()).thenCallRealMethod();

            String declared = MailConfig.class.getAnnotation(ConfigEntity.class).value();

            assertThat(declared).isEqualTo("config/mail.yml");
            assertThat(new MailConfig().getConfigFilePath()).isEqualTo(declared);
            assertThat(plugin.operatorConfigPath()).isEqualTo(declared);
        }

        @Test
        @DisplayName("a failure inside the check never costs the module its enable or its reload")
        void aFailingCheckNeverFailsEnableOrReload() {
            // The check is advisory; an exception escaping it from registerSelf() would cost the
            // server its whole mail module. Simulated with the file lookup itself failing.
            UltiMail plugin = mock(UltiMail.class);
            logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.mail.i18n.CatalogueText.answer("en"));
            when(plugin.operatorConfigFile())
                    .thenThrow(new UncheckedIOException(new IOException("disk unavailable")));
            when(plugin.registerSelf()).thenCallRealMethod();
            doCallRealMethod().when(plugin).onReload();

            assertThat(plugin.registerSelf()).isTrue();
            assertThatCode(plugin::onReload).doesNotThrowAnyException();

            ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
            verify(logger, times(2)).warn(any(Throwable.class), messages.capture());
            assertThat(messages.getAllValues())
                    .allSatisfy(m -> assertThat(m).contains("removed").contains("mail.yml"));
        }

        @Test
        @DisplayName("neither entry point warns when the file holds no removed key")
        void neitherWarnsOnACleanFile(@TempDir File dir) throws IOException {
            // Paired with the two controls above: same entry points, same shape of file, the three
            // keys taken out and nothing else changed.
            UltiMail onEnable = pluginReading(dir, FILE_WITHOUT_THE_REMOVED_KEYS);
            when(onEnable.registerSelf()).thenCallRealMethod();
            assertThat(onEnable.registerSelf()).isTrue();
            assertThat(warnings()).isEmpty();

            UltiMail onReload = pluginReading(dir, FILE_WITHOUT_THE_REMOVED_KEYS);
            doCallRealMethod().when(onReload).onReload();
            onReload.onReload();
            assertThat(warnings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("注解配置测试")
    class AnnotationConfigTests {

        @Test
        @DisplayName("类应该有 @UltiToolsModule 注解")
        void shouldHaveUltiToolsModuleAnnotation() {
            boolean hasAnnotation = UltiMail.class.isAnnotationPresent(
                com.ultikits.ultitools.annotations.UltiToolsModule.class
            );

            assertThat(hasAnnotation).isTrue();
        }

        @Test
        @DisplayName("@UltiToolsModule 应该扫描正确的包")
        void shouldScanCorrectPackages() {
            com.ultikits.ultitools.annotations.UltiToolsModule annotation =
                UltiMail.class.getAnnotation(
                    com.ultikits.ultitools.annotations.UltiToolsModule.class
                );

            String[] packages = annotation.scanBasePackages();
            assertThat(packages).contains("com.ultikits.plugins.mail");
        }
    }

    @Nested
    @DisplayName("继承关系测试")
    class InheritanceTests {

        @Test
        @DisplayName("应该继承 UltiToolsPlugin")
        void shouldExtendUltiToolsPlugin() {
            assertThat(com.ultikits.ultitools.abstracts.UltiToolsPlugin.class)
                .isAssignableFrom(UltiMail.class);
        }
    }
}
