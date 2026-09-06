package com.ultikits.plugins.mail.gui;

import com.ultikits.plugins.mail.utils.MockBukkitHelper;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import mc.obliviate.inventory.InventoryAPI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AttachmentSelectorPage}.
 * <p>
 * Every test in this class constructs the real page, opens it for a bootstrapped MockBukkit
 * player, and drives real {@link InventoryClickEvent}s through
 * {@code Bukkit.getPluginManager().callEvent(...)} -- the same dispatch path a real server
 * uses, reaching the real {@code obliviate-invs} {@code InvListener} (registered here via a
 * real {@link InventoryAPI}) and, through it, this page's own real click handling. Neither
 * MockBukkit nor the framework's own listener performs the server's own post-event item
 * movement, so each test completes that one step itself, exactly as a real server would,
 * conditioned on the event's real (not asserted-in-advance) cancellation state.
 */
@DisplayName("AttachmentSelectorPage 测试")
@ExtendWith(MockitoExtension.class)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AttachmentSelectorPageTest {

    private PlayerMock player;
    private AttachmentSelectorPage page;
    private final AtomicBoolean confirmed = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<ItemStack[]> receivedItems = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkitHelper.bootstrapServer();
        UltiToolsPlugin mockPlugin = TestHelper.mockUltiToolsPlugin();

        // Register the real obliviate-invs InvListener with the mock plugin manager, exactly
        // as the framework does at startup, so every click in this test travels through the
        // actual production dispatch path rather than a re-implementation of its logic.
        PluginMock javaPlugin = MockBukkit.createMockPlugin();
        new InventoryAPI(javaPlugin).init();

        player = server.addPlayer("testplayer");

        page = new AttachmentSelectorPage(player, 27, mockPlugin,
                items -> {
                    confirmed.set(true);
                    receivedItems.set(items);
                },
                () -> cancelled.set(true));
        page.open();
    }

    @AfterEach
    void tearDown() {
        TestHelper.cleanupMocks();
        MockBukkitHelper.safeUnmock();
    }

    /**
     * Builds a real {@link InventoryClickEvent} for the given raw slot inside the page's own
     * (top) inventory, using the player's real, just-opened {@link InventoryView}.
     */
    private InventoryClickEvent topInventoryClickEvent(int rawSlot) {
        return topInventoryClickEvent(rawSlot, ClickType.LEFT, InventoryAction.PLACE_ALL);
    }

    /**
     * Same as {@link #topInventoryClickEvent(int)}, but for a specific {@link ClickType}/
     * {@link InventoryAction} pair -- used to prove that {@link AttachmentSelectorPage#onClick}'s
     * (and the library's toolbar-protection default's) behaviour does not depend on which one was
     * used to trigger the click, per WR-01.
     */
    private InventoryClickEvent topInventoryClickEvent(int rawSlot, ClickType clickType, InventoryAction action) {
        InventoryView view = player.getOpenInventory();
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, clickType, action);
    }

    /**
     * Fires {@code event} through the real Bukkit dispatch, tolerating one specific, pre-existing,
     * out-of-scope defect: the OK/Cancel icon's own click action (registered by the framework's
     * {@code BaseConfirmationPage}, not by this page) calls {@code player.closeInventory()}, which
     * fires a real {@code InventoryCloseEvent} into {@code InvListener.onClose()} ->
     * {@code Gui.onClose()}. The {@code obliviate-invs} 4.3.0 classes shaded into the framework
     * jar were compiled against a pre-1.21 Paper API where {@code InventoryView} was a class, and
     * still call {@code InventoryView.getTopInventory()} via {@code invokevirtual} (confirmed by
     * {@code javap} against the shaded jar) -- so on Paper 1.21's interface-shaped
     * {@code InventoryView}, that call throws {@link IncompatibleClassChangeError}. This is a
     * third-party binary incompatibility unrelated to this plan's fix, not caused by it, and not
     * fixable by editing this module. By the time it is thrown, {@code setCancelled(...)} and this
     * page's own {@code onConfirm}/{@code onCancel} callback have already run (both precede
     * {@code player.closeInventory()} in the icon's click-action lambda), so the assertions this
     * test makes are unaffected. Any other failure is rethrown rather than swallowed.
     */
    private void fireClickTolerantOfKnownCloseIncompatibility(InventoryClickEvent event) {
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (RuntimeException e) {
            Throwable cause = e;
            while (cause != null && !(cause instanceof IncompatibleClassChangeError)) {
                cause = cause.getCause();
            }
            if (cause == null) {
                throw e;
            }
        }
    }

    private int okButtonSlot() {
        // BaseConfirmationPage.OK_BUTTON_COLUMN = 5, added via addToBottomRow(column, icon),
        // whose slot is (getSize() - 9) + column.
        return page.getSize() - 9 + 5;
    }

    private int cancelButtonSlot() {
        // BaseConfirmationPage.CANCEL_BUTTON_COLUMN = 3
        return page.getSize() - 9 + 3;
    }

    @Test
    @DisplayName("在附件槽位放置物品应该被接受")
    void placingAnItemInAnAttachmentSlotIsAccepted() {
        int slot = 20; // inside the 0-44 content area
        ItemStack diamond = new ItemStack(Material.DIAMOND);

        InventoryClickEvent event = topInventoryClickEvent(slot);
        Bukkit.getPluginManager().callEvent(event);

        // Neither MockBukkit nor the framework's listener moves items on our behalf; complete
        // the placement a real, uncancelled click would result in.
        if (!event.isCancelled()) {
            page.getInventory().setItem(slot, diamond);
        }

        assertThat(page.getInventory().getItem(slot))
                .as("an item placed via an uncancelled click must land in the page's own inventory")
                .isEqualTo(diamond);
        assertThat(event.isCancelled())
                .as("a placement click in the attachment slots must not be cancelled")
                .isFalse();
    }

    @Test
    @DisplayName("确认按钮点击应该仍然按确认处理")
    void clickingTheConfirmSlotIsStillHandledAsConfirm() {
        InventoryClickEvent event = topInventoryClickEvent(okButtonSlot());
        fireClickTolerantOfKnownCloseIncompatibility(event);

        assertThat(event.isCancelled())
                .as("the toolbar slot must stay protected exactly as before this fix")
                .isTrue();
        assertThat(confirmed.get())
                .as("the OK icon's own click action must still fire")
                .isTrue();
    }

    @Test
    @DisplayName("取消按钮点击应该仍然按取消处理")
    void clickingTheCancelSlotIsStillHandledAsCancel() {
        InventoryClickEvent event = topInventoryClickEvent(cancelButtonSlot());
        fireClickTolerantOfKnownCloseIncompatibility(event);

        assertThat(event.isCancelled())
                .as("the toolbar slot must stay protected exactly as before this fix")
                .isTrue();
        assertThat(cancelled.get())
                .as("the Cancel icon's own click action must still fire")
                .isTrue();
    }

    @Test
    @DisplayName("确认时应该收集已放置的物品到附件集合")
    void confirmCollectsThePlacedItemsIntoTheAttachmentSet() {
        int slot = 10;
        ItemStack diamond = new ItemStack(Material.DIAMOND);

        InventoryClickEvent placeEvent = topInventoryClickEvent(slot);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (!placeEvent.isCancelled()) {
            page.getInventory().setItem(slot, diamond);
        }

        InventoryClickEvent confirmEvent = topInventoryClickEvent(okButtonSlot());
        fireClickTolerantOfKnownCloseIncompatibility(confirmEvent);

        assertThat(receivedItems.get())
                .as("confirming after a placement must hand the placed item onward")
                .isNotNull();
        assertThat(Arrays.stream(receivedItems.get()).anyMatch(i -> i.getType() == Material.DIAMOND))
                .as("the diamond placed into the content area must reach the attachment set")
                .isTrue();
    }

    @Test
    @DisplayName("经工具栏的位移点击（shift-click）仍应保持取消")
    void shiftClickOnToolbarSlotStaysCancelled() {
        InventoryClickEvent event = topInventoryClickEvent(okButtonSlot(),
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        fireClickTolerantOfKnownCloseIncompatibility(event);

        // InvListener's toolbar-protection branch (verified by bytecode disassembly, see this
        // class's javadoc and AttachmentSelectorPage.onClick's own javadoc) gates purely on
        // getSlot() == getRawSlot() -- it never inspects InventoryAction -- so a shift-click into
        // the toolbar must stay cancelled exactly like the plain left-click the other toolbar
        // tests use.
        assertThat(event.isCancelled())
                .as("the toolbar slot must stay protected regardless of click type")
                .isTrue();
    }

    @Test
    @DisplayName("数字键快捷栏交换（hotbar-swap）在附件槽位应该被接受")
    void numberKeyHotbarSwapOnContentSlotIsAccepted() {
        int slot = 15; // inside the 0-44 content area

        InventoryClickEvent event = topInventoryClickEvent(slot,
                ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP);
        Bukkit.getPluginManager().callEvent(event);

        // AttachmentSelectorPage.onClick reports "handled" for any raw slot inside the content
        // area purely by slot range, regardless of InventoryAction, so a number-key hotbar swap
        // must be let through exactly like the plain left-click the placement test above uses.
        assertThat(event.isCancelled())
                .as("a hotbar-swap placement in the attachment slots must not be cancelled")
                .isFalse();
    }

    @Test
    @DisplayName("拖拽放置到附件槽位仍会被取消（未修复的已知限制）")
    void dragPlacementIntoContentAreaStillCancelled() {
        int slot = 5; // inside the 0-44 content area
        ItemStack diamond = new ItemStack(Material.DIAMOND);

        InventoryDragEvent event = new InventoryDragEvent(player.getOpenInventory(),
                null, diamond, false, Collections.singletonMap(slot, diamond));

        Bukkit.getPluginManager().callEvent(event);

        // Gui.onDrag(InventoryDragEvent) defaults to `return false`, and AttachmentSelectorPage
        // does not override it (unlike onClick), so InvListener's own default cancellation for an
        // unhandled drag into the top inventory still applies -- a real mouse-drag placement
        // remains fully blocked, exactly as before this PR's fix. This is the behaviour WR-03
        // documents: the class javadoc's former "players can drag items into the GUI" claim was
        // inaccurate, and this test locks in the actual (unfixed) limitation rather than the
        // aspirational one.
        assertThat(event.isCancelled())
                .as("drag-placement into the content area is not supported and must stay cancelled")
                .isTrue();
    }
}
