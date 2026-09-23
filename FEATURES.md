# UltiMail — Feature Inventory

This document catalogues every operator- or player-visible function, command, content item and
configuration key in this repository, as read directly from source. It is an internal reference
for UAT execution and issue reconciliation — the public description of these features lives on
<https://doc.ultikits.com/>. Update this file in the same pull request as any feature change.

## Conventions

- **ID grammar:** `<repo-slug>.<area>.<action>`, dot-separated, every segment lowercase ASCII
  drawn from `[a-z0-9-]`. `<repo-slug>` is the repository name lowercased with no separators —
  `ultimail` here, `ultitools`, `ultichat`, `ultilogin`, and `ultitools-example` for
  `UltiTools-External-Example`. `<area>` is the feature section's slug. `<action>` is the verb.
  A `config` row is the one shape that exceeds three segments and is exempt from the
  lowercase-ASCII rule for its key-path suffix:
  `<repo-slug>.config.<file-stem>.<yml key path>`, the key path keeping its own dots and its own
  casing verbatim from the yml file — a config ID is a citation of the key, not a re-derived slug,
  so lowercasing it would make it un-greppable against its own source line. An ID changes only
  when the feature's identity changes, never on rewording. IDs are unique within a repository.
- **Kind**, exactly these eight values: `command`, `config`, `event`, `gui`, `scheduled`,
  `placeholder`, `persistence`, `gate`. Each maps one-to-one onto a reconciliation-table line.
  This module has no `scheduled` rows (`@Scheduled` count is 0, confirmed below), no `gate` rows
  (`@ConditionalOnConfig` count is 0), and no `placeholder` rows (this module registers no
  PlaceholderAPI expansion and consumes none) — all three Kinds stay in the vocabulary for
  cross-repository consistency even though none appears below. The three rows under `## Lifecycle Hooks` are
  `event` rows with no `@EventHandler` site behind them: module load, `/ul reload` and
  `/upm uninstall` are framework-invoked lifecycle steps, not commands this repository maps or
  config reads, so `event` is the closest-fitting Kind.
- **Tier**, exactly three: `player`, `admin`, `internal`. Judged from what the feature is for, not
  from whether it carries a permission string — this module's three `@CmdExecutor` classes each
  declare exactly one class-level permission node (`ultimail.use`, `ultimail.send`,
  `ultimail.recall`), and two `@CmdMapping` sites additionally override with a stricter
  method-level permission (`ultimail.admin.sendall`); none sets `requireOp = true`.
- **Manual**, exactly three: `detailed`, `brief`, `none`.
- **Target**, exactly four: `player`, `console`, `both`, or `n/a` — the first three read straight
  off `@CmdTarget` for a `command` row; it is a property, not a tier. `n/a` is for every other
  Kind. `RecallCommand` carries no class-level `@CmdTarget` at all — Target is recorded `both` for
  its two rows, matching the framework default and confirmed by the class accepting a plain
  `CommandSender` (not `Player`) parameter.
- **Permission:** the literal node string, `none`, or `n/a`, each optionally suffixed with the
  literal text `(requireOp=true)` (preceded by one space) when the row's class-level
  `@CmdExecutor` carries that flag — none of this module's three `@CmdExecutor` classes sets
  `requireOp = true`, so no row below carries the suffix. `n/a` is for every Kind that is not
  `command`.
- **Source:** `ClassName#member` — the class and member that actually reads or applies the
  feature — for every Kind, `config` included: all 20 `config` rows below cite the reading
  member. This module's one configuration file (`mail.yml`) is a real
  `@ConfigEntity`/`@ConfigEntry`-bound class, so a config row's Source cites whichever class and
  method actually calls the generated getter — not the config class's own field declaration,
  which merely binds the key.
- **Row order:** by section, then by ID ascending within the section.
- **No manual prose:** no troubleshooting column, no explanatory paragraphs, no draft page text.
  A hazard noticed while reading becomes a negative checklist row, not a note here. Where a
  feature's actual runtime behaviour genuinely diverges from what its config comment or lang key
  describes (a declared-but-dead key, an unwired help line), that fact is itself part of "what the
  feature does" and is stated here as a plain, sourced observation, with the filed issue number,
  never as advice on how to fix it.

### Reconciliation command family

The canonical form for counting an annotation site across this repository's real sources:

```bash
find <repo-root> -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' \
  -not -path '*/.worktrees/*' -print0 | xargs -0 grep -nE '^[[:space:]]*@AnnotationName\b' | wc -l
```

This form defeats three measured traps, each of which produces a wrong-but-plausible number
rather than an error:

1. **Multi-root repositories** — UltiBot's sources live under `ultibot-api/`, `ultibot-core/`
   and `ultibot-v1_21_R1/`, so a naive `<repo>/src/main/java` glob returns 0 for it, silently.
   This module is a single-root Maven project, so this trap does not apply to it, but the robust
   `find` form is used regardless — the same command must work unmodified across all 18
   repositories.
2. **Git worktrees and build output** — UltiEconomy carries
   `.worktrees/economy-v2/src/main/java`, so a `find` without the `-not -path` exclusions above
   reports 48 `@CmdMapping` sites where the real number is 24. This module carries no worktree
   directory.
3. **Javadoc and string literals** — requiring the annotation to start its own line (the
   `^[[:space:]]*@` anchor) is what defeats a javadoc mention or a warning-message string literal
   that merely contains the annotation's name as text. `AttachmentSelectorPage`'s own javadoc
   mentions `Gui.onDrag(InventoryDragEvent)` and `InvListener.class` extensively but never writes
   an annotation name at the start of a line, so this module's naive and line-start counts are
   identical for every annotation kind measured below — no javadoc or string-literal false
   positive exists in this module's source — but the anchored form is still the one used, so the
   same command is trustworthy unmodified against every repository in the fan-out.

**Positive control:** the line-start form returns `@CmdExecutor` = 3, `@CmdMapping` = 17,
`@EventListener` = 2 (classes), `@EventHandler` = 6 (handler methods), `@Scheduled` = 0,
`@ConditionalOnConfig` = 0, `@ConfigEntity` = 1 (class), `@ConfigEntry` = 20, `@Table` = 1
(`MailData`) — confirmed by reading all 15 source files directly, not by trusting the count
alone. `MailCommand`'s twelve `@CmdMapping` sites are this module's standing positive control —
the class with the most sub-commands behind one executor, the shape most likely to silently drop
a row under a naive approach, confirmed present one by one against `MailCommand.java`'s own
source (`read` line 58, `sentgui` line 66, `inbox` line 73, `sent` line 102, `read <index>` line
129, `claim <index>` line 164, `delete <index>` line 200, `delall` line 226, `delread` line 235,
`sendall <content>` line 246, `sendall <content> items` line 254, bare `""` line 274). This
document's command-row count (18) diverges from the `@CmdMapping` count (17) for one explained
reason, stated in the `## Recall` section below. This section carries 0 rows against the
reconciliation table's `@Scheduled` = 0 (this module runs no scheduled task at all, and has no
mail expiry: the `mail-expire-days` key that implied one was removed by `UltiKits/UltiMail#23`,
and mail expiry is a feature request, `UltiKits/UltiMail#34`).

## Mail

`MailCommand` — class-level `@CmdExecutor(alias = {"mail", "inbox"}, permission =
"ultimail.use")` (its `description` attribute is Simplified Chinese, not reproduced here per
D-02), `@CmdTarget(PLAYER)`. Twelve `@CmdMapping` sites, all listed below.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultimail.mail.broadcast | Send a text-only mail to every offline/online player at once (admin), progress-reported every 50 recipients, running asynchronously | command | `/mail sendall <content>` | ultimail.admin.sendall | player | admin | brief | MailCommand#sendAll |
| ultimail.mail.broadcast-with-items | Broadcast mail with attached items selected via `AttachmentSelectorPage`; every recipient's copy carries a CLONE of the same items (not one shared instance) | command | `/mail sendall <content> items` | ultimail.admin.sendall | player | admin | brief | MailCommand#sendAllWithItems |
| ultimail.mail.claim | Claim the item attachments of a specific inbox mail by its listed index, refusing if already claimed, has no items, or the player's inventory lacks enough empty slots | command | `/mail claim <index>` | ultimail.use | player | player | brief | MailCommand#claim |
| ultimail.mail.delete | Soft-delete a specific inbox mail by index; refuses if it has unclaimed item attachments | command | `/mail delete <index>` | ultimail.use | player | player | brief | MailCommand#delete |
| ultimail.mail.delete-all | Soft-delete every inbox mail with no unclaimed items, reporting the count deleted | command | `/mail delall` | ultimail.use | player | player | brief | MailCommand#deleteAll |
| ultimail.mail.delete-read | Soft-delete every READ inbox mail with no unclaimed items, reporting the count deleted | command | `/mail delread` | ultimail.use | player | player | brief | MailCommand#deleteRead |
| ultimail.mail.help | Print `/mail` usage — omits a line for `sentgui` even though that sub-command exists and `lang/en.yml` declares a matching `help_sentgui` key (UltiKits/UltiMail#21); the `sendall` line is shown only to a sender holding `ultimail.admin.sendall` | command | bare `/mail` | ultimail.use | player | player | none | MailCommand#handleHelp |
| ultimail.mail.inbox | List every non-deleted received mail as text, newest first, each line showing read/claim status, subject, and sender | command | `/mail inbox` | ultimail.use | player | player | brief | MailCommand#inbox |
| ultimail.mail.read-detail | Open a specific inbox mail's full detail view by its listed index: sender, subject, timestamp, content, and (if applicable) claim-hint or already-claimed status; marks the mail read and executes any attached commands on first read | command | `/mail read <index>` | ultimail.use | player | player | brief | MailCommand#readByIndex |
| ultimail.mail.read-gui | Open the paginated inbox GUI (`MailboxGUI`) | command | `/mail read` | ultimail.use | player | player | brief | MailCommand#openInboxGUI |
| ultimail.mail.sent | List every non-deleted sent mail as text, newest first, each line showing read status (by receiver), subject, and receiver | command | `/mail sent` | ultimail.use | player | player | brief | MailCommand#sent |
| ultimail.mail.sentbox-gui | Open the paginated sentbox GUI (`SentboxGUI`) | command | `/mail sentgui` | ultimail.use | player | player | brief | MailCommand#openSentboxGUI |

## Recall

`RecallCommand` — class-level `@CmdExecutor(alias = {"recall", "callback"}, permission =
"ultimail.recall")` (its `description` attribute is Simplified Chinese, not reproduced here per
D-02), no class-level `@CmdTarget` (defaults to accepting a plain `CommandSender`). Two
`@CmdMapping` sites. `sendRecallWithMessage` additionally re-checks `sender.isOp() ||
hasPermission("ultimail.recall.admin")` by hand inside the method body — a SECOND, stricter gate
than the class-level `ultimail.recall` permission the framework's own validator already enforced
before this method runs at all, so a sender holding only `ultimail.recall` (and not OP or
`ultimail.recall.admin`) is admitted past the framework's check and then refused here. Literal
`/recall help` reaches `#handleHelp` through the framework's own short-circuit ahead of
`matchMethod`, with no `@CmdMapping` site of its own — the same mechanism the framework's own
`FEATURES.md` documents for `/upm help` — and is this section's contribution to the
`@CmdMapping`-vs-command-row divergence noted above.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultimail.recall.help | Print `/recall` usage — entirely hardcoded Simplified Chinese, no matching lang key exists anywhere (UltiKits/UltiMail#22) | command | literal `/recall help` (bare `/recall` reaches `sendRecall` instead, see below) | ultimail.recall | both | admin | none | RecallCommand#handleHelp |
| ultimail.recall.send-custom | Send a custom recall message (in-game mail, and real email if `email.enabled`) to every known offline registered player, skipping online players; requires OP or `ultimail.recall.admin` in addition to the class-level `ultimail.recall` permission. Registered-player discovery tries `UltiLogin`'s `AccountData` via reflection first, falls back to distinct mail receivers, then adds every offline player who has played before | command | `/recall <message>` | ultimail.recall | both | admin | detailed | RecallCommand#sendRecallWithMessage |
| ultimail.recall.send-default | Send the configured default recall message (same delivery and permission rules as the custom-message form) | command | `/recall` | ultimail.recall | both | admin | brief | RecallCommand#sendRecall |

## Send Mail

`SendMailCommand` — class-level `@CmdExecutor(alias = {"sendmail", "sm"}, permission =
"ultimail.send")` (its `description` attribute is Simplified Chinese, not reproduced here per
D-02), `@CmdTarget(PLAYER)`. Three `@CmdMapping` sites.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultimail.sendmail.attach | Send mail with an attachment: a sender holding `ultimail.admin.multiattach` gets the multi-item `AttachmentSelectorPage`; an ordinary sender attaches only their main-hand item (removed from their hand immediately, before the content prompt even begins). The attachment is handed back whenever the content prompt ends without the mail being sent, and the return does not depend on WHY it ended: it runs from Bukkit's conversation-abandonment hook, which every termination route reaches — typing `cancel` in any capitalisation, the 120-second inactivity timeout, the sender disconnecting, and another plugin calling `Conversation#abandon()`. A send the service refuses hands it back the same way. The return runs at most once per conversation (Bukkit's own `abandoned` guard), and what the sender's inventory can no longer hold is dropped at their feet rather than destroyed. What routes an item to that return is the conversation's custody of it, which only a mail actually accepting the items clears — not a cancellation verdict | command | `/sendmail <player> <subject> attach` (alias `/sm`) | ultimail.send | player | player | detailed | SendMailCommand#sendMailWithItems |
| ultimail.sendmail.help | Print `/sendmail` usage (two lines plus a cancel hint), fully via `plugin.i18n(...)` | command | bare `/sendmail` | ultimail.send | player | player | none | SendMailCommand#handleHelp |
| ultimail.sendmail.text | Send a text-only mail; content is collected via a modal chat conversation (120s timeout, `cancel` to abort) started after the command itself | command | `/sendmail <player> <subject>` (alias `/sm`) | ultimail.send | player | player | brief | SendMailCommand#sendMail |

## Notifications and Integration

`MailNotifyListener` and `AttachmentGUIListener` — two `@EventListener`-annotated classes,
confirmed by the reconciliation table's own count (2). `UltiMailGameMailService` is this module's
`GameMailService` implementation, a framework-defined pluggable cross-module API — reachable by
any other module or the framework holding a `GameMailService` reference, not only through this
module's own commands.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultimail.attachment.gui-guard | Five handlers on `AttachmentGUIListener`. It tracks which `AttachmentSelectorPage` each player has open, from the inventory-open event (learning the page from the GUI library's own open registry) to the inventory-close event, and that tracking is the only identification it uses — the library opens every page with an empty inventory holder, so the holder-type test this class used to perform never matched and all of its handlers were dead code (`UltiKits/UltiMail#27`). On that basis it leaves clicks inside the 45-slot content area alone, so an item can be placed there by a plain pick-up-and-place click and removed by either a plain click or a shift-click. Placement by shift-click and by drag do NOT work, and neither refusal comes from this listener: a shift-click starting in the player's own inventory has its raw slot in the bottom inventory, so the page reports it unhandled and the GUI library cancels `MOVE_TO_OTHER_INVENTORY` itself; and `Gui#onDrag` returns `false` with this page not overriding it, so the library cancels every drag touching the page before this listener's own `HIGH`-priority drag branch is reached — that branch therefore cannot change a drag's outcome either way, exactly like the shift-click-into-toolbar guard disclosed below. It returns every un-confirmed item exactly once when the page closes (each slot emptied as its item is handed over; anything the player's inventory no longer has room for is dropped at their feet), and drops its tracking when the player quits, returning any item still held. The same return is reachable without any event, for the one case that produces none — this module being unloaded, see `ultimail.lifecycle.unload`. Its shift-click-into-toolbar guard stays unreachable for its own separate reason (`UltiKits/UltiMail#26`). The net effect on placement is a deliberate limitation, not a defect: the only way to put an attachment into this page is a plain click, and that is what the `ultimail.attachment.gui-guard` checklist row now states so a tester does not record a `fail` against intended behaviour | event | interact with an open `AttachmentSelectorPage` | n/a | n/a | internal | brief | AttachmentGUIListener#onInventoryOpen, AttachmentGUIListener#onInventoryClick, AttachmentGUIListener#onInventoryDrag, AttachmentGUIListener#onInventoryClose, AttachmentGUIListener#onPlayerQuit |
| ultimail.integration.game-mail-service | Framework-pluggable `GameMailService` implementation (priority 100) letting another module send game mail, send system mail, or query unread count through `MailService` without depending on this module's command classes directly; its own `notifyNewMail(Player)` hardcodes Simplified Chinese text, unlike every other player-facing message in this module (UltiKits/UltiMail#21) | event | any other module or the framework calling a held `GameMailService` reference | n/a | n/a | internal | brief | UltiMailGameMailService#sendMail, UltiMailGameMailService#notifyNewMail |
| ultimail.notify.on-join | On join, if `notify-on-join` and the player has unread mail, send a delayed clickable chat notification, click-to-run `/mail read`. The rendered text is garbled by three compounding defects: the `{0}` placeholder in `notify_new_mail` is never substituted (the code replaces `{COUNT}`, a token that does not appear in that key), `notify_click_to_view`'s own already-bracketed text is wrapped in a second pair of brackets, and every `&`-prefixed legacy color code in both strings is left untranslated in the raw `TextComponent` text (`ChatColor.translateAlternateColorCodes` is never called). Known product defect, UltiKits/UltiMail#24 | event | join the server with at least one unread mail, `notify-on-join: true` (shipped default) | n/a | n/a | player | detailed | MailNotifyListener#onPlayerJoin |

## GUI

Phase 9 excluded all three classes below from this module's JaCoCo `check` gate
(`.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/UltiMail.md`).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultimail.gui.attachment-selector | 45-slot content area for selecting mail attachments plus a confirm/cancel toolbar; confirming past `max-items` returns the excess to the player and truncates the selection; cancelling or closing without confirming returns every placed item. Every one of those returns is one-shot, and by construction rather than by a flag: the content area IS the record of what the page still owes the player, so every slot the page hands over — the kept selection, the excess, and a cancelled or closed page's whole content — is emptied as its item leaves, and a second return finds nothing to give. There is deliberately no "already settled" state to consult, so no caller has to ask whether the page was confirmed. If the confirm callback fails, the kept items are handed back before the failure propagates, since the drained slots would otherwise leave them in neither the page nor a mail. Whatever the player's inventory no longer has room for is dropped at their feet rather than destroyed | gui | opened by `ultimail.mail.broadcast-with-items` or `ultimail.sendmail.attach` (admin branch) | n/a | n/a | player | detailed | AttachmentSelectorPage#onConfirm, AttachmentSelectorPage#returnAllItems |
| ultimail.gui.mailbox | Paginated inbox: one icon per mail (book if read, writable book if unread), lore shows sender/time/content preview and claim state; clicking marks read, executes attached commands on first read, and claims items if space allows | gui | `ultimail.mail.read-gui` | n/a | n/a | player | detailed | MailboxGUI#handleMailClick |
| ultimail.gui.sentbox | Paginated sentbox: one icon per sent mail (map if read by receiver, paper if unread), lore shows receiver/time/content preview and claim state; read-only — clicking only redisplays the content in chat, no state changes | gui | `ultimail.mail.sentbox-gui` | n/a | n/a | player | brief | SentboxGUI#createMailIcon |

## Lifecycle Hooks

As of UltiTools 6.3.0 `UltiToolsPlugin#unregisterSelf()` and `UltiToolsPlugin#reloadSelf()` are
`final` framework template methods. Before `UltiKits/UltiMail#20` this module overrode both
directly, completely replacing the framework's own steps, and each override only logged a line;
both were deleted rather than renamed. This module has an `onUnregister()` hook, added for
`UltiKits/UltiMail#27`: the framework calls that hook *before* unregistering the module's
listeners, which is the only point where both the attachment-selector tracking and the online
player still exist — see `ultimail.lifecycle.unload`. It also has an `onReload()` hook, added for
`UltiKits/UltiMail#23`, whose only work is the removed-key warning — see
`ultimail.lifecycle.removed-key-warning`. `/ul reload UltiMail` (and `/ul reload`, which reloads
every module) runs the framework's own reload steps — config reload, language refresh,
`@ConditionalOnConfig` drift report, and the framework's per-module `Module 'UltiMail' reloaded.`
INFO line — and then that hook.
`ultimail.lifecycle.reload` records what that changes for an operator: `ConfigManager#reloadConfigs`
re-initialises, in place, the same `MailConfig` instance the container injected into `MailService`,
`MailNotifyListener` and `RecallCommand`, and each of them calls its getters at call time.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultimail.lifecycle.reload | `/ul reload UltiMail` re-reads `config/mail.yml` into the running module, so an edited value such as `max-subject-length` applies to the next mail submitted without a restart; this module's own reload work is only the removed-key warning (`ultimail.lifecycle.removed-key-warning`), and it prints no reload line of its own. Before `UltiKits/UltiMail#20` the module's reload override replaced the framework's reload and only logged, so an edit took effect only after a restart | event | `/ul reload UltiMail` (framework calls `reloadSelf()`, which reloads configuration, refreshes language, reports `@ConditionalOnConfig` drift and logs its own per-module line) | n/a | n/a | admin | brief | MailService#sendMail |
| ultimail.lifecycle.removed-key-warning | When this module loads, and on every reload of it, it checks its operator's own `config/mail.yml` for the three keys `UltiKits/UltiMail#23` removed (`mail-expire-days`, `messages.new-mail`, `messages.mail-sent`), which the framework never deletes from an existing file, and logs one WARNING per key still present, naming the file and the key and saying where the setting went: mail expiry is a feature request (`UltiKits/UltiMail#34`), and each message's text is edited in this module's language file (`notify_new_mail`, `mail_sent_success`). A file without those keys produces no line; a missing or unparseable file produces none either; a failure inside the check is logged and never fails the load or the reload | event | server start (the framework calls `registerSelf()` when it loads this module), and `/ul reload UltiMail` or `/ul reload` (framework calls `reloadSelf()`, which calls this module's `onReload()` last) | n/a | n/a | admin | brief | UltiMail#registerSelf, UltiMail#onReload, RemovedConfigKeys#warnAboutLeftovers |
| ultimail.lifecycle.unload | Unloading this module hands back every item still sitting in an open `AttachmentSelectorPage` and closes that page, before the module stops listening. This is the module's only unload work: the framework's own command and listener unregistration runs around it, and `unregisterSelf()` is not overridden (it is `final`). It exists because the other two returns both need an event that unloading removes the listener for — the items an open selector holds live only in an in-memory inventory container that nothing persists, so without this they were destroyed (`UltiKits/UltiMail#27`) | event | `/upm uninstall UltiMail` (framework calls `unregisterSelf()`, which calls this module's `onUnregister()` first) | n/a | n/a | admin | detailed | UltiMail#onUnregister, AttachmentGUIListener#returnEveryOpenSelector |

## Data persistence

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultimail.mail.restart-survival | A mail sent to an offline player (`@Table("mail_messages")`-backed) survives a full server restart and is present in the recipient's inbox once they join | persistence | send mail to an offline player, then restart the server, then have that player join and check their inbox | n/a | n/a | admin | none | MailData, MailService#getInbox |

## Configuration

Every `@ConfigEntry`-annotated field on this module's one `@ConfigEntity` class (20 keys total,
matching the reconciliation table's own `@ConfigEntry` count exactly). Several of these keys
already have a behavioural row above (broadcast, recall, notification) — that row documents the
*feature* the key drives, this row documents the *key* itself, at key granularity, so the
reconciliation table can prove every key is accounted for without also making every behavioural
row carry a `config` Kind.

**Three keys that were declared and validated but never read by any production code —
`mail-expire-days`, `messages.new-mail` and `messages.mail-sent` — were removed by
UltiKits/UltiMail#23 and have no row here.** `mail-expire-days` described an expiry that does not
exist (feature request `UltiKits/UltiMail#34`); the two message keys duplicated text the language
catalogue already supplies (`notify_new_mail`, `mail_sent_success`).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultimail.config.mail.email.enabled | Whether recall notifications also attempt a real SMTP email send, in addition to in-game mail | config | `config/mail.yml: email.enabled (default: false)` | n/a | n/a | admin | brief | RecallCommand#sendRecallNotifications |
| ultimail.config.mail.email.recall-content | Real-email recall body template; `{SERVER}`/`{PLAYER}`/`{SENDER}` placeholders | config | `config/mail.yml: email.recall-content (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | RecallCommand#sendRealEmail |
| ultimail.config.mail.email.recall-subject | Real-email recall subject template; `{SERVER}` placeholder | config | `config/mail.yml: email.recall-subject (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | none | RecallCommand#sendRealEmail |
| ultimail.config.mail.email.smtp-from-email | Sender address stamped on the recall email | config | `config/mail.yml: email.smtp-from-email (default: "noreply@example.com")` | n/a | n/a | admin | brief | RecallCommand#sendRealEmail |
| ultimail.config.mail.email.smtp-host | SMTP server host used only by `/recall`'s optional real-email path (independent of the framework's own `email.smtp.*` block) | config | `config/mail.yml: email.smtp-host (default: "smtp.example.com")` | n/a | n/a | admin | brief | RecallCommand#sendRealEmail |
| ultimail.config.mail.email.smtp-password | SMTP authentication password for the recall email path | config | `config/mail.yml: email.smtp-password (default: "")` | n/a | n/a | admin | brief | RecallCommand#sendRealEmail |
| ultimail.config.mail.email.smtp-port | SMTP port for the recall email path | config | `config/mail.yml: email.smtp-port (default: 587)` | n/a | n/a | admin | none | RecallCommand#sendRealEmail |
| ultimail.config.mail.email.smtp-ssl | Use SSL for the recall email path's SMTP connection | config | `config/mail.yml: email.smtp-ssl (default: false)` | n/a | n/a | admin | none | RecallCommand#sendRealEmail |
| ultimail.config.mail.email.smtp-starttls | Use STARTTLS for the recall email path's SMTP connection (checked only when `email.smtp-ssl` is false) | config | `config/mail.yml: email.smtp-starttls (default: true)` | n/a | n/a | admin | none | RecallCommand#sendRealEmail |
| ultimail.config.mail.email.smtp-username | SMTP authentication username for the recall email path | config | `config/mail.yml: email.smtp-username (default: "")` | n/a | n/a | admin | brief | RecallCommand#sendRealEmail |
| ultimail.config.mail.max-content-length | Maximum mail content length, in characters | config | `config/mail.yml: max-content-length (default: 500)` | n/a | n/a | admin | brief | MailService#sendMail |
| ultimail.config.mail.max-items | Maximum item attachments per mail | config | `config/mail.yml: max-items (default: 27)` | n/a | n/a | admin | brief | MailService#createMailData |
| ultimail.config.mail.max-subject-length | Maximum mail subject length, in characters | config | `config/mail.yml: max-subject-length (default: 50)` | n/a | n/a | admin | brief | MailService#sendMail |
| ultimail.config.mail.messages.mail-received | Chat message shown to an ONLINE receiver the instant a mail arrives; `{SENDER}` substituted. This file's only `messages.*` key: its two former siblings, `messages.new-mail` and `messages.mail-sent`, were never read and were removed by UltiKits/UltiMail#23 | config | `config/mail.yml: messages.mail-received (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | MailService#notifyReceiver |
| ultimail.config.mail.notify-delay | Delay, in seconds, before the join notification fires, letting other join-time plugins finish first | config | `config/mail.yml: notify-delay (default: 3)` | n/a | n/a | admin | none | MailNotifyListener#onPlayerJoin |
| ultimail.config.mail.notify-on-join | Whether an unread-mail notification is sent on join at all | config | `config/mail.yml: notify-on-join (default: true)` | n/a | n/a | admin | brief | MailNotifyListener#onPlayerJoin |
| ultimail.config.mail.recall.content | In-game recall mail body template; `{SERVER}`/`{SENDER}` placeholders (overridden entirely if `/recall <message>`'s custom message argument is supplied) | config | `config/mail.yml: recall.content (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | RecallCommand#sendGameMail |
| ultimail.config.mail.recall.server-name | Server display name substituted into recall mail/email subject and content, and used as the in-game mail's sender name — NOT shown in the admin's own recall-summary lines, which contain only fixed text and numeric counts and never read this key | config | `config/mail.yml: recall.server-name (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | RecallCommand#sendGameMail |
| ultimail.config.mail.recall.subject | In-game recall mail subject template; `{SERVER}` placeholder | config | `config/mail.yml: recall.subject (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | RecallCommand#sendGameMail |
| ultimail.config.mail.send-cooldown | Minimum seconds between a player's own `/sendmail`/mail-sending actions | config | `config/mail.yml: send-cooldown (default: 10)` | n/a | n/a | admin | brief | MailService#isOnCooldown |
