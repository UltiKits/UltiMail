package com.ultikits.plugins.mail;

import com.ultikits.plugins.mail.i18n.CatalogueText;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.manager.PluginManager;
import com.ultikits.ultitools.manager.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StartupRefusalTest {
    @ParameterizedTest
    @CsvSource({"en,UltiMail needs UltiTools 6.4.0", "zh,UltiMail 在低于 Minecraft 1.21"})
    void refusesBeforeEnableWork(String language, String text) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            org.bukkit.plugin.PluginManager bukkitPlugins = mock(org.bukkit.plugin.PluginManager.class);
            Plugin core = mock(Plugin.class);
            bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.20.6-R0.1-SNAPSHOT");
            bukkit.when(Bukkit::getPluginManager).thenReturn(bukkitPlugins);
            when(bukkitPlugins.getPlugin("UltiTools")).thenReturn(core);
            when(core.getDescription()).thenReturn(new PluginDescriptionFile("UltiTools", "6.3.1-SNAPSHOT", "Core"));
            UltiMail module = module(language);
            // Exercise the real version readers as well as registerSelf, not just its predicate.
            when(module.runningMinecraftVersion()).thenCallRealMethod();
            when(module.runningFrameworkVersion()).thenCallRealMethod();

            assertThat(module.registerSelf()).isFalse();

            verify(module.getLogger()).error(contains(text));
            verify(module.getLogger()).error(contains("server: 1.20.6-R0.1-SNAPSHOT, UltiTools: 6.3.1-SNAPSHOT"));
            verify(module.getLogger(), never()).info(anyString());
            verify(module, never()).operatorConfigFile();
            verify(module, never()).getConfig(any(Class.class));
            verify(bukkitPlugins, never()).disablePlugin(any());
        }
    }

    @ParameterizedTest
    @CsvSource({"garbage,6.3.0", "1.20.6,garbage", "1.21.11,garbage", ",6.3.0", "1.20.6,"})
    void unknownVersionsWarnAndContinue(String minecraft, String framework) {
        UltiMail module = module("en");
        when(module.runningMinecraftVersion()).thenReturn(minecraft);
        when(module.runningFrameworkVersion()).thenReturn(framework);
        assertThat(module.registerSelf()).isTrue();
        verify(module.getLogger()).warn("UltiMail could not determine startup compatibility (server: "
                + minecraft + ", UltiTools: " + framework + "). Continuing without the version check.");
        verify(module.getLogger()).info("UltiMail has been enabled!");
        verify(module.getLogger(), never()).error(anyString());
    }

    @Test
    void absentCoreVersionIsUnknown() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            org.bukkit.plugin.PluginManager plugins = mock(org.bukkit.plugin.PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            UltiMail module = module("en");
            when(module.runningFrameworkVersion()).thenCallRealMethod();
            assertThat(module.runningFrameworkVersion()).isNull();
        }
    }

    @Test
    void frameworkRefusalClosesContainerWithoutEnablingOrRegisteringAnything() throws Exception {
        UltiMail module = module("en");
        when(module.runningMinecraftVersion()).thenReturn("1.19.2");
        when(module.runningFrameworkVersion()).thenReturn("6.3.0");
        when(module.getPluginName()).thenReturn("UltiMail");
        SimpleContainer context = mock(SimpleContainer.class);
        when(module.getContext()).thenReturn(context);
        PluginManager manager = new PluginManager();
        TaskManager tasks = mock(TaskManager.class);
        Field field = PluginManager.class.getDeclaredField("taskManager");
        field.setAccessible(true);
        field.set(manager, tasks);
        UltiTools core = mock(UltiTools.class);
        org.bukkit.plugin.PluginManager bukkitPlugins = mock(org.bukkit.plugin.PluginManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<UltiTools> framework = mockStatic(UltiTools.class)) {
            framework.when(UltiTools::getInstance).thenReturn(core);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("startup-refusal-test"));
            bukkit.when(Bukkit::getPluginManager).thenReturn(bukkitPlugins);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            // Real 6.3.0 refusal/activation branch. Module construction and container assembly
            // are covered separately; this is not a full Paper bootstrap.
            Method activation = PluginManager.class.getDeclaredMethod("attemptPluginRegistration", UltiToolsPlugin.class);
            activation.setAccessible(true);
            assertThat(activation.invoke(manager, module)).isEqualTo(false);
            assertThat(manager.getPluginList()).doesNotContain(module);
            verify(context).close();
            verifyNoInteractions(tasks, scheduler, bukkitPlugins);
            verify(core, never()).getCommandManager();
            verify(core, never()).getListenerManager();
            verify(context, never()).getSingletonValues();
        }
    }

    private UltiMail module(String language) {
        UltiMail module = mock(UltiMail.class);
        PluginLogger logger = mock(PluginLogger.class);
        when(module.getLogger()).thenReturn(logger);
        when(module.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
        when(module.registerSelf()).thenCallRealMethod();
        return module;
    }
}
