# UltiMail — UAT Checklist

This document is the executable companion to `FEATURES.md`: one row per feature stating the
steps to exercise it and the observable truth that proves it works. It is an internal reference
for real-machine verification, not user-facing documentation.

> Batches are dispatched at 60 rows or fewer, and a batch never spans two repositories. There are
> exactly two legitimate exits to `human-uat-pending`: a row needing the pixel layer while the
> real-client harness is not ready, and a row needing personal credentials. Every other row must
> reach `pass`, `fail`, or `blocked`.

## Conventions

- **Columns:** `ID`, `Preconditions`, `Steps`, `Expected`, `Layer`, `Covers`.
- **ID:** cites its `FEATURES.md` ID verbatim. A negative case suffixes the checklist ID only,
  as `.neg-<slug>` — a negative case still tests the same feature, so the base ID is unchanged.
- **Layer**, copied verbatim from Laojun's own `ultitools-real-client-uat` skill so no
  translation step exists at dispatch time: `protocol`, `java-client`, `os-input`, `pixel`,
  `server`, `human`.
- This module has no row needing personal credentials or a maintainer-authenticated UltiCloud
  panel session — the D-27b pattern (stated here for template consistency) does not currently
  apply to any row below.
- **Expected** must name an observable truth — an exact chat line, a log line, a database row,
  an inventory slot — and never the words "it works".
- **Covers** back-references a Phase 9 GUI-excluded class name; left blank when no such class
  applies.
- A row whose Preconditions name a prior row must appear after that row in file order — asserted
  mechanically: for every row, every checklist ID cited in its Preconditions cell must have a
  strictly smaller line number in this file than the row citing it (sweep class 8, D-27a).
- **Config-per-file rule (D-06):** one checklist row per `@ConfigEntity`-annotated class, never
  one row per key. The row's ID is suffixed `-yml` (`ultimail.config.mail-yml`), aggregating
  every per-key `ultimail.config.mail.*` row rather than citing a single one of them.
- **Two message sources, not one:** `RecallCommand`'s entire output, and `MailService`'s
  `notifyReceiver` (the ONLINE-receiver immediate notification, driven by `MailConfig`'s
  `messages.mail-received` field) are hardcoded/config-driven Simplified Chinese, **independent
  of the `language` setting** — those Expected cells quote the shipped default text description,
  never `language: en` as a precondition, and cite `UltiKits/UltiMail#22` (RecallCommand) or note
  the config-driven routing directly. Every other row in this module routes through
  `plugin.i18n(...)` (`lang/en.yml`/`lang/zh.yml`) and DOES carry `language: en` as a precondition.
- This module ships `max-items: 27`, `max-subject-length: 50`, `max-content-length: 500`,
  `send-cooldown: 10`, `notify-on-join: true`, `notify-delay: 3`, `email.enabled: false` as its
  defaults; every row below assumes these unless its own Preconditions say otherwise.

## Send Mail

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultimail.sendmail.text | `language: en`; an ONLINE receiver `<receiver>`; sender not on `send-cooldown` | Run `/sendmail <receiver> Greetings`, then type `Hello there` at the content prompt | Sender sees `mail_sent_success`: "Mail sent to {RECEIVER}!" (green, `sendRawMessage` — delivered even though the sender is still inside the modal conversation); the ONLINE receiver immediately sees `messages.mail-received`'s shipped (Simplified Chinese, not reproduced per D-02) text with `{SENDER}` substituted, driven by `MailConfig` directly and unaffected by `language` | server | |
| ultimail.sendmail.text.neg-cancel | `language: en`; any valid receiver | Run `/sendmail <receiver> Test`, then type `cancel` at the content prompt | `send_cancelled`: "Mail sending cancelled." (red, `sendRawMessage`); no mail is created (the receiver's inbox count is unchanged from before this attempt) | server | |
| ultimail.sendmail.text.neg-cooldown | `language: en`; the same sender as `ultimail.sendmail.text`, immediately after that row's successful send (within `send-cooldown` seconds, default 10) | Run `/sendmail <a different receiver> Another` | `send_cooldown`: "Sending mail too frequently, please wait!" (red, `sendRawMessage`) — refused before the content prompt even begins | server | |
| ultimail.sendmail.text.neg-subject-too-long | `language: en`; sender not on cooldown; a subject string of 51+ characters (exceeds `max-subject-length: 50`) | Run `/sendmail <receiver> <the 51-character subject>` | `send_subject_too_long` with `{0}` substituted to `50` (red, `sendRawMessage`) — refused before the content prompt begins | server | |
| ultimail.sendmail.attach | `language: en`; sender does NOT hold `ultimail.admin.multiattach`; sender holds a non-air item in their main hand; not on cooldown | Run `/sendmail <receiver> WithItem attach`, then type `Item inside` at the content prompt | The item is removed from the sender's main hand immediately (before the content prompt), sent as the mail's single attachment; `mail_sent_success` appears on completion, exactly as `ultimail.sendmail.text` | server | |
| ultimail.sendmail.attach.admin-multi | `language: en`; sender holds `ultimail.admin.multiattach`; not on cooldown | Run `/sendmail <receiver> Multi attach`; observe `attachment_gui_hint` in chat, then place 2 distinct items into the opened `AttachmentSelectorPage`'s content area, then click Confirm, then type content at the resulting prompt | The 45-slot content area accepts placed items freely; clicking Confirm closes the GUI and both items are attached to the resulting mail (claimable later, see `ultimail.mail.claim`) | pixel | AttachmentSelectorPage |
| ultimail.sendmail.attach.neg-empty-hand | `language: en`; sender does NOT hold `ultimail.admin.multiattach`; sender's main hand is empty (air) | Run `/sendmail <receiver> NoItem attach` | `error_no_item_in_hand`: "Please hold the item to attach in your main hand!" (red) — refused before any conversation starts | server | |
| ultimail.sendmail.help | `language: en` | Run bare `/sendmail` | `=== Send Mail Help ===` (gold, `help_sendmail_title`), then two usage lines (`help_sendmail_text`, `help_sendmail_attach`) and a cancel hint (`help_cancel_hint`), all via `plugin.i18n(...)` | server | |

## Mail

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultimail.mail.broadcast | `language: en`; sender holds `ultimail.admin.sendall`; at least 2 offline/online players other than the sender exist on the server | Run `/mail sendall <content>` | Every non-sender player (online or offline) receives a mail with subject `sendall_success` ("Mail sent to all players!") and the given content; an online recipient is notified via `messages.mail-received` immediately, same as `ultimail.sendmail.text`; the sender sees `sendall_success` in green once the async broadcast completes | server | |
| ultimail.mail.broadcast-with-items | `language: en`; sender holds `ultimail.admin.sendall`; at least 1 other player exists | Run `/mail sendall <content> items`, place 1 item into the opened `AttachmentSelectorPage`, click Confirm | `send_attachment_added` with `{0}` substituted to `1` (green); every recipient's copy of the broadcast mail carries its OWN clone of the item (verified by claiming it from two different recipients independently, both succeeding) | pixel | AttachmentSelectorPage |
| ultimail.mail.claim | `language: en`; the receiver from `ultimail.sendmail.attach` (or `.admin-multi`), with at least 1 empty inventory slot | Run `/mail inbox` to find the mail's index, then run `/mail claim <index>` | `claim_success` with `{0}` substituted to the item count (green); the item(s) now appear in the player's inventory; a subsequent `/mail claim <index>` on the same mail returns `claim_already_claimed` | server | |
| ultimail.mail.claim.neg-inventory-full | `language: en`; a receiver with an item-bearing, unclaimed mail; the receiver's inventory has 0 empty slots (fill it completely first) | Run `/mail claim <index>` | `claim_inventory_full` with `{0}` substituted to the required slot count (red); the mail remains unclaimed | server | |
| ultimail.mail.claim.neg-invalid-index | `language: en`; any receiver | Run `/mail claim 9999` (an index beyond the inbox size) | `error_invalid_index`: "Invalid mail number!" (red) | server | |
| ultimail.mail.claim.neg-no-items | `language: en`; a receiver with at least one text-only (no attachment) mail | Run `/mail claim <index>` for that text-only mail | `claim_no_items`: "This mail has no attachments!" (red) | server | |
| ultimail.mail.delete | `language: en`; a receiver with a text-only (no attachment) mail | Run `/mail delete <index>` | `delete_success`: "Mail deleted!" (green); the mail no longer appears in `/mail inbox` | server | |
| ultimail.mail.delete-all | `language: en`; a receiver with at least 2 mails with no unclaimed items, PLUS at least 1 mail with an unclaimed item attachment | Run `/mail delall` | `delete_all_success` followed by the count of mails actually deleted in parentheses, matching only the mails with no unclaimed items (green); the mail with unclaimed items is still present afterward | server | |
| ultimail.mail.delete-read | `language: en`; a receiver with at least 1 READ mail (no unclaimed items) and at least 1 UNREAD mail | Run `/mail delread` | `delete_read_success` followed by the count deleted in parentheses (green); the unread mail is untouched, still present in `/mail inbox` | server | |
| ultimail.mail.delete.neg-unclaimed-items | `language: en`; a receiver with an item-bearing, unclaimed mail | Run `/mail delete <index>` for that mail | `delete_claim_first`: "Please claim attachments before deleting!" (red); the mail is NOT deleted | server | |
| ultimail.mail.help | `language: en`; sender does NOT hold `ultimail.admin.sendall` | Run bare `/mail` | `help_title` (gold) followed by nine usage lines (`read`, `inbox`, `sent`, `read <number>`, `claim <number>`, `delete <number>`, `delall`, `delread`, `/sendmail`) — the `sendall` line is NOT shown; separately, confirm as a sender WHO holds `ultimail.admin.sendall` that the tenth `sendall` line IS shown; in neither case is a `sentgui` line printed (known gap, `UltiKits/UltiMail#21`) | server | |
| ultimail.mail.help.neg-with-sendall-permission | `language: en`; sender holds `ultimail.admin.sendall` | Run bare `/mail` | Identical block to `ultimail.mail.help`, PLUS a tenth line for `/mail sendall <content>` (red, `help_sendall`) | server | |
| ultimail.mail.inbox | `language: en`; a receiver with at least 1 mail (see `ultimail.sendmail.text`) | Run `/mail inbox` | `inbox_title` with `{0}` substituted to the actual mail count (gold); one line per mail showing `[Unread]`/`[Read]` status (green/gray), subject, and sender name; ends with `inbox_hint` (gray) | server | |
| ultimail.mail.inbox.neg-empty | `language: en`; a fresh receiver with zero mail | Run `/mail inbox` | `inbox_empty`: "Inbox is empty!" (yellow) | server | |
| ultimail.mail.read-detail | `language: en`; a receiver with an UNREAD mail carrying attached commands (author it via `MailData#setCommands`, or accept the module's own default mail flow has no command-authoring UI — read directly via `MailService#executeMailCommands` if no live authoring path exists) | Run `/mail inbox` to find the index, then run `/mail read <index>` | `mail_detail_title` (gold) then sender/subject/time/content lines (yellow labels, white values); the mail transitions from `[Unread]` to `[Read]` in a subsequent `/mail inbox`; any attached commands run exactly once (a second `/mail read <index>` does not re-run them) | server | |
| ultimail.mail.read-detail.neg-invalid-index | `language: en`; any receiver | Run `/mail read 9999` | `error_invalid_index`: "Invalid mail number!" (red) | server | |
| ultimail.mail.read-gui | `language: en`; a receiver with at least 1 mail | Run `/mail read` | `MailboxGUI` opens, titled `inbox_gui_title` with the player's own name substituted; one icon per mail (writable book if unread, book if read); clicking an unread icon marks it read and claims items if any, matching `ultimail.mail.claim`'s effect | pixel | MailboxGUI |
| ultimail.mail.sent | `language: en`; a sender who has sent at least 1 mail (see `ultimail.sendmail.text`) | Run `/mail sent` | `sentbox_title` with `{0}` substituted to the actual sent count (gold); one line per mail showing read-by-receiver status, subject, `sentbox_to`, and receiver name | server | |
| ultimail.mail.sent.neg-empty | `language: en`; a fresh sender with zero sent mail | Run `/mail sent` | `sentbox_empty`: "Sentbox is empty!" (yellow) | server | |
| ultimail.mail.sentbox-gui | `language: en`; a sender who has sent at least 1 mail | Run `/mail sentgui` | `SentboxGUI` opens, titled `sentbox_gui_title` with the player's own name substituted; one icon per sent mail (map if read by receiver, paper if unread); clicking an icon only redisplays its content in chat, no state change | pixel | SentboxGUI |

## Recall

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultimail.recall.help | none | Run literal `/recall help` (bare `/recall` reaches `sendRecall` instead, see below) | A hardcoded Simplified Chinese three-line block (`RecallCommand.java:357-359`, not reproduced per D-02), unaffected by `language: en`; no matching lang key exists anywhere (`UltiKits/UltiMail#22`) | server | |
| ultimail.recall.send-custom | Sender is OP or holds `ultimail.recall.admin` (in addition to the class-level `ultimail.recall` permission); at least 1 registered offline player exists | Run `/recall A custom homecoming message` | The custom message replaces `recall.content`/`recall.subject`'s own text verbatim in the resulting in-game mail's content (subject is still built from `recall.subject`, `{SERVER}` substituted) — confirmed by the offline recipient's inbox once they join | server | |
| ultimail.recall.send-default | Sender is OP or holds `ultimail.recall.admin`; at least 1 known offline registered player exists | Run `/recall` | A hardcoded Simplified Chinese "sending" line, then (once the async pass completes) a hardcoded Simplified Chinese completion summary naming the total registered-player count and the in-game-mail count, and (if `email.enabled`) a real-email count line (`RecallCommand.java:76,93-100`, not reproduced per D-02, `UltiKits/UltiMail#22`); every offline player receives an in-game mail from `recall.server-name` with `recall.subject`/`recall.content` (placeholders substituted); an ONLINE player is skipped entirely | server | |
| ultimail.recall.send-default.neg-no-permission | Sender holds the class-level `ultimail.recall` permission (so the command itself dispatches) but is NOT OP and does NOT hold `ultimail.recall.admin` | Run `/recall` | A hardcoded Simplified Chinese permission-denied line (`RecallCommand.java:72`, not reproduced per D-02, `UltiKits/UltiMail#22`), red; no mail is sent to anyone | server | |

## Notifications and Integration

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultimail.attachment.gui-guard | `language: en`; sender holds `ultimail.admin.multiattach`; `AttachmentSelectorPage` open (see `ultimail.sendmail.attach.admin-multi`) | Place an item in the content area (slots 0-44), confirm it is NOT cancelled; then attempt a shift-click from the player's own inventory targeting a full toolbar (slots 45+); then close the GUI via ESC without clicking Confirm | The content-area placement succeeds normally; the shift-click into the toolbar is cancelled (the item stays in the player's inventory, not the toolbar); closing without confirming returns every item placed in the content area back to the player's inventory | pixel | AttachmentSelectorPage |
| ultimail.integration.game-mail-service | none — no other module in this monorepo currently holds or calls a `GameMailService` reference (confirmed: `grep -rln GameMailService` across all module repositories returns only this module's own two files) | Read `UltiMailGameMailService.java` directly | `sendMail`/`sendSystemMail` delegate to `MailService#sendMailInternal` correctly, matching the interface contract; `notifyNewMail(Player)` sends a hardcoded Simplified Chinese line via plain `ChatColor` concatenation, with no `plugin.i18n(...)` call anywhere in this class — confirmed by reading the file, not a live-server assertion, since no current caller in this monorepo exercises this path | protocol | |
| ultimail.notify.on-join | `language: en`; `notify-on-join: true` (shipped default); the joining player has at least 1 unread mail; `notify-delay: 3` (shipped default) | Join the server, wait just over 3 seconds | A gold `✉ ` prefix, then the LITERAL, garbled text `&e[Mail] &fYou have &a{0} &funread mail(s)!` (the `{0}` placeholder is never substituted — the code replaces `{COUNT}`, which does not occur in this key; the `&`-codes are never translated to real colors) — NOT a clean colored line naming the real unread count; followed by a space, then the LITERAL text `[&e[Click to view]]` (double-bracketed, untranslated `&e`), which IS still clickable (runs `/mail read`) and still shows the hover text `Click to open inbox` despite the garbled label. Known product defect, `UltiKits/UltiMail#24` | server | |
| ultimail.notify.on-join.neg-disabled | `language: en`; `notify-on-join: false` (NOT the shipped default); the joining player has at least 1 unread mail | Join the server, wait at least 5 seconds | No notification is sent at all | server | |

## GUI

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultimail.gui.attachment-selector.neg-too-many | `language: en`; sender holds `ultimail.admin.multiattach`; `max-items: 2` (NOT the shipped default 27 — lowered for this row only) | Open `AttachmentSelectorPage` via `/sendmail <receiver> Test attach`, place 3 distinct items, click Confirm | `send_items_too_many` with `{0}` substituted to `2` (yellow); the 3rd (excess) item is returned directly to the player's inventory; only the first 2 items are attached to the resulting mail | pixel | AttachmentSelectorPage |

## Data persistence

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultimail.mail.restart-survival | A mail sent to a player who is OFFLINE at send time (via `ultimail.sendmail.text`'s same flow, aimed at an offline receiver) | Stop the server completely, then start it again, then have the receiver join and run `/mail inbox` | The mail sent before the restart is present in the inbox, with its original subject and content intact | server | |

## Configuration

One row per shipped yml file (D-06's config-per-file rule): `mail.yml` (23 keys). The row
confirms every key is present at its `FEATURES.md`-documented default, then flips one or more
representative keys and observes the behaviour follow — **except the three keys `FEATURES.md`
documents as having no observable effect** (`mail-expire-days`, `messages.new-mail`,
`messages.mail-sent`), which this row deliberately does NOT attempt to exercise for an effect.

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultimail.config.mail-yml | Fresh `config/mail.yml` at its shipped default | Load the file; confirm all 23 keys listed under `FEATURES.md`'s `## Configuration` section are present at their documented defaults; then set `max-items: 1` (default 27) and attempt to attach 2 items via `/sendmail <receiver> Test attach` as an `ultimail.admin.multiattach` holder; separately set `send-cooldown: 120` (default 10) and confirm a second `/sendmail` attempt within 120 seconds is refused where it previously was not (at the default 10s); separately set `notify-on-join: false` (default true) and confirm no join notification fires for a player with unread mail. Do NOT vary `mail-expire-days`, `messages.new-mail`, or `messages.mail-sent` expecting an observable effect — none has one (`UltiKits/UltiMail#23`) | All 23 keys present at their documented defaults before any change; (a) attaching 2 items with `max-items: 1` triggers `send_items_too_many` where it previously would not have at 27; (b) the second `/sendmail` within 120 seconds is refused with `send_cooldown`, where at the default 10s cooldown it would have succeeded by that point; (c) no join notification fires with `notify-on-join: false`, where it previously would have | server | |
