package com.ultikits.plugins.mail.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The single place this module hands items back to a player.
 * <p>
 * {@code Inventory#addItem} returns the stacks it could not fit. Every call site that discards
 * that return value destroys those items whenever the player's inventory happens to be full --
 * the defect class behind {@code UltiKits/UltiMail#27}. Routing every return through this class
 * means a stack that no longer fits is dropped at the player's feet instead.
 * <p>
 * "Single place" is meant literally, and is checkable: {@code grep -rn "addItem\|dropItem"
 * src/main/java} finds no hand-over outside this class. {@code MailService#claimItems} used to
 * repeat the same pair by hand while this javadoc claimed otherwise, so it was routed here too --
 * which also fixed a failure the duplicate had of its own: it passed its raw deserialized array
 * to {@code addItem}, and a stored empty slot then threw out of the claim.
 *
 * @author wisdomme
 * @version 1.1.0
 */
public final class ItemReturns {

    private ItemReturns() {
    }

    /**
     * Gives {@code items} back to {@code player}, dropping at their location whatever their
     * inventory has no room for. Null and air entries are ignored, so callers do not have to
     * filter their own arrays -- an attachment array read back out of a conversation's session
     * data legitimately contains both.
     *
     * @param player the player to give the items back to, never {@code null}
     * @param items  the items to return; may be empty, and may contain nulls
     */
    public static void giveOrDrop(Player player, ItemStack... items) {
        List<ItemStack> real = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                real.add(item);
            }
        }
        if (real.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> overflow =
                player.getInventory().addItem(real.toArray(new ItemStack[0]));
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}
