package com.ultikits.plugins.mail;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import com.ultikits.plugins.mail.utils.MockBukkitHelper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen-guard sentinel for UltiMail's test-time Bukkit server bootstrap.
 * <br>
 * Every assertion here depends on a live server instance, never a bare registry constant —
 * {@code mockbukkit-v1.21} registers its {@code RegistryAccess} mock via
 * {@code java.util.ServiceLoader}, so registry constants resolve merely from the dependency being
 * on the classpath, independent of whether {@link MockBukkit#mock()} was
 * ever called. If this class is ever authored to assert only a bare constant, it stops being able
 * to detect the bootstrap being silently removed.
 * <br>
 * This class deliberately does <b>not</b> call {@link MockBukkit#mock()} itself. Doing so would let
 * the module's shared test-time bootstrap ({@link MockBukkitHelper#bootstrapServer()}) be removed or
 * replaced (e.g. with a bare Mockito {@code Server} mock) without this sentinel ever noticing — it
 * would simply keep standing up its own unrelated live server. Routing through the same shared entry
 * point every other MockBukkit-backed test in this module uses ({@code AttachmentSelectorPageTest},
 * {@code MailboxGUITest}, {@code SentboxGUITest}) is what makes this a guard on the module's actual
 * wiring, not on this file's own private copy of it.
 */
public class UltiMailRegistrySentinelTest {

    @BeforeEach
    void setUp() {
        MockBukkitHelper.bootstrapServer();
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @Test
    void liveServerIsBootstrapped() {
        assertNotNull(Bukkit.getServer(), "live server bootstrap must be present");
    }

    @Test
    void unsafeValuesResolves() {
        assertNotNull(Bukkit.getUnsafe(), "UnsafeValues must resolve on a live server");
    }

    @Test
    void createProfileDoesNotSilentlyReturnNull() {
        Object profile = Bukkit.createProfile(UUID.randomUUID(), "SentinelPlayer");
        assertNotNull(profile, "createProfile must not silently return null");
    }

    @Test
    void itemStackConstructionResolvesRegistry() {
        ItemStack stack = new ItemStack(Material.DIAMOND);
        assertNotNull(stack);
        assertEquals(Material.DIAMOND, stack.getType());
    }
}
