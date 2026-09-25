package com.ultikits.plugins.mail.listener;

import com.ultikits.plugins.mail.gui.AttachmentSelectorPage;
import com.ultikits.plugins.mail.utils.MockBukkitHelper;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import mc.obliviate.inventory.InventoryAPI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryViewMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AttachmentGUIListener}.
 * <p>
 * <b>Why every test here stands up a real MockBukkit server instead of mocking the event.</b>
 * The defect this class covers ({@code UltiKits/UltiMail#27}) was invisible to the previous
 * generation of these tests precisely because they stubbed
 * {@code event.getInventory().getHolder()} to return an {@code AttachmentSelectorPage} -- a state
 * the real server can never produce. {@code mc.obliviate.inventory.Gui#open()} passes
 * {@code aconst_null} as the holder to {@code Bukkit.createInventory(...)} (verified by
 * {@code javap -c} against both the standalone {@code obliviate-invs} 4.3.0 core jar and the
 * shaded {@code UltiTools-API} jar), so holder-based identification never matched in production
 * and every handler in this listener was unreachable. Stubbing the holder made the tests pass
 * while the production path was dead. Therefore no test in this class stubs identification: each
 * one opens the real page through {@code page.open()}, which registers it in the real
 * {@link InventoryAPI} registry the same way a real server does, and drives real Bukkit events
 * through the real plugin manager.
 * <p>
 * <b>The one deliberate tolerance.</b> {@code obliviate-invs} 4.3.0 was compiled against a
 * pre-1.21 Bukkit where {@code InventoryView} was a class, and its {@code Gui#onClose} still
 * invokes {@code InventoryView.getTopInventory()} with {@code invokevirtual} (offset 20 of that
 * method in both jars). On the Paper 1.21 API this module compiles against, {@code InventoryView}
 * is an {@code interface}, so that call raises {@link IncompatibleClassChangeError} inside the
 * library's own {@code NORMAL}-priority close handler. Bukkit itself isolates this: the
 * {@code fireEvent} loop in {@code org.bukkit.plugin.SimplePluginManager} carries an exception
 * table entry for {@code java.lang.Throwable} per registered listener and continues with the next
 * one, so on a real server this module's own close handler still runs. MockBukkit's
 * {@code PluginManagerMock} catches only {@code EventException} and rethrows, aborting the rest
 * of the dispatch -- so the closing tests below fire the close through
 * {@link #tolerateKnownLibraryCloseIncompatibility(Runnable)}, which swallows only that specific
 * error and rethrows anything else. This module's close handler runs at
 * {@link EventPriority#LOWEST}, ahead of the library's throwing handler, which is why the
 * assertions still hold under MockBukkit's stricter dispatch.
 * <p>
 * <b>What the click tests can and cannot prove.</b> This listener's click handler has exactly one
 * cancelling branch, and that branch is the one {@code UltiKits/UltiMail#26} records as unreachable
 * (the toolbar row is fully occupied from the moment the page opens, so {@code firstEmpty()} can
 * never return a toolbar index). Whether that handler runs therefore has no observable effect on a
 * click at all, so {@code ClickHandlerTests} states exactly that -- it pins "this handler cancels
 * nothing" per branch rather than pretending to prove a guard that cannot fire. Cancellation inside
 * the page is the library's decision, taken from {@code AttachmentSelectorPage#onClick}'s return
 * value, and is covered by {@code AttachmentSelectorPageTest}.
 */
@DisplayName("AttachmentGUIListener 测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AttachmentGUIListenerTest {

    private static final Material PLACED = Material.COPPER_INGOT;

    private ServerMock server;
    private PluginMock javaPlugin;
    private PlayerMock player;
    private AttachmentGUIListener listener;
    private AttachmentSelectorPage page;
    private InventoryView view;

    private final AtomicBoolean cancelCallbackRan = new AtomicBoolean(false);
    private final AtomicReference<ItemStack[]> confirmedItems = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        server = MockBukkitHelper.bootstrapServer();
        server.addSimpleWorld("world");
        UltiToolsPlugin mockPlugin = TestHelper.mockUltiToolsPlugin();

        // The framework initialises exactly one InventoryAPI at startup
        // (DependenceManagers#initInventoryAPI); do the same here so the page's open path, and
        // this listener's own identification of it, run against the real registry.
        javaPlugin = MockBukkit.createMockPlugin();
        new InventoryAPI(javaPlugin).init();

        listener = new AttachmentGUIListener();
        Bukkit.getPluginManager().registerEvents(listener, javaPlugin);

        player = server.addPlayer("attachment-tester");
        page = new AttachmentSelectorPage(player, 27, mockPlugin,
                confirmedItems::set,
                () -> cancelCallbackRan.set(true));
        page.open();
        view = player.getOpenInventory();
    }

    @AfterEach
    void tearDown() {
        TestHelper.cleanupMocks();
        MockBukkitHelper.safeUnmock();
    }

    /**
     * Runs {@code action}, swallowing only the third-party
     * {@link IncompatibleClassChangeError} described in this class's javadoc. Anything else is
     * rethrown, so a genuine failure is never hidden.
     */
    private void tolerateKnownLibraryCloseIncompatibility(Runnable action) {
        try {
            action.run();
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

    private void placeItemInContentArea(int slot, int amount) {
        page.getInventory().setItem(slot, new ItemStack(PLACED, amount));
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

    private int countInContentArea(Material material) {
        int total = 0;
        for (int slot = 0; slot < AttachmentSelectorPage.getContentSize(); slot++) {
            ItemStack stack = page.getInventory().getItem(slot);
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private int okButtonSlot() {
        // BaseConfirmationPage.OK_BUTTON_COLUMN = 5, placed via addToBottomRow(column, icon),
        // whose slot is (getSize() - 9) + column.
        return page.getSize() - 9 + 5;
    }

    private int cancelButtonSlot() {
        // BaseConfirmationPage.CANCEL_BUTTON_COLUMN = 3
        return page.getSize() - 9 + 3;
    }

    /**
     * Invokes the toolbar icon's own registered click action -- the exact callback the library's
     * {@code InvListener} invokes for a real click on that slot -- so the confirm/cancel paths are
     * driven through production code rather than a test-only shortcut. Both actions end with
     * {@code player.closeInventory()}, hence the tolerance wrapper.
     */
    private void clickToolbarIcon(int slot) {
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tolerateKnownLibraryCloseIncompatibility(
                () -> page.getItems().get(slot).getClickAction().accept(event));
    }

    @Nested
    @DisplayName("打开时的按玩家追踪")
    class OpenTrackingTests {

        @Test
        @DisplayName("库打开页面时 holder 永远是 null——旧机制不可能匹配")
        void theHolderTheOldMechanismReliedOnIsAlwaysNull() {
            assertThat(page.getInventory().getHolder())
                    .as("the GUI library opens every page with a null holder, so the holder-type "
                            + "identification this listener used to perform can never match")
                    .isNull();
            assertThat(view.getTopInventory())
                    .as("the page's own inventory must be the one the player has open, which is "
                            + "the only identity this listener can rely on")
                    .isSameAs(page.getInventory());
        }

        /**
         * A second player with a page in the state {@code Gui#open()} leaves behind just before
         * the inventory-open event is fired: registered in the library's registry, inventory
         * created. The open event itself is then fired by the caller, which is what lets these two
         * tests differ in exactly one respect -- whether that event was cancelled. The event is
         * fired through the real plugin manager rather than handed to the handler directly, so
         * these tests never name a method the pre-fix listener did not have.
         */
        private AttachmentSelectorPage pageAwaitingItsOpenEvent(PlayerMock owner) {
            AttachmentSelectorPage owned = new AttachmentSelectorPage(owner, 27,
                    TestHelper.getMockPlugin(), items -> { }, () -> { });
            owned.setInventory(Bukkit.createInventory(null, 54, "awaiting open"));
            InventoryAPI.getInstance().getPlayers().put(owner.getUniqueId(), owned);
            owned.getInventory().setItem(0, new ItemStack(PLACED, 1));
            return owned;
        }

        @Test
        @DisplayName("未被取消的打开事件会被追踪，关闭时归还")
        void anUncancelledOpenIsTrackedAndReturnsOnClose() {
            PlayerMock owner = server.addPlayer("second-tester");
            AttachmentSelectorPage owned = pageAwaitingItsOpenEvent(owner);
            InventoryView ownedView = new PlayerInventoryViewMock(owner, owned.getInventory());

            Bukkit.getPluginManager().callEvent(new InventoryOpenEvent(ownedView));
            tolerateKnownLibraryCloseIncompatibility(
                    () -> Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(ownedView)));

            assertThat(owned.getInventory().getItem(0))
                    .as("a tracked page hands its item back when it closes")
                    .isNull();
        }

        @Test
        @DisplayName("被取消的打开事件不会被追踪")
        void aCancelledOpenIsNotTracked() {
            PlayerMock owner = server.addPlayer("third-tester");
            AttachmentSelectorPage owned = pageAwaitingItsOpenEvent(owner);
            InventoryView ownedView = new PlayerInventoryViewMock(owner, owned.getInventory());

            InventoryOpenEvent openEvent = new InventoryOpenEvent(ownedView);
            openEvent.setCancelled(true);
            Bukkit.getPluginManager().callEvent(openEvent);
            tolerateKnownLibraryCloseIncompatibility(
                    () -> Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(ownedView)));

            assertThat(owned.getInventory().getItem(0))
                    .as("an inventory that never actually opened is not tracked, so its close is "
                            + "not this listener's business -- the only difference from the test "
                            + "above is the cancellation")
                    .isNotNull();
        }

        @Test
        @DisplayName("打开普通容器不会被误认为附件选择界面")
        void openingAnOrdinaryContainerIsNotMistakenForTheSelector() {
            PlayerMock bystander = server.addPlayer("fourth-tester");
            Inventory chest = Bukkit.createInventory(null, 27, "a chest");
            InventoryView chestView = bystander.openInventory(chest);
            chest.setItem(0, new ItemStack(PLACED, 1));

            Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(chestView));

            assertThat(chest.getItem(0))
                    .as("closing an ordinary container must not drain it")
                    .isNotNull();
            assertThat(countInPlayerInventory(PLACED)).isZero();
        }

        @Test
        @DisplayName("从未打开过的页面关闭时不应被归还（归还以已追踪的打开为前提）")
        void aPageThatWasNeverOpenedGetsNoReturnOnClose() {
            placeItemInContentArea(0, 1);

            AttachmentSelectorPage neverOpened = new AttachmentSelectorPage(player, 27,
                    TestHelper.getMockPlugin(), items -> { }, () -> { });
            neverOpened.setInventory(Bukkit.createInventory(null, 54, "never opened"));
            neverOpened.getInventory().setItem(0, new ItemStack(PLACED, 1));

            Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(
                    new PlayerInventoryViewMock(player, neverOpened.getInventory())));

            assertThat(neverOpened.getInventory().getItem(0))
                    .as("a page the listener never saw open is not its business")
                    .isNotNull();
            assertThat(countInContentArea(PLACED))
                    .as("and the page that IS open must not be drained by another page's close")
                    .isEqualTo(1);
            assertThat(countInPlayerInventory(PLACED)).isZero();
        }
    }

    @Nested
    @DisplayName("未确认关闭时归还物品")
    class CloseReturnsItemsTests {

        @Test
        @DisplayName("ESC 关闭未确认的页面应把物品原样归还一次")
        void closingWithoutConfirmingReturnsThePlacedItemExactlyOnce() {
            placeItemInContentArea(0, 1);

            // Pre-assertions: the item really is in the page and really is not in the player's
            // inventory, so a passing assertion below cannot be vacuous.
            assertThat(countInContentArea(PLACED)).isEqualTo(1);
            assertThat(countInPlayerInventory(PLACED)).isZero();

            tolerateKnownLibraryCloseIncompatibility(player::closeInventory);

            assertThat(countInPlayerInventory(PLACED))
                    .as("closing without confirming must give the placed item back")
                    .isEqualTo(1);
            assertThat(countInContentArea(PLACED))
                    .as("the returned item must be gone from the page, not copied out of it")
                    .isZero();
            assertThat(countDroppedInWorld(PLACED))
                    .as("nothing needs dropping while the player has inventory space")
                    .isZero();
        }

        @Test
        @DisplayName("重复关闭事件不得把物品归还两次")
        void aSecondCloseEventCannotReturnTheItemTwice() {
            placeItemInContentArea(4, 1);
            assertThat(countInContentArea(PLACED)).isEqualTo(1);

            tolerateKnownLibraryCloseIncompatibility(player::closeInventory);
            assertThat(countInPlayerInventory(PLACED))
                    .as("the first close must return the item, otherwise this test proves nothing")
                    .isEqualTo(1);

            tolerateKnownLibraryCloseIncompatibility(
                    () -> Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view)));

            assertThat(countInPlayerInventory(PLACED) + countDroppedInWorld(PLACED))
                    .as("a duplicate close event must not duplicate the item")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("背包已满时归还的物品应掉落在玩家脚下而不是消失")
        void anItemThatNoLongerFitsIsDroppedAtThePlayersFeet() {
            player.getInventory().clear();
            // Every slot, not only the 36 storage slots: MockBukkit's InventoryMock#addItem scans
            // the whole backing array, so leaving the armor/offhand slots empty would let the
            // returned item "fit" and the drop path would never be reached.
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
            }
            placeItemInContentArea(9, 3);

            assertThat(player.getInventory().firstEmpty())
                    .as("the player's inventory must really be full for this test to mean anything")
                    .isEqualTo(-1);
            assertThat(countInContentArea(PLACED)).isEqualTo(3);

            tolerateKnownLibraryCloseIncompatibility(player::closeInventory);

            assertThat(countInPlayerInventory(PLACED))
                    .as("there is no room, so the item cannot be in the inventory")
                    .isZero();
            assertThat(countDroppedInWorld(PLACED))
                    .as("an item that does not fit must be dropped, never destroyed")
                    .isEqualTo(3);
            assertThat(countInContentArea(PLACED))
                    .as("the page must not still hold a copy of the dropped item")
                    .isZero();
        }

        @Test
        @DisplayName("点击确认后关闭不得再归还一次（否则物品被复制）")
        void closingAfterConfirmDoesNotAlsoReturnTheItems() {
            placeItemInContentArea(2, 1);
            assertThat(countInContentArea(PLACED)).isEqualTo(1);

            clickToolbarIcon(okButtonSlot());

            assertThat(confirmedItems.get())
                    .as("the OK icon's own action must have run and handed the placed item to the "
                            + "confirm callback, otherwise this test is vacuous")
                    .isNotNull();
            assertThat(countInContentArea(PLACED))
                    .as("confirming drains the slots it hands over, and that -- not a flag -- is "
                            + "what makes the close below unable to return the item again")
                    .isZero();

            tolerateKnownLibraryCloseIncompatibility(
                    () -> Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view)));

            assertThat(countInPlayerInventory(PLACED) + countDroppedInWorld(PLACED))
                    .as("a confirmed selection is attached to the mail, so returning it as well "
                            + "would duplicate it")
                    .isZero();
        }

        @Test
        @DisplayName("点击取消后再关闭只归还一次")
        void cancellingAndThenClosingReturnsTheItemOnlyOnce() {
            placeItemInContentArea(3, 1);
            assertThat(countInContentArea(PLACED)).isEqualTo(1);

            clickToolbarIcon(cancelButtonSlot());

            assertThat(cancelCallbackRan.get())
                    .as("the Cancel icon's own action must have run, otherwise this test is vacuous")
                    .isTrue();
            assertThat(countInPlayerInventory(PLACED))
                    .as("cancelling returns the placed item immediately")
                    .isEqualTo(1);

            tolerateKnownLibraryCloseIncompatibility(
                    () -> Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view)));

            assertThat(countInPlayerInventory(PLACED) + countDroppedInWorld(PLACED))
                    .as("the close that follows a cancel must not hand the item back a second time")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("其他容器不受影响")
    class OtherInventoriesTests {

        /**
         * A view over a different inventory for the SAME player, built directly rather than via
         * {@code player.openInventory(...)}: opening a second inventory would first close the
         * page, which is a different scenario and, under MockBukkit, aborts inside
         * {@code openInventory} on the library incompatibility described in this class's javadoc
         * before the new view is ever installed.
         */
        private InventoryView viewOverAnotherInventory() {
            Inventory chest = Bukkit.createInventory(null, 27, "a chest");
            return new PlayerInventoryViewMock(player, chest);
        }

        @Test
        @DisplayName("关闭另一个容器不得触发归还")
        void closingSomeOtherInventoryDoesNotReturnTheSelectorsItems() {
            placeItemInContentArea(0, 1);

            Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(viewOverAnotherInventory()));

            assertThat(countInContentArea(PLACED))
                    .as("a close event for an unrelated inventory must leave the selector alone")
                    .isEqualTo(1);
            assertThat(countInPlayerInventory(PLACED)).isZero();
        }

    }

    @Nested
    @DisplayName("点击处理器的实际效果：什么都不取消")
    class ClickHandlerTests {

        /**
         * These four cases characterise what the click handler actually does now that it is
         * reachable at all: nothing observable. Its only cancelling branch is the one
         * {@code UltiKits/UltiMail#26} records as unreachable -- the toolbar row is fully occupied
         * from the moment the page opens (verified: {@code Gui#addItem} writes each icon straight
         * into the inventory), so {@code firstEmpty()} can only ever return a content-area index
         * or {@code -1}, never a toolbar index. The handler is invoked directly here, because what
         * is being pinned is that IT touches nothing; cancellation inside the page is the library's
         * decision from {@code AttachmentSelectorPage#onClick} and is covered by
         * {@code AttachmentSelectorPageTest}. When #26 is fixed, the third case below is the one
         * that must change.
         */
        private InventoryClickEvent clickEvent(int rawSlot, ClickType type, InventoryAction action) {
            return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, type,
                    action);
        }

        @Test
        @DisplayName("内容区域的点击不被本处理器取消")
        void aContentAreaClickIsLeftAlone() {
            InventoryClickEvent event = clickEvent(0, ClickType.LEFT, InventoryAction.PLACE_ALL);
            listener.onInventoryClick(event);
            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("工具栏槽位的普通点击不被本处理器取消（由库自行取消）")
        void aPlainToolbarClickIsLeftAloneByThisHandler() {
            InventoryClickEvent event = clickEvent(okButtonSlot(), ClickType.LEFT,
                    InventoryAction.PICKUP_ALL);
            listener.onInventoryClick(event);
            assertThat(event.isCancelled())
                    .as("the toolbar is protected by the library's own default, not by this handler")
                    .isFalse();
        }

        @Test
        @DisplayName("从玩家背包 shift-click：#26 记录的守卫条件仍无法成立")
        void aShiftClickFromThePlayerInventoryStillCannotTripTheToolbarGuard() {
            InventoryClickEvent event = clickEvent(view.getTopInventory().getSize() + 5,
                    ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

            assertThat(view.getTopInventory().firstEmpty())
                    .as("the guard reads firstEmpty(), and with the toolbar pre-filled and the "
                            + "content area empty it can only answer with a content-area index")
                    .isBetween(0, AttachmentSelectorPage.getContentSize() - 1);

            listener.onInventoryClick(event);

            assertThat(event.isCancelled())
                    .as("UltiKits/UltiMail#26: this guard cannot fire, and this fix does not "
                            + "change that")
                    .isFalse();
        }

        @Test
        @DisplayName("另一个容器中的点击被本处理器忽略")
        void aClickInAnotherInventoryIsIgnored() {
            Inventory chest = Bukkit.createInventory(null, 27, "a chest");
            InventoryClickEvent event = new InventoryClickEvent(
                    new PlayerInventoryViewMock(player, chest), InventoryType.SlotType.CONTAINER,
                    5, ClickType.LEFT, InventoryAction.PLACE_ALL);

            listener.onInventoryClick(event);

            assertThat(event.isCancelled()).isFalse();
        }
    }

    @Nested
    @DisplayName("拖拽仍受工具栏保护")
    class DragTests {

        @Test
        @DisplayName("只落在内容区域的拖拽不被本处理器取消")
        void aDragConfinedToTheContentAreaIsLeftAloneByThisHandler() {
            ItemStack dragged = new ItemStack(PLACED, 1);
            InventoryDragEvent event = new InventoryDragEvent(view, null, dragged, false,
                    Collections.singletonMap(3, dragged));

            listener.onInventoryDrag(event);

            assertThat(event.isCancelled())
                    .as("this handler only guards the toolbar; the library's own default is what "
                            + "cancels a drag into the page (see this nest's cancel test)")
                    .isFalse();
        }

        @Test
        @DisplayName("另一个容器中的拖拽被本处理器忽略")
        void aDragInAnotherInventoryIsIgnored() {
            Inventory chest = Bukkit.createInventory(null, 27, "a chest");
            ItemStack dragged = new ItemStack(PLACED, 1);
            InventoryDragEvent event = new InventoryDragEvent(
                    new PlayerInventoryViewMock(player, chest), null, dragged, false,
                    Collections.singletonMap(50, dragged));

            listener.onInventoryDrag(event);

            assertThat(event.isCancelled()).isFalse();
        }

        /**
         * A guard on the observable property the checklist row claims, deliberately NOT a proof
         * that this listener's own drag branch ran: {@code Gui#onDrag} returns {@code false} by
         * default and {@code AttachmentSelectorPage} does not override it, so the library cancels
         * every drag touching this page whether or not this listener exists (both facts measured
         * by disassembly). It is kept so that a future change which makes drags writable cannot
         * silently open the confirm/cancel toolbar to them.
         */
        @Test
        @DisplayName("拖拽到工具栏区域应被取消")
        void aDragThatWouldLandInTheToolbarIsCancelled() {
            ItemStack dragged = new ItemStack(PLACED, 1);
            InventoryDragEvent event = new InventoryDragEvent(view, null, dragged, false,
                    Collections.singletonMap(okButtonSlot(), dragged));

            Bukkit.getPluginManager().callEvent(event);

            assertThat(event.isCancelled())
                    .as("the confirm/cancel toolbar must stay protected from drags")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("玩家退出时的清理")
    class QuitTests {

        @Test
        @DisplayName("玩家带着未确认的物品退出时物品不得消失")
        void quittingWithAnUnconfirmedItemStillGivesItBack() {
            placeItemInContentArea(1, 2);
            assertThat(countInContentArea(PLACED)).isEqualTo(2);

            Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(player, (String) null));

            assertThat(countInPlayerInventory(PLACED) + countDroppedInWorld(PLACED))
                    .as("an item held by the selector must not vanish when its owner leaves")
                    .isEqualTo(2);
            assertThat(countInContentArea(PLACED))
                    .as("and it must not be left in the page as a second copy")
                    .isZero();
        }
    }

    @Nested
    @DisplayName("模块卸载时的归还")
    class ModuleUnloadTests {

        /**
         * The close and quit returns both need an event, and there is no event when this module
         * is unloaded -- {@code /upm uninstall UltiMail} unregisters its listeners while the
         * player stays online, and on shutdown {@code CraftServer#disablePlugins()} runs at
         * bytecode offset 126 of {@code MinecraftServer#stopServer()} while {@code
         * PlayerList#removeAll(Z)} is only reached at offset 199, so the listener is gone before
         * any close event exists. Measured on the Paper 1.21.11 server jar this module targets.
         * <p>
         * That same ordering is what makes the fix work rather than merely move the loss:
         * {@code PlayerList#saveAll()} sits at offset 188 and each removed player's own
         * {@code save(...)} at offset 183 of {@code PlayerList#remove}, both AFTER
         * {@code disablePlugins()} -- so an item handed to an online player's inventory from
         * inside the module's unload hook is written to disk.
         * <p>
         * The cosmetic half of the hook -- closing the now-empty selector so the player is not
         * left looking at a dead GUI -- is deliberately not asserted here: under MockBukkit
         * {@code closeInventory()} aborts inside the GUI library's own close handler on the
         * {@link IncompatibleClassChangeError} described in this class's javadoc, so whether the
         * view was swapped is not observable. The real-machine row
         * {@code ultimail.lifecycle.unload-returns-attachments} covers it instead.
         */
        private AttachmentSelectorPage openSelectorFor(PlayerMock owner, int amount) {
            AttachmentSelectorPage owned = new AttachmentSelectorPage(owner, 27,
                    TestHelper.getMockPlugin(), items -> { }, () -> { });
            owned.open();
            Bukkit.getPluginManager().callEvent(
                    new InventoryOpenEvent(owner.getOpenInventory()));
            owned.getInventory().setItem(0, new ItemStack(PLACED, amount));
            return owned;
        }

        private int countInInventoryOf(PlayerMock who) {
            int total = 0;
            for (ItemStack stack : who.getInventory().getContents()) {
                if (stack != null && stack.getType() == PLACED) {
                    total += stack.getAmount();
                }
            }
            return total;
        }

        /**
         * When a selector cannot be closed during unload (in this test environment the library's
         * close really fails, which is what drives the catch), the console warning is the language
         * file's text in the server's language, through the module's logger. The plugin is
         * injected only when the listener declares one, so this runs before and after the change.
         */
        @Test
        @DisplayName("under language: zh the unload close-failure warning is the Chinese catalogue text")
        void closeFailureWarningFollowsTheLanguageSetting() throws Exception {
            UltiToolsPlugin zhPlugin = TestHelper.pluginIn("zh");
            com.ultikits.ultitools.interfaces.impl.logger.PluginLogger logger = zhPlugin.getLogger();
            try {
                TestHelper.injectField(listener, "plugin", zhPlugin);
            } catch (NoSuchFieldException absent) {
                // the listener does not read the language file yet
            }
            String expected = com.ultikits.plugins.mail.i18n.CatalogueText.text("zh", "log_attachment_close_failed");
            placeItemInContentArea(0, 3);

            listener.returnEveryOpenSelector();

            assertThat(countInPlayerInventory(PLACED) + countDroppedInWorld(PLACED))
                    .as("control: the unload return still happened").isEqualTo(3);
            org.mockito.Mockito.verify(logger, org.mockito.Mockito.atLeastOnce())
                    .warn(org.mockito.ArgumentMatchers.any(Throwable.class), org.mockito.ArgumentMatchers.eq(expected));
        }

        @Test
        @DisplayName("卸载时应把每一个打开中的选择界面里的物品归还其所有者")
        void unloadingReturnsTheItemsOfEveryOpenSelector() {
            PlayerMock second = server.addPlayer("unload-tester");
            AttachmentSelectorPage secondPage = openSelectorFor(second, 7);
            placeItemInContentArea(0, 3);

            // Pre-assertions: both pages really hold their items and neither owner holds any.
            assertThat(countInContentArea(PLACED)).isEqualTo(3);
            assertThat(secondPage.getInventory().getItem(0)).isNotNull();
            assertThat(countInPlayerInventory(PLACED)).isZero();
            assertThat(countInInventoryOf(second)).isZero();

            listener.returnEveryOpenSelector();

            assertThat(countInPlayerInventory(PLACED) + countDroppedInWorld(PLACED))
                    .as("the first player's 3 must come back when the module unloads")
                    .isEqualTo(3);
            assertThat(countInInventoryOf(second))
                    .as("every open selector is returned, not just the first one found")
                    .isEqualTo(7);
            assertThat(countInContentArea(PLACED))
                    .as("the page must not keep a second copy")
                    .isZero();
            assertThat(secondPage.getInventory().getItem(0)).isNull();
        }

        @Test
        @DisplayName("卸载后再收到关闭事件不得再归还一次")
        void unloadingForgetsThePagesItReturnedSoACloseCannotDuplicateThem() {
            placeItemInContentArea(0, 3);
            assertThat(countInContentArea(PLACED)).isEqualTo(3);

            listener.returnEveryOpenSelector();
            assertThat(countInPlayerInventory(PLACED))
                    .as("the unload return must have happened, otherwise this proves nothing")
                    .isEqualTo(3);

            // The tracking entry really is gone, not merely emptied of consequence: a page this
            // listener has already settled and closed is no longer its business, so anything that
            // ends up in that inventory afterwards is not its to hand out. This is asserted BEFORE
            // the close event below, because the close handler drops the entry too and would
            // otherwise cover for a missing removal here.
            page.getInventory().setItem(0, new ItemStack(PLACED, 9));
            listener.returnEveryOpenSelector();

            assertThat(countInPlayerInventory(PLACED) + countDroppedInWorld(PLACED))
                    .as("a second unload must not reach into a page this listener has already "
                            + "settled and forgotten")
                    .isEqualTo(3);
            assertThat(countInContentArea(PLACED))
                    .as("and that later stack must be left exactly where it was put")
                    .isEqualTo(9);

            tolerateKnownLibraryCloseIncompatibility(
                    () -> Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view)));

            assertThat(countInPlayerInventory(PLACED) + countDroppedInWorld(PLACED))
                    .as("a close event arriving after the unload return must not hand the owner "
                            + "anything from a page the unload already settled")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("没有打开中的选择界面时卸载是安全的空操作")
        void unloadingWithNoOpenSelectorIsANoOp() {
            tolerateKnownLibraryCloseIncompatibility(player::closeInventory);
            assertThat(countInPlayerInventory(PLACED))
                    .as("nothing was placed, so the close returned nothing")
                    .isZero();

            listener.returnEveryOpenSelector();

            assertThat(countInPlayerInventory(PLACED) + countDroppedInWorld(PLACED)).isZero();
        }
    }

    @Nested
    @DisplayName("注解配置测试")
    class AnnotationTests {

        @Test
        @DisplayName("类应该有 @EventListener 注解")
        void shouldHaveEventListenerAnnotation() {
            assertThat(AttachmentGUIListener.class.isAnnotationPresent(
                    com.ultikits.ultitools.annotations.EventListener.class)).isTrue();
        }

        @Test
        @DisplayName("应该实现 Bukkit Listener 接口")
        void shouldImplementListener() {
            assertThat(org.bukkit.event.Listener.class)
                    .isAssignableFrom(AttachmentGUIListener.class);
        }

        @Test
        @DisplayName("四个容器事件处理方法都应带 @EventHandler")
        void everyInventoryHandlerIsAnEventHandler() throws Exception {
            assertThat(AttachmentGUIListener.class
                    .getMethod("onInventoryOpen", InventoryOpenEvent.class)
                    .isAnnotationPresent(org.bukkit.event.EventHandler.class)).isTrue();
            assertThat(AttachmentGUIListener.class
                    .getMethod("onInventoryClick", InventoryClickEvent.class)
                    .isAnnotationPresent(org.bukkit.event.EventHandler.class)).isTrue();
            assertThat(AttachmentGUIListener.class
                    .getMethod("onInventoryDrag", InventoryDragEvent.class)
                    .isAnnotationPresent(org.bukkit.event.EventHandler.class)).isTrue();
            assertThat(AttachmentGUIListener.class
                    .getMethod("onInventoryClose", InventoryCloseEvent.class)
                    .isAnnotationPresent(org.bukkit.event.EventHandler.class)).isTrue();
        }

        @Test
        @DisplayName("onInventoryClick 应该使用 HIGH 优先级")
        void shouldUseHighPriorityForClick() throws Exception {
            assertThat(AttachmentGUIListener.class
                    .getMethod("onInventoryClick", InventoryClickEvent.class)
                    .getAnnotation(org.bukkit.event.EventHandler.class)
                    .priority()).isEqualTo(EventPriority.HIGH);
        }

        @Test
        @DisplayName("onInventoryClose 必须早于库自身的关闭处理器运行")
        void closeHandlerRunsBeforeTheLibrarysOwnCloseHandler() throws Exception {
            // The library's InvListener#onClose carries a bare @EventHandler (NORMAL) and throws
            // IncompatibleClassChangeError on Paper 1.21 -- see this class's javadoc. Running the
            // item return at LOWEST means it does not depend on that handler at all.
            assertThat(AttachmentGUIListener.class
                    .getMethod("onInventoryClose", InventoryCloseEvent.class)
                    .getAnnotation(org.bukkit.event.EventHandler.class)
                    .priority()).isEqualTo(EventPriority.LOWEST);
        }

        @Test
        @DisplayName("onPlayerQuit 应该清理追踪状态")
        void quitHandlerExists() throws Exception {
            assertThat(AttachmentGUIListener.class
                    .getMethod("onPlayerQuit", PlayerQuitEvent.class)
                    .isAnnotationPresent(org.bukkit.event.EventHandler.class)).isTrue();
        }
    }

    @Nested
    @DisplayName("内容区域大小测试")
    class ContentSizeTests {

        @Test
        @DisplayName("内容区域应该是 45 个槽位（前 5 行）")
        void shouldHaveCorrectContentSize() {
            assertThat(AttachmentSelectorPage.getContentSize()).isEqualTo(5 * 9);
        }
    }
}
