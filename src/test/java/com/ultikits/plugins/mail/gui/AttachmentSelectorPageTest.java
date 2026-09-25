package com.ultikits.plugins.mail.gui;

import com.ultikits.plugins.mail.utils.MockBukkitHelper;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import mc.obliviate.inventory.InventoryAPI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private UltiToolsPlugin plugin;
    private AttachmentSelectorPage page;
    private final AtomicBoolean confirmed = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<ItemStack[]> receivedItems = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkitHelper.bootstrapServer();
        // A world is needed because an item that no longer fits in the player's inventory is
        // dropped at their location rather than destroyed.
        server.addSimpleWorld("world");
        UltiToolsPlugin mockPlugin = TestHelper.mockUltiToolsPlugin();
        this.plugin = mockPlugin;

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
     * used to trigger the click.
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

    private int countInPlayerInventory(Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private int countDroppedInWorld(Material material) {
        int total = 0;
        for (Item item : player.getWorld().getEntitiesByClass(Item.class)) {
            if (item.getItemStack().getType() == material) {
                total += item.getItemStack().getAmount();
            }
        }
        return total;
    }

    /** Fills every slot of the player's inventory so nothing more can be added to it. */
    private void fillPlayerInventory() {
        player.getInventory().clear();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }
    }

    @Test
    @DisplayName("归还已放置的物品必须是一次性的：槽位先清空，再交还")
    void returningPlacedItemsDrainsTheContentAreaSoItCannotHappenTwice() {
        page.getInventory().setItem(0, new ItemStack(Material.COPPER_INGOT, 1));
        assertThat(page.getInventory().getItem(0))
                .as("the item must really be in the page, otherwise this test proves nothing")
                .isNotNull();
        assertThat(countInPlayerInventory(Material.COPPER_INGOT)).isZero();

        page.returnAllItems();
        page.returnAllItems();

        assertThat(countInPlayerInventory(Material.COPPER_INGOT))
                .as("the second call has nothing left to give back, because the first one emptied "
                        + "the slot as it handed the item over")
                .isEqualTo(1);
        assertThat(page.getInventory().getItem(0))
                .as("a returned item must no longer be in the page")
                .isNull();
    }

    @Test
    @DisplayName("背包已满时归还的物品应掉落而不是被销毁")
    void aReturnedItemThatNoLongerFitsIsDroppedRatherThanDestroyed() {
        fillPlayerInventory();
        page.getInventory().setItem(0, new ItemStack(Material.COPPER_INGOT, 2));
        assertThat(player.getInventory().firstEmpty())
                .as("the inventory must really be full for the drop path to be reached")
                .isEqualTo(-1);

        page.returnAllItems();

        assertThat(countInPlayerInventory(Material.COPPER_INGOT)).isZero();
        assertThat(countDroppedInWorld(Material.COPPER_INGOT))
                .as("an item with nowhere to go must be dropped at the player's feet")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("确认时超出上限的物品在背包已满时应掉落而不是被销毁")
    void excessItemsAboveTheLimitAreDroppedWhenTheInventoryIsFull() {
        AtomicReference<ItemStack[]> attached = new AtomicReference<>();
        AttachmentSelectorPage limitedPage = new AttachmentSelectorPage(player, 1, plugin,
                attached::set, () -> { });
        limitedPage.open();
        limitedPage.getInventory().setItem(0, new ItemStack(Material.COPPER_INGOT, 1));
        limitedPage.getInventory().setItem(1, new ItemStack(Material.IRON_INGOT, 1));
        fillPlayerInventory();
        assertThat(player.getInventory().firstEmpty())
                .as("the inventory must really be full for the drop path to be reached")
                .isEqualTo(-1);

        // onConfirm is protected; this test lives in the page's own package, which is the access
        // the real confirm icon has too (its click action calls exactly this method).
        limitedPage.onConfirm(null);

        assertThat(attached.get())
                .as("only the first item, up to the limit, may be attached")
                .hasSize(1);
        assertThat(countDroppedInWorld(Material.IRON_INGOT))
                .as("the item above the limit must come back to the player -- dropped when there "
                        + "is no inventory space, never destroyed")
                .isEqualTo(1);
        assertThat(countInPlayerInventory(Material.IRON_INGOT)).isZero();
    }

    /**
     * {@code onConfirm} used to leave the items it KEPT sitting in their slots and rely on the
     * {@code confirmed} flag to stop the close handler handing them back a second time -- so the
     * module's system-level claim that "no path can give the same stack back twice" because "the
     * content slot is emptied as its item is handed over" was true of {@link
     * AttachmentSelectorPage#returnAllItems()} and of the excess branch, but not of the confirm
     * path. Draining every slot it hands over makes the content area the single record of what
     * the page still owes the player, which is what lets the flag go entirely.
     */
    @Test
    @DisplayName("确认时应清空它交出的每一个槽位，使二次归还无从发生")
    void confirmDrainsEveryContentSlotItHandsOver() {
        page.getInventory().setItem(0, new ItemStack(Material.COPPER_INGOT, 5));
        page.getInventory().setItem(1, new ItemStack(Material.IRON_INGOT, 2));
        assertThat(page.getInventory().getItem(0))
                .as("the items must really be in the page, otherwise this test proves nothing")
                .isNotNull();

        page.onConfirm(null);

        assertThat(receivedItems.get())
                .as("both placed stacks are inside the limit, so both must be handed onward")
                .hasSize(2);
        for (int slot = 0; slot < AttachmentSelectorPage.getContentSize(); slot++) {
            assertThat(page.getInventory().getItem(slot))
                    .as("slot %d must have been emptied as its item was handed over", slot)
                    .isNull();
        }

        // The invariant the drained slots buy: the close, quit and unload paths all funnel into
        // returnAllItems(), and after a confirm it must find nothing left to give.
        page.returnAllItems();
        assertThat(countInPlayerInventory(Material.COPPER_INGOT) + countDroppedInWorld(Material.COPPER_INGOT))
                .as("a confirmed selection belongs to the mail; returning it as well would duplicate it")
                .isZero();
        assertThat(countInPlayerInventory(Material.IRON_INGOT) + countDroppedInWorld(Material.IRON_INGOT))
                .isZero();
    }

    /**
     * Draining the slots before the callback runs is what makes a second return impossible -- and
     * it is also what would turn a throwing callback into item destruction, since the items are
     * then in neither the page nor the mail. The confirm path therefore hands the kept items back
     * if the callback did not complete. Without that compensation this test's items would exist
     * nowhere at all.
     */
    @Test
    @DisplayName("确认回调抛异常时应把已保留的物品归还，而不是让它们消失")
    void aConfirmCallbackThatThrowsStillGivesTheKeptItemsBack() {
        AttachmentSelectorPage throwingPage = new AttachmentSelectorPage(player, 27, plugin,
                items -> {
                    throw new IllegalStateException("the confirm callback blew up");
                },
                () -> { });
        throwingPage.open();
        throwingPage.getInventory().setItem(0, new ItemStack(Material.COPPER_INGOT, 4));
        assertThat(countInPlayerInventory(Material.COPPER_INGOT))
                .as("the player must not already hold the item under test")
                .isZero();

        assertThatThrownBy(() -> throwingPage.onConfirm(null))
                .as("a failing confirm callback is surfaced, not swallowed")
                .isInstanceOf(IllegalStateException.class);

        assertThat(countInPlayerInventory(Material.COPPER_INGOT) + countDroppedInWorld(Material.COPPER_INGOT))
                .as("the callback never took custody, so the items must be back with the player "
                        + "rather than lost between the drained page and the mail that was never sent")
                .isEqualTo(4);
        assertThat(throwingPage.getInventory().getItem(0))
                .as("and they must not ALSO still be in the page, which would duplicate them")
                .isNull();
    }

    /**
     * The checklist row for this page asserted that "a shift-click INTO the content area while it
     * has room is deliberately allowed, not guarded". Measured here: it is cancelled, and not by
     * anything this module wrote. A shift-click originating in the player's own inventory has its
     * raw slot in the BOTTOM inventory, so {@link AttachmentSelectorPage#onClick} reports
     * unhandled ({@code rawSlot >= 0 && rawSlot < CONTENT_SIZE} is false), and the library's
     * {@code InvListener#onClick} then takes its {@code getSlot() != getRawSlot()} branch and
     * cancels {@code MOVE_TO_OTHER_INVENTORY} outright.
     * <p>
     * The free content slot asserted first is the positive control: "while it has room" really did
     * hold, and the click was cancelled anyway -- so this is not a full-page artefact.
     */
    @Test
    @DisplayName("从玩家背包 shift-click 放入内容区域会被取消（与文档此前的说法相反）")
    void shiftClickPlacementFromThePlayerInventoryIsCancelled() {
        InventoryView view = player.getOpenInventory();
        assertThat(view.getTopInventory().firstEmpty())
                .as("a content slot must really be free, so 'while it has room' holds and this test "
                        + "is not measuring a full page")
                .isBetween(0, AttachmentSelectorPage.getContentSize() - 1);

        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                view.getTopInventory().getSize() + 4, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);
        Bukkit.getPluginManager().callEvent(event);

        assertThat(event.isCancelled())
                .as("the GUI library cancels MOVE_TO_OTHER_INVENTORY for any bottom-inventory slot "
                        + "this page does not handle, so shift-click placement does not work -- the "
                        + "only way to place an attachment is a plain pick-up-and-place click")
                .isTrue();
    }

    /**
     * The other half of that correction: removal by shift-click DOES work, because the raw slot is
     * then inside the content area and this page reports the click handled. Kept next to the test
     * above so the asymmetry the documents now state is visible in one place.
     */
    @Test
    @DisplayName("从内容区域 shift-click 取出物品不会被取消")
    void shiftClickRemovalOutOfTheContentAreaIsNotCancelled() {
        page.getInventory().setItem(7, new ItemStack(Material.DIAMOND));

        InventoryClickEvent event = topInventoryClickEvent(7, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);
        Bukkit.getPluginManager().callEvent(event);

        assertThat(event.isCancelled())
                .as("the raw slot is inside the content area, so this page reports the click handled "
                        + "and the library leaves it alone")
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
        // remains fully blocked, exactly as before this PR's fix. This is the behaviour the class
        // javadoc now documents: the class javadoc's former "players can drag items into the GUI"
        // claim was inaccurate, and this test locks in the actual (unfixed) limitation rather
        // than the aspirational one.
        assertThat(event.isCancelled())
                .as("drag-placement into the content area is not supported and must stay cancelled")
                .isTrue();
    }
}
