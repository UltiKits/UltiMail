package com.ultikits.plugins.mail;

import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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
