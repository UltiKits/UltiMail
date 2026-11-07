package com.ultikits.plugins.mail;

import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.manager.ConfigManager;
import net.bytebuddy.jar.asm.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** A class-shape/scanning test, not a substitute for the real-server matrix. */
class OldInventoryViewScanTest {
    @TempDir Path folder;

    @Test
    void frameworkScansAndBuildsBeansWithClassShapedInventoryViewBeforeRefusal() throws Exception {
        Path classes = java.nio.file.Paths.get("target/classes").toAbsolutePath();
        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(classes.resolve("com/ultikits/plugins/mail"))) {
            classFiles = files.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
        }
        assertThat(classFiles).hasSizeGreaterThan(20);
        // Sweep the compiled module, not just the previously reported source location.
        List<String> viewCalls = new ArrayList<>();
        for (Path file : classFiles) {
            new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                            if (owner.equals("org/bukkit/inventory/InventoryView")) {
                                viewCalls.add(classes.relativize(file) + "#" + name + ":" + method);
                            }
                        }
                    };
                }
            }, 0);
        }
        assertThat(viewCalls).containsExactly("com/ultikits/plugins/mail/listener/AttachmentGUIListener.class#onInventoryDrag:getTopInventory");

        try (OldApiLoader loader = new OldApiLoader(classes.toUri().toURL());
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            Class<?> view = loader.loadClass("org.bukkit.inventory.InventoryView");
            assertThat(view.isInterface()).isFalse();
            assertThat(view.getClassLoader()).isSameAs(loader);
            for (Path file : classFiles) {
                String name = classes.relativize(file).toString().replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", "");
                Class<?> loaded = Class.forName(name, true, loader);
                assertThat(loaded.getClassLoader()).isSameAs(loader);
            }
            // Positive control: defining/loading an invokeinterface site succeeds; executing it
            // against this class-shaped API must fail. Otherwise the fixture proves nothing.
            Class<?> probe = loader.defineProbe();
            assertThatThrownBy(() -> probe.getMethod("call", view).invoke(null, new Object[] {null}))
                    .hasCauseInstanceOf(IncompatibleClassChangeError.class);

            org.bukkit.plugin.PluginManager plugins = mock(org.bukkit.plugin.PluginManager.class);
            Plugin core = mock(Plugin.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            when(plugins.getPlugin("UltiTools")).thenReturn(core);
            UltiMail module = mock(UltiMail.class, CALLS_REAL_METHODS);
            PluginLogger logger = mock(PluginLogger.class);
            doReturn(logger).when(module).getLogger();
            doReturn("en").when(module).getLanguageCode();
            doAnswer(com.ultikits.plugins.mail.i18n.CatalogueText.answer("en")).when(module).i18n(anyString());
            doReturn(mock(DataOperator.class)).when(module).getDataOperator(any());
            Field path = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
            path.setAccessible(true);
            path.set(module, folder.toString());
            com.ultikits.ultitools.abstracts.AbstractConfigEntity config =
                    (com.ultikits.ultitools.abstracts.AbstractConfigEntity) loader.loadClass(MailConfig.class.getName())
                            .getDeclaredConstructor().newInstance();
            // Real config-load path, before activation, as in framework 6.3.0.
            new ConfigManager().register(module, config);
            try (SimpleContainerHandle handle = new SimpleContainerHandle()) {
                SimpleContainer container = handle.container;
                container.setClassLoader(loader);
                container.registerType(UltiToolsPlugin.class, module);
                container.registerType(Plugin.class, core);
                container.registerSingleton("mailConfig", config);
                container.scanComponents("com.ultikits.plugins.mail");
                assertThat(container.getBeanDefinitionNames()).contains("attachmentGUIListener", "mailNotifyListener",
                        "mailService", "ultiMailGameMailService", "mailCommand", "sendMailCommand", "recallCommand");
                container.refresh();
                assertThat(container.getBean(loader.loadClass("com.ultikits.plugins.mail.listener.AttachmentGUIListener"))).isNotNull();
                Object service = container.getBean(loader.loadClass("com.ultikits.plugins.mail.service.MailService"));
                assertThat(service).isNotNull();
                verify(module).getDataOperator(any()); // real @PostConstruct ran
                doReturn("1.20.6").when(module).runningMinecraftVersion();
                doReturn("6.3.0").when(module).runningFrameworkVersion();
                assertThat(module.registerSelf()).isFalse();
                verify(plugins, never()).registerEvents(any(), any());
                bukkit.verify(Bukkit::getScheduler, never());
            }
        }
    }

    private static class SimpleContainerHandle implements AutoCloseable {
        private final SimpleContainer container = new SimpleContainer();
        @Override public void close() { container.close(); }
    }

    private static class OldApiLoader extends URLClassLoader {
        OldApiLoader(URL classes) { super(new URL[] {classes}, OldInventoryViewScanTest.class.getClassLoader()); }
        @Override public URL getResource(String name) {
            // Surefire puts test-classes first; the real module scanner must see product classes.
            return name.startsWith("com/ultikits/plugins/mail") ? findResource(name) : super.getResource(name);
        }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null && name.equals("org.bukkit.inventory.InventoryView")) {
                ClassWriter writer = new ClassWriter(0);
                writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                        "org/bukkit/inventory/InventoryView", null, "java/lang/Object", null);
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getTopInventory",
                        "()Lorg/bukkit/inventory/Inventory;", null, null).visitEnd();
                writer.visitEnd();
                byte[] bytes = writer.toByteArray();
                loaded = defineClass(name, bytes, 0, bytes.length);
            } else if (loaded == null && name.startsWith("com.ultikits.plugins.mail.")) {
                loaded = findClass(name);
            }
            if (loaded == null) { loaded = super.loadClass(name, false); }
            if (resolve) { resolveClass(loaded); }
            return loaded;
        }
        Class<?> defineProbe() {
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "OldViewProbe", null, "java/lang/Object", null);
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call",
                    "(Lorg/bukkit/inventory/InventoryView;)V", null, null);
            method.visitCode();
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/bukkit/inventory/InventoryView", "getTopInventory",
                    "()Lorg/bukkit/inventory/Inventory;", true);
            method.visitInsn(Opcodes.POP);
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(1, 1);
            method.visitEnd();
            writer.visitEnd();
            byte[] bytes = writer.toByteArray();
            return defineClass("OldViewProbe", bytes, 0, bytes.length);
        }
    }
}
