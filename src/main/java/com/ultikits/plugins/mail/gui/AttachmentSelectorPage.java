package com.ultikits.plugins.mail.gui;

import com.ultikits.plugins.mail.util.ItemReturns;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.gui.BaseConfirmationPage;
import com.ultikits.ultitools.entities.Colors;
import mc.obliviate.inventory.Icon;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * GUI for selecting multiple attachments.
 * <p>
 * Players place items into the 45-slot content area to add them as attachments: by a plain click,
 * by a shift-click from their own inventory (see {@link #onClick}) or by a drag (see
 * {@link #onDrag}). The bottom toolbar row holds the confirm and cancel buttons and never takes an
 * item.
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
     * {@code CONTENT_SIZE - 1}), and places a stack shift-clicked from the player's own inventory.
     * <p>
     * The cancellation this overrides comes from the {@code obliviate-invs} library's
     * {@code InvListener}, which reads this return value: a click it is told was handled is left
     * alone, an unhandled click on a slot of this page's own top inventory is cancelled (protecting
     * the toolbar icons), and an unhandled {@code MOVE_TO_OTHER_INVENTORY} from the player's own
     * inventory is cancelled too (verified by disassembling the shaded framework jar's
     * {@code mc/obliviate/inventory/InvListener.class}).
     * <p>
     * That last cancellation is why a shift-click used to place nothing at all (UltiKits/UltiMail#32).
     * The page now moves such a stack itself -- into the first free content slot, never into the
     * toolbar, and only while the page holds fewer than {@code min(maxItems, 45)} attachments -- and
     * still reports the click unhandled, so the server's own shift-click (which would merge into
     * any matching stack, toolbar icons included) never runs. A stack the page has no room for stays
     * where it is and the player is told the limit; this replaces a guard on the attachment listener
     * that could never fire (UltiKits/UltiMail#26).
     *
     * @param event the click event
     * @return {@code true} (handled -- do not cancel) for a content-area slot, {@code false}
     *     (unhandled -- the library cancels it) otherwise
     */
    @Override
    public boolean onClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < CONTENT_SIZE) {
            return true;
        }
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && rawSlot >= getInventory().getSize()) {
            event.setCancelled(true);
            placeFromPlayerInventory(event);
        }
        return false;
    }

    /**
     * Accepts a drag that lands only in the content area or the player's own inventory, as long as
     * the content slots it newly fills keep the page within its limit; a drag touching the toolbar
     * is refused (UltiKits/UltiMail#32).
     *
     * @param event the drag event
     * @return {@code true} to let the drag through, {@code false} to have the library cancel it
     */
    @Override
    public boolean onDrag(InventoryDragEvent event) {
        int topSize = getInventory().getSize();
        int newlyFilled = 0;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                continue;
            }
            if (rawSlot < 0 || rawSlot >= CONTENT_SIZE) {
                return false;
            }
            if (isEmpty(getInventory().getItem(rawSlot))) {
                newlyFilled++;
            }
        }
        if (newlyFilled > 0 && occupiedContentSlots() + newlyFilled > attachmentLimit()) {
            tellLimit();
            return false;
        }
        return true;
    }

    /**
     * Moves the shift-clicked stack into the first free content slot, or leaves it and tells the
     * player the limit when the page holds as many attachments as it accepts.
     */
    private void placeFromPlayerInventory(InventoryClickEvent event) {
        ItemStack moving = event.getCurrentItem();
        if (isEmpty(moving)) {
            return;
        }
        int target = -1;
        if (occupiedContentSlots() < attachmentLimit()) {
            for (int i = 0; i < CONTENT_SIZE; i++) {
                if (isEmpty(getInventory().getItem(i))) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) {
            tellLimit();
            return;
        }
        getInventory().setItem(target, moving.clone());
        event.setCurrentItem(null);
    }

    /** How many attachments this page accepts: the configured limit, at most the content area. */
    private int attachmentLimit() {
        return Math.min(maxItems, CONTENT_SIZE);
    }

    private int occupiedContentSlots() {
        int occupied = 0;
        for (int i = 0; i < CONTENT_SIZE; i++) {
            if (!isEmpty(getInventory().getItem(i))) {
                occupied++;
            }
        }
        return occupied;
    }

    private void tellLimit() {
        player.sendMessage(ChatColor.YELLOW + i18n("send_items_too_many")
                .replace("{0}", String.valueOf(attachmentLimit())));
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    @Override
    protected String getOkButtonName() {
        return ChatColor.GREEN + i18n("send_confirm_attachments");
    }
    
    @Override
    protected String getCancelButtonName() {
        return ChatColor.RED + i18n("send_cancel_attachments");
    }
    
    /**
     * Hands the selection to the confirm callback, keeping at most {@code maxItems} of it and
     * giving the rest back.
     * <p>
     * <b>Every slot this method hands over is emptied before anything leaves the page.</b> The
     * content area is this page's record of what it still owes the player, so draining it is what
     * makes a second return impossible -- and it is why this class needs no "already settled"
     * flag: {@link #returnAllItems()} reached afterwards by a close, a quit or a module unload
     * finds nothing left to give. The flag this class used to carry was set by {@link #onCancel}
     * as well as by a confirm, so it answered "confirmed" for a page the player had cancelled;
     * once the close and quit handlers began reading it that mislabel was load-bearing, and
     * draining the kept slots removes the need for it entirely.
     * <p>
     * Draining first is also what makes a failing callback dangerous, since the items are then in
     * neither the page nor a mail -- so if the callback does not complete, the kept items are
     * handed back before its failure propagates.
     *
     * @param event the click on the confirm icon; unused, the selection is read from the page
     */
    @Override
    protected void onConfirm(InventoryClickEvent event) {
        Map<Integer, ItemStack> placed = collectPlacedItems();
        List<Integer> slots = new ArrayList<>(placed.keySet());
        List<ItemStack> items = new ArrayList<>(placed.values());

        List<ItemStack> excess = new ArrayList<>();
        if (items.size() > maxItems) {
            player.sendMessage(ChatColor.YELLOW + i18n("send_items_too_many")
                .replace("{0}", String.valueOf(maxItems)));
            excess.addAll(items.subList(maxItems, items.size()));
            items = new ArrayList<>(items.subList(0, maxItems));
        }

        for (Integer slot : slots) {
            getInventory().setItem(slot, null);
        }

        if (!excess.isEmpty()) {
            ItemReturns.giveOrDrop(player, excess.toArray(new ItemStack[0]));
        }

        List<ItemStack> kept = items;
        boolean handedOver = false;
        try {
            if (onConfirmCallback != null) {
                ItemStack[] result = kept.isEmpty() ? null : kept.toArray(new ItemStack[0]);
                onConfirmCallback.accept(result);
            }
            handedOver = true;
        } finally {
            if (!handedOver) {
                ItemReturns.giveOrDrop(player, kept.toArray(new ItemStack[0]));
            }
        }
    }

    @Override
    protected void onCancel(InventoryClickEvent event) {
        returnAllItems();
        if (onCancelCallback != null) {
            onCancelCallback.run();
        }
    }
    
    /**
     * Collects every item placed in the content area, keyed by the slot it sits in, in slot order.
     * <p>
     * The slot is part of the result because a caller that gives an item back has to be able to
     * empty the slot it came from -- see {@link #returnAllItems()} for why that matters.
     */
    private Map<Integer, ItemStack> collectPlacedItems() {
        Map<Integer, ItemStack> items = new LinkedHashMap<>();
        for (int i = 0; i < CONTENT_SIZE; i++) {
            ItemStack item = getInventory().getItem(i);
            if (item != null && !item.getType().isAir()) {
                items.put(i, item.clone());
            }
        }
        return items;
    }

    /**
     * Gives every item placed in the content area back to the player, exactly once.
     * <p>
     * <b>One-shot by construction, not by a flag.</b> Each slot is emptied before its item is
     * handed over, so the items this page holds are the state that makes a second return
     * impossible: a repeated call finds nothing left to give. That matters because more than one
     * path can legitimately reach this method for the same page -- the Cancel button, a real
     * {@code InventoryCloseEvent}, the library's own {@code FakeInventoryCloseEvent} when another
     * page is opened over this one, the owner quitting, and this module being unloaded -- and a
     * guard that only counted invocations would duplicate items the moment a new path was added.
     * {@link #onConfirm} drains its slots on the same rule, so this method is safe to call after
     * a confirm too and no caller needs to ask whether the page was confirmed.
     * <p>
     * Whatever the player's inventory has no room for is dropped at their feet rather than
     * discarded (see {@link ItemReturns#giveOrDrop}).
     */
    public void returnAllItems() {
        Map<Integer, ItemStack> placed = collectPlacedItems();
        for (Integer slot : placed.keySet()) {
            getInventory().setItem(slot, null);
        }
        ItemReturns.giveOrDrop(player, placed.values().toArray(new ItemStack[0]));
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
     * Gets the content area size.
     */
    public static int getContentSize() {
        return CONTENT_SIZE;
    }
    
    private String i18n(String key) {
        return plugin.i18n(key);
    }
}
