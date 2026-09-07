package com.ultikits.plugins.mail.gui;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.gui.BaseConfirmationPage;
import com.ultikits.ultitools.entities.Colors;
import mc.obliviate.inventory.Icon;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI for selecting multiple attachments.
 * <p>
 * Players can click to place items into the GUI to add them as attachments. Drag-placement is
 * not supported: {@code Gui.onDrag(InventoryDragEvent)} defaults to cancelling any drag into this
 * page's own top inventory, and this class does not override it (see {@link #onClick} for the
 * click-side override that does exist).
 * This is only available for admins with ultimail.admin.sendall permission.
 *
 * @author wisdomme
 * @version 1.0.0
 */
public class AttachmentSelectorPage extends BaseConfirmationPage {
    
    /**
     * Content area size (5 rows x 9 columns = 45 slots)
     */
    private static final int CONTENT_SIZE = 45;
    
    private final int maxItems;
    private final UltiToolsPlugin plugin;
    private final Consumer<ItemStack[]> onConfirmCallback;
    private final Runnable onCancelCallback;
    private boolean confirmed = false;

    /**
     * Creates a new attachment selector page.
     *
     * @param player            The player opening the GUI
     * @param maxItems          Maximum number of items allowed
     * @param plugin            The plugin instance for i18n
     * @param onConfirmCallback Called when confirm button is clicked, receives the selected items
     * @param onCancelCallback  Called when cancel button is clicked or GUI is closed
     */
    public AttachmentSelectorPage(@NotNull Player player, int maxItems, UltiToolsPlugin plugin,
                                   Consumer<ItemStack[]> onConfirmCallback,
                                   Runnable onCancelCallback) {
        super(player, "attachment-selector",
              ChatColor.GOLD + plugin.i18n("send_select_attachments").replace("{0}", String.valueOf(maxItems)),
              6);
        this.maxItems = maxItems;
        this.plugin = plugin;
        this.onConfirmCallback = onConfirmCallback;
        this.onCancelCallback = onCancelCallback;
        this.setShowBottomToolbar(true);
    }
    
    @Override
    protected void setupDialogContent(InventoryOpenEvent event) {
        // Content area is left empty for players to place items
        // The bottom toolbar is set up by parent class
    }

    /**
     * Lets a player freely place and remove items in the content area (slots {@code 0} to
     * {@code CONTENT_SIZE - 1}).
     * <p>
     * The cancellation this overrides is not applied by this class, nor by
     * {@code BaseConfirmationPage}, nor by {@code BaseInventoryPage} -- none of the three
     * overrides this hook, so the {@code obliviate-invs} library's own
     * {@code mc.obliviate.inventory.Gui.onClick(InventoryClickEvent)} default (unconditionally
     * {@code false}) runs instead. The library's {@code InvListener}, which reads that return
     * value, cancels every click landing on a raw slot inside this page's own top inventory
     * unless it is told the click was handled (verified by disassembling the shaded framework
     * jar's {@code mc/obliviate/inventory/InvListener.class}: an unhandled click whose
     * {@code getSlot() == getRawSlot()} is unconditionally cancelled). Returning {@code true}
     * only for raw slots inside the content area is the narrowest override that lets placement
     * through: the bottom toolbar (the confirm/cancel buttons, slots {@code CONTENT_SIZE} and
     * up) is left unhandled here, so it keeps the same default cancellation it already had,
     * protecting those icons from being picked up or swapped.
     *
     * @param event the click event
     * @return {@code true} (handled -- do not cancel) for a content-area slot, {@code false}
     *     (unhandled -- fall back to the library's own default) otherwise
     */
    @Override
    public boolean onClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        return rawSlot >= 0 && rawSlot < CONTENT_SIZE;
    }
    
    @Override
    protected String getOkButtonName() {
        return ChatColor.GREEN + i18n("send_confirm_attachments");
    }
    
    @Override
    protected String getCancelButtonName() {
        return ChatColor.RED + i18n("send_cancel_attachments");
    }
    
    @Override
    protected void onConfirm(InventoryClickEvent event) {
        confirmed = true;
        List<ItemStack> items = collectItems();
        
        if (items.size() > maxItems) {
            // Return excess items to player
            player.sendMessage(ChatColor.YELLOW + i18n("send_items_too_many")
                .replace("{0}", String.valueOf(maxItems)));
            for (int i = maxItems; i < items.size(); i++) {
                player.getInventory().addItem(items.get(i));
            }
            items = items.subList(0, maxItems);
        }
        
        if (onConfirmCallback != null) {
            ItemStack[] result = items.isEmpty() ? null : items.toArray(new ItemStack[0]);
            onConfirmCallback.accept(result);
        }
    }
    
    @Override
    protected void onCancel(InventoryClickEvent event) {
        confirmed = true;
        returnAllItems();
        if (onCancelCallback != null) {
            onCancelCallback.run();
        }
    }
    
    /**
     * Collects all items from the content area.
     */
    private List<ItemStack> collectItems() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < CONTENT_SIZE; i++) {
            ItemStack item = getInventory().getItem(i);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        return items;
    }
    
    /**
     * Returns all items to the player.
     */
    public void returnAllItems() {
        for (int i = 0; i < CONTENT_SIZE; i++) {
            ItemStack item = getInventory().getItem(i);
            if (item != null && !item.getType().isAir()) {
                player.getInventory().addItem(item);
            }
        }
    }
    
    /**
     * Checks if any items have been placed in the GUI.
     */
    public boolean hasItems() {
        for (int i = 0; i < CONTENT_SIZE; i++) {
            ItemStack item = getInventory().getItem(i);
            if (item != null && !item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if the GUI was confirmed (vs cancelled/closed).
     */
    public boolean isConfirmed() {
        return confirmed;
    }
    
    /**
     * Gets the content area size.
     */
    public static int getContentSize() {
        return CONTENT_SIZE;
    }
    
    private String i18n(String key) {
        return plugin.i18n(key);
    }
}
