package com.ultikits.plugins.mail;

import com.ultikits.plugins.mail.listener.AttachmentGUIListener;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
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
         * Gate 1 MJ-02's hook point. {@code unregisterSelf()} is {@code final} in UltiTools 6.3.0
         * and always runs the framework's own command and listener unregistration, so a module
         * with unload work of its own overrides {@code onUnregister()} -- which that template
         * method calls BEFORE the framework tears the module's listeners down, so the module's own
         * beans are still alive while it runs.
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
