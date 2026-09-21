package com.ultikits.plugins.mail.listener;

import com.ultikits.plugins.mail.gui.AttachmentSelectorPage;
import com.ultikits.ultitools.annotations.EventListener;

import mc.obliviate.inventory.Gui;
import mc.obliviate.inventory.InventoryAPI;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for the attachment selector GUI.
 * <p>
 * Allows players to freely place and remove items in the content area, restricts actions in the
 * toolbar area, and gives back every item still sitting in the content area when the page is
 * closed without being confirmed.
 * <p>
 * <b>How the page is identified, and why it is not the inventory's holder.</b> Every handler here
 * used to ask {@code event.getInventory().getHolder() instanceof AttachmentSelectorPage}. That
 * test can never be true: {@code mc.obliviate.inventory.Gui#open()} creates the inventory with a
 * {@code null} holder (an {@code aconst_null} argument to {@code Bukkit.createInventory(...)},
 * verified by disassembling both the standalone {@code obliviate-invs} 4.3.0 jar and the shaded
 * {@code UltiTools-API} jar), so all three handlers were unreachable in production and no
 * un-confirmed item was ever returned ({@code UltiKits/UltiMail#27}).
 * <p>
 * The page is therefore tracked per player, from the inventory-open event to the inventory-close
 * event, and that tracking is the only identification used here. The open event learns which page
 * it belongs to from the GUI library's own registry ({@code InventoryAPI#getPlayersCurrentGui},
 * populated by {@code Gui#open()} before the inventory is shown, which is also what the library's
 * own {@code InvListener} consults). That registry deliberately is NOT consulted at close time:
 * the library removes the player's entry inside its own close handler, and on the Paper 1.21 API
 * this module targets that handler raises {@link IncompatibleClassChangeError} before it gets
 * there ({@code Gui#onClose} still calls {@code InventoryView.getTopInventory()} with
 * {@code invokevirtual}, while {@code InventoryView} is now an interface), so at close time the
 * registry is either already emptied or inconsistent. Module-side tracking is what makes the
 * return reliable either way.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@EventListener
public class AttachmentGUIListener implements Listener {

    /**
     * The attachment selector each player currently has open. An entry exists from the moment the
     * page's inventory is opened until it is closed or the player leaves.
     */
    private final Map<UUID, AttachmentSelectorPage> openPages = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.isCancelled() || !(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        AttachmentSelectorPage page = libraryPageBeingOpened(player, event.getInventory());
        if (page != null) {
            openPages.put(player.getUniqueId(), page);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (trackedPage(event.getWhoClicked(), event.getInventory()) == null) {
            return;
        }

        int slot = event.getRawSlot();
        int contentSize = AttachmentSelectorPage.getContentSize();

        // Allow free item manipulation in content area (slots 0-44)
        if (slot >= 0 && slot < contentSize) {
            // Don't cancel - allow normal item operations
            return;
        }

        // Bottom toolbar area (slots 45-53) - handled by base class onClick
        // Cancel shift-click from player inventory to prevent putting items in toolbar
        if (event.isShiftClick() && slot >= event.getView().getTopInventory().getSize()) {
            // Check if target slot would be in toolbar
            int targetSlot = event.getView().getTopInventory().firstEmpty();
            if (targetSlot >= contentSize) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (trackedPage(event.getWhoClicked(), event.getInventory()) == null) {
            return;
        }

        int contentSize = AttachmentSelectorPage.getContentSize();

        // Cancel if any slot is in the toolbar area
        for (int slot : event.getRawSlots()) {
            if (slot >= contentSize && slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Returns the items of a page closed without being confirmed.
     * <p>
     * Runs at {@link EventPriority#LOWEST} deliberately: the GUI library's own close handler sits
     * at the default priority and raises {@link IncompatibleClassChangeError} on Paper 1.21 (see
     * this class's javadoc), so giving the items back first means the return does not depend on
     * any other listener surviving the same event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity closedBy = event.getPlayer();
        AttachmentSelectorPage page = trackedPage(closedBy, event.getInventory());
        if (page == null) {
            return;
        }
        openPages.remove(closedBy.getUniqueId());
        page.returnAllItems();
    }

    /**
     * Drops the tracking entry when its owner leaves, and gives back anything the close event did
     * not already return -- a quitting player normally produces an inventory-close event first, so
     * this is the path that must not silently keep (or lose) the items if it does not.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        AttachmentSelectorPage page = openPages.remove(event.getPlayer().getUniqueId());
        if (page != null) {
            page.returnAllItems();
        }
    }

    /**
     * The attachment selector this player has open, or {@code null} if the inventory in question
     * is not that page's own inventory. The inventory comparison is what keeps an unrelated
     * container's event from touching a tracked page.
     */
    private AttachmentSelectorPage trackedPage(HumanEntity who, Inventory inventory) {
        AttachmentSelectorPage page = openPages.get(who.getUniqueId());
        return page != null && inventory.equals(page.getInventory()) ? page : null;
    }

    /**
     * Asks the GUI library which page it is opening for this player, accepting it only if the
     * inventory being opened really is that page's own.
     */
    private AttachmentSelectorPage libraryPageBeingOpened(Player player, Inventory inventory) {
        // getInstance() is null only if a page is somehow opened before the framework initialised
        // the GUI library (DependenceManagers#initInventoryAPI), in which case there is nothing to
        // ask and nothing to track.
        InventoryAPI api = InventoryAPI.getInstance();
        Gui gui = api == null ? null : api.getPlayersCurrentGui(player);
        return gui instanceof AttachmentSelectorPage && inventory.equals(gui.getInventory())
                ? (AttachmentSelectorPage) gui
                : null;
    }
}
