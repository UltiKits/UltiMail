package com.ultikits.plugins.mail.service;

import com.ultikits.plugins.mail.config.MailConfig;
import com.ultikits.plugins.mail.entity.MailData;
import com.ultikits.plugins.mail.utils.MockBukkitHelper;
import com.ultikits.plugins.mail.utils.TestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.exceptions.DataAccessException;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;

/**
 * Hand-over tests for {@link MailService#claimItems}, gate 1 MN-03.
 * <p>
 * <b>Why this is a separate class from {@link MailServiceTest}.</b> That class is pure Mockito by
 * design and bootstraps no server, so {@code MailService}'s Base64 item serialization cannot run
 * there at all -- its own {@code serializeItems} tests record that it fails without a live Bukkit.
 * Every existing {@code claimItems} test therefore covers only the three early returns (already
 * claimed, null items, empty string): not one of them reaches the hand-over. This class stands up a
 * real MockBukkit server so a mail can carry genuinely serialized attachments and the hand-over can
 * be asserted against a real inventory and real dropped entities.
 * <p>
 * What MN-03 found: {@code claimItems} performed the {@code addItem} then
 * {@code dropItemNaturally(overflow)} sequence by hand, duplicating {@code ItemReturns#giveOrDrop}
 * -- whose own javadoc claims to be "the single place this module hands items back to a player".
 * The declaration and the code disagreed, which is the defect class this milestone exists to
 * remove.
 */
@DisplayName("MailService#claimItems 交付测试 (UltiKits/UltiMail#27)")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MailServiceClaimItemsTest {

    private ServerMock server;
    private PlayerMock receiver;
    private MailService mailService;
    private DataOperator<MailData> dataOperator;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkitHelper.bootstrapServer();
        // An attachment that no longer fits is dropped at the claimer's location.
        server.addSimpleWorld("world");
        receiver = server.addPlayer("claimer");

        UltiToolsPlugin plugin = TestHelper.mockUltiToolsPlugin();
        @SuppressWarnings("unchecked")
        DataOperator<MailData> operator = mock(DataOperator.class);
        dataOperator = operator;
        lenient().when(plugin.getDataOperator(any())).thenReturn((DataOperator) dataOperator);

        mailService = new MailService();
        TestHelper.injectField(mailService, "plugin", plugin);
        TestHelper.injectField(mailService, "config", new MailConfig());
        mailService.init();
    }

    @AfterEach
    void tearDown() {
        TestHelper.cleanupMocks();
        MockBukkitHelper.safeUnmock();
    }

    /**
     * Builds the Base64 payload {@code claimItems} reads, through {@code MailService}'s own private
     * serializer -- so the attachment under test is byte-identical to one a real send produced, and
     * this test cannot pass against a payload shape the production code never writes.
     */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private MailData mailCarrying(ItemStack... items) throws Exception {
        Method serialize = MailService.class.getDeclaredMethod("serializeItems", ItemStack[].class);
        serialize.setAccessible(true);
        String payload = (String) serialize.invoke(mailService, (Object) items);
        assertThat(payload)
                .as("the attachment payload must really have serialized, otherwise claimItems would "
                        + "take its empty-items early return and this test would prove nothing")
                .isNotNull();

        MailData mail = new MailData();
        mail.setId(UUID.randomUUID().toString());
        mail.setReceiverUuid(receiver.getUniqueId().toString());
        mail.setReceiverName(receiver.getName());
        mail.setItems(payload);
        mail.setClaimed(false);
        return mail;
    }

    private int countInInventory(Material material) {
        int total = 0;
        for (ItemStack stack : receiver.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private int countDropped(Material material) {
        int total = 0;
        for (Item item : receiver.getWorld().getEntitiesByClass(Item.class)) {
            if (item.getItemStack().getType() == material) {
                total += item.getItemStack().getAmount();
            }
        }
        return total;
    }

    private void fillInventory() {
        receiver.getInventory().clear();
        for (int slot = 0; slot < receiver.getInventory().getSize(); slot++) {
            receiver.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }
    }

    @Test
    @DisplayName("领取附件应把物品真正交到领取者手上")
    void claimingHandsTheAttachmentToTheClaimer() throws Exception {
        MailData mail = mailCarrying(new ItemStack(Material.DIAMOND, 2));
        assertThat(countInInventory(Material.DIAMOND))
                .as("the claimer must not already hold the item under test")
                .isZero();

        ItemStack[] claimed = mailService.claimItems(mail, receiver).getItems();

        assertThat(claimed).hasSize(1);
        assertThat(countInInventory(Material.DIAMOND))
                .as("claiming a mail with a diamond attached must put that diamond in the claimer's "
                        + "inventory")
                .isEqualTo(2);
        assertThat(mail.isClaimed()).isTrue();
    }

    @Test
    @DisplayName("背包已满时领取的附件应掉落而不是被销毁")
    void claimingWithAFullInventoryDropsTheAttachmentRatherThanDestroyingIt() throws Exception {
        MailData mail = mailCarrying(new ItemStack(Material.DIAMOND, 3));
        fillInventory();
        assertThat(receiver.getInventory().firstEmpty())
                .as("the inventory must really be full for the drop path to be reached")
                .isEqualTo(-1);

        mailService.claimItems(mail, receiver);

        assertThat(countInInventory(Material.DIAMOND)).isZero();
        assertThat(countDropped(Material.DIAMOND))
                .as("an attachment with nowhere to go must be dropped at the claimer's feet")
                .isEqualTo(3);
    }

    /**
     * The property routing through {@link com.ultikits.plugins.mail.util.ItemReturns#giveOrDrop}
     * adds, and the reason this is not merely a cosmetic de-duplication: a mail whose stored
     * attachment array contains a {@code null} -- which the Base64 payload round-trips faithfully,
     * and which any mail written before the send path started filtering air can hold -- is now
     * given to the claimer without that entry. The hand-written sequence passed the raw array
     * straight to {@code Inventory#addItem}.
     */
    @Test
    @DisplayName("附件数组中的空位不应被交给领取者，而是跳过")
    void aNullEntryInTheStoredAttachmentIsSkippedRatherThanHandedOver() throws Exception {
        MailData mail = mailCarrying(new ItemStack(Material.DIAMOND, 1), null,
                new ItemStack(Material.GOLD_INGOT, 1));

        ItemStack[] claimed = mailService.claimItems(mail, receiver).getItems();

        assertThat(claimed)
                .as("claimItems still reports what the mail stored, nulls included -- callers such "
                        + "as MailCommand#claim count that array")
                .hasSize(3);
        assertThat(countInInventory(Material.DIAMOND)).isEqualTo(1);
        assertThat(countInInventory(Material.GOLD_INGOT)).isEqualTo(1);
        assertThat(countDropped(Material.DIAMOND) + countDropped(Material.GOLD_INGOT))
                .as("both real items fitted, so neither should have been dropped")
                .isZero();
    }

    // ==================== record before hand-over (UltiKits/UltiMail#31) ====================

    /**
     * The maintainer's answer of 2026-09-24 for a one-time claim whose record cannot be written:
     * write the record first, hand over only after it was written, and refuse the claim when it
     * fails. Before, the attachment was handed over first and a failed write was only logged, and
     * because the inbox is re-read from storage on every command the attachment could be claimed
     * again at once (UltiKits/UltiMail#31). Asserted on the real inventory, the dropped items and
     * the mail's own flag.
     */
    @Test
    @DisplayName("the claimed flag cannot be written (unchecked database error): nothing is given and the mail stays claimable")
    void aFailedClaimedWriteGivesNothing() throws Exception {
        MailData mail = mailCarrying(new ItemStack(Material.DIAMOND, 2));
        doThrow(new DataAccessException("connection lost")).when(dataOperator).update(any(MailData.class));

        MailService.ClaimResult result = mailService.claimItems(mail, receiver);

        assertThat(result.getStatus()).isEqualTo(MailService.ClaimResult.Status.NOT_RECORDED);
        assertThat(result.getItems()).isEmpty();
        assertThat(countInInventory(Material.DIAMOND)).isZero();
        assertThat(countDropped(Material.DIAMOND)).isZero();
        assertThat(mail.isClaimed()).isFalse();
    }

    @Test
    @DisplayName("the claimed flag cannot be written (IllegalAccessException): nothing is given and the mail stays claimable")
    void aCheckedFailureOfTheClaimedWriteGivesNothing() throws Exception {
        MailData mail = mailCarrying(new ItemStack(Material.DIAMOND, 2));
        doThrow(new IllegalAccessException("field not accessible")).when(dataOperator).update(any(MailData.class));

        MailService.ClaimResult result = mailService.claimItems(mail, receiver);

        assertThat(result.getStatus()).isEqualTo(MailService.ClaimResult.Status.NOT_RECORDED);
        assertThat(countInInventory(Material.DIAMOND)).isZero();
        assertThat(mail.isClaimed()).isFalse();
    }

    @Test
    @DisplayName("the claimed flag is written before the attachment is handed over")
    void theClaimedFlagIsWrittenBeforeTheHandOver() throws Exception {
        MailData mail = mailCarrying(new ItemStack(Material.DIAMOND, 2));
        List<Integer> diamondsAtWrite = new ArrayList<>();
        List<Boolean> claimedAtWrite = new ArrayList<>();
        doAnswer(inv -> {
            diamondsAtWrite.add(countInInventory(Material.DIAMOND));
            claimedAtWrite.add(((MailData) inv.getArgument(0)).isClaimed());
            return null;
        }).when(dataOperator).update(any(MailData.class));

        MailService.ClaimResult result = mailService.claimItems(mail, receiver);

        assertThat(result.getStatus()).isEqualTo(MailService.ClaimResult.Status.CLAIMED);
        assertThat(diamondsAtWrite).containsExactly(0);
        assertThat(claimedAtWrite).containsExactly(true);
        assertThat(countInInventory(Material.DIAMOND)).isEqualTo(2);
    }

    @Test
    @DisplayName("across a failed write the attachment is handed over exactly once")
    void theAttachmentIsHandedOverExactlyOnce() throws Exception {
        MailData mail = mailCarrying(new ItemStack(Material.DIAMOND, 2));
        doThrow(new DataAccessException("connection lost")).doNothing()
                .when(dataOperator).update(any(MailData.class));

        assertThat(mailService.claimItems(mail, receiver).getStatus())
                .isEqualTo(MailService.ClaimResult.Status.NOT_RECORDED);
        assertThat(mailService.claimItems(mail, receiver).getStatus())
                .isEqualTo(MailService.ClaimResult.Status.CLAIMED);
        assertThat(mailService.claimItems(mail, receiver).getStatus())
                .isEqualTo(MailService.ClaimResult.Status.NOTHING_TO_CLAIM);

        assertThat(countInInventory(Material.DIAMOND) + countDropped(Material.DIAMOND)).isEqualTo(2);
    }
}
