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
  cross-repository consistency even though none appears below.
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
  feature — for every Kind, `config` included: all 23 `config` rows below cite the reading
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
`@EventListener` = 2 (classes), `@EventHandler` = 4 (handler methods), `@Scheduled` = 0,
`@ConditionalOnConfig` = 0, `@ConfigEntity` = 1 (class), `@ConfigEntry` = 23, `@Table` = 1
(`MailData`) — confirmed by reading all 13 source files directly, not by trusting the count
alone. `MailCommand`'s twelve `@CmdMapping` sites are this module's standing positive control —
the class with the most sub-commands behind one executor, the shape most likely to silently drop
a row under a naive approach, confirmed present one by one against `MailCommand.java`'s own
source (`read` line 58, `sentgui` line 66, `inbox` line 73, `sent` line 102, `read <index>` line
129, `claim <index>` line 164, `delete <index>` line 200, `delall` line 226, `delread` line 235,
`sendall <content>` line 246, `sendall <content> items` line 254, bare `""` line 274). This
document's command-row count (18) diverges from the `@CmdMapping` count (17) for one explained
reason, stated in the `## Recall` section below. This section carries 0 rows against the
reconciliation table's `@Scheduled` = 0 (this module runs no scheduled task at all — mail expiry,
despite `mail-expire-days` implying one exists, does not; see `## Configuration`).

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
| ultimail.sendmail.attach | Send mail with an attachment: a sender holding `ultimail.admin.multiattach` gets the multi-item `AttachmentSelectorPage`; an ordinary sender attaches only their main-hand item (removed from their hand immediately, before the content prompt even begins) | command | `/sendmail <player> <subject> attach` (alias `/sm`) | ultimail.send | player | player | detailed | SendMailCommand#sendMailWithItems |
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
| ultimail.attachment.gui-guard | Three handlers on `AttachmentGUIListener` enforcing `AttachmentSelectorPage`'s content-vs-toolbar split: allow free item placement/removal in the 45-slot content area, cancel a shift-click or drag that would land in the toolbar area, and return every un-confirmed item to the player if the GUI closes without a confirm click | event | interact with an open `AttachmentSelectorPage` | n/a | n/a | internal | brief | AttachmentGUIListener#onInventoryClick, AttachmentGUIListener#onInventoryDrag, AttachmentGUIListener#onInventoryClose |
| ultimail.integration.game-mail-service | Framework-pluggable `GameMailService` implementation (priority 100) letting another module send game mail, send system mail, or query unread count through `MailService` without depending on this module's command classes directly; its own `notifyNewMail(Player)` hardcodes Simplified Chinese text, unlike every other player-facing message in this module (UltiKits/UltiMail#21) | event | any other module or the framework calling a held `GameMailService` reference | n/a | n/a | internal | brief | UltiMailGameMailService#sendMail, UltiMailGameMailService#notifyNewMail |
| ultimail.notify.on-join | On join, if `notify-on-join` and the player has unread mail, send a delayed clickable chat notification, click-to-run `/mail read`. The rendered text is garbled by three compounding defects: the `{0}` placeholder in `notify_new_mail` is never substituted (the code replaces `{COUNT}`, a token that does not appear in that key), `notify_click_to_view`'s own already-bracketed text is wrapped in a second pair of brackets, and every `&`-prefixed legacy color code in both strings is left untranslated in the raw `TextComponent` text (`ChatColor.translateAlternateColorCodes` is never called). Known product defect, UltiKits/UltiMail#24 | event | join the server with at least one unread mail, `notify-on-join: true` (shipped default) | n/a | n/a | player | detailed | MailNotifyListener#onPlayerJoin |

## GUI

Phase 9 excluded all three classes below from this module's JaCoCo `check` gate
(`.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/UltiMail.md`).

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultimail.gui.attachment-selector | 45-slot free-placement content area plus a confirm/cancel toolbar for selecting mail attachments; confirming past `max-items` returns the excess to the player and truncates the selection; cancelling or closing without confirming returns every placed item | gui | opened by `ultimail.mail.broadcast-with-items` or `ultimail.sendmail.attach` (admin branch) | n/a | n/a | player | detailed | AttachmentSelectorPage#onConfirm |
| ultimail.gui.mailbox | Paginated inbox: one icon per mail (book if read, writable book if unread), lore shows sender/time/content preview and claim state; clicking marks read, executes attached commands on first read, and claims items if space allows | gui | `ultimail.mail.read-gui` | n/a | n/a | player | detailed | MailboxGUI#handleMailClick |
| ultimail.gui.sentbox | Paginated sentbox: one icon per sent mail (map if read by receiver, paper if unread), lore shows receiver/time/content preview and claim state; read-only — clicking only redisplays the content in chat, no state changes | gui | `ultimail.mail.sentbox-gui` | n/a | n/a | player | brief | SentboxGUI#createMailIcon |

## Data persistence

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultimail.mail.restart-survival | A mail sent to an offline player (`@Table("mail_messages")`-backed) survives a full server restart and is present in the recipient's inbox once they join | persistence | send mail to an offline player, then restart the server, then have that player join and check their inbox | n/a | n/a | admin | none | MailData, MailService#getInbox |

## Configuration

Every `@ConfigEntry`-annotated field on this module's one `@ConfigEntity` class (23 keys total,
matching the reconciliation table's own `@ConfigEntry` count exactly). Several of these keys
already have a behavioural row above (broadcast, recall, notification) — that row documents the
*feature* the key drives, this row documents the *key* itself, at key granularity, so the
reconciliation table can prove every key is accounted for without also making every behavioural
row carry a `config` Kind.

**Three keys are declared and validated but never read by any production code — see
UltiKits/UltiMail#23, each called out in its own row below rather than a claim that editing it
changes anything.**

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
| ultimail.config.mail.mail-expire-days | Declared as mail expiry in days (0 = never); never read anywhere in this module's source, and no scheduled task or read-time check enforces expiry at all — a mail never expires regardless of this key's value. Known product defect, UltiKits/UltiMail#23 | config | `config/mail.yml: mail-expire-days (default: 30, has no effect, see UltiKits/UltiMail#23)` | n/a | n/a | admin | brief | MailConfig#mailExpireDays (declared, never read outside this class) |
| ultimail.config.mail.max-content-length | Maximum mail content length, in characters | config | `config/mail.yml: max-content-length (default: 500)` | n/a | n/a | admin | brief | MailService#sendMail |
| ultimail.config.mail.max-items | Maximum item attachments per mail | config | `config/mail.yml: max-items (default: 27)` | n/a | n/a | admin | brief | MailService#createMailData |
| ultimail.config.mail.max-subject-length | Maximum mail subject length, in characters | config | `config/mail.yml: max-subject-length (default: 50)` | n/a | n/a | admin | brief | MailService#sendMail |
| ultimail.config.mail.messages.mail-received | Chat message shown to an ONLINE receiver the instant a mail arrives; `{SENDER}` substituted. The only one of this file's four `messages.*` keys genuinely read by production code | config | `config/mail.yml: messages.mail-received (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | MailService#notifyReceiver |
| ultimail.config.mail.messages.mail-sent | Declared as the send-confirmation message; never read — the real confirmation text is `lang/en.yml`'s `mail_sent_success` key instead (`SendMailCommand.ContentPrompt#acceptInput`). Known product defect, UltiKits/UltiMail#23 | config | `config/mail.yml: messages.mail-sent (default: Simplified Chinese text, not reproduced per D-02, has no effect, see UltiKits/UltiMail#23)` | n/a | n/a | admin | brief | MailConfig#mailSentMessage (declared, never read outside this class) |
| ultimail.config.mail.messages.new-mail | Declared as the join-notification message; never read — the real join-notification text is `lang/en.yml`'s `notify_new_mail` key instead (`MailNotifyListener#sendClickableNotification`). Known product defect, UltiKits/UltiMail#23 | config | `config/mail.yml: messages.new-mail (default: Simplified Chinese text, not reproduced per D-02, has no effect, see UltiKits/UltiMail#23)` | n/a | n/a | admin | brief | MailConfig#newMailMessage (declared, never read outside this class) |
| ultimail.config.mail.notify-delay | Delay, in seconds, before the join notification fires, letting other join-time plugins finish first | config | `config/mail.yml: notify-delay (default: 3)` | n/a | n/a | admin | none | MailNotifyListener#onPlayerJoin |
| ultimail.config.mail.notify-on-join | Whether an unread-mail notification is sent on join at all | config | `config/mail.yml: notify-on-join (default: true)` | n/a | n/a | admin | brief | MailNotifyListener#onPlayerJoin |
| ultimail.config.mail.recall.content | In-game recall mail body template; `{SERVER}`/`{SENDER}` placeholders (overridden entirely if `/recall <message>`'s custom message argument is supplied) | config | `config/mail.yml: recall.content (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | RecallCommand#sendGameMail |
| ultimail.config.mail.recall.server-name | Server display name substituted into recall subject/content and shown to the admin's own recall-summary lines | config | `config/mail.yml: recall.server-name (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | RecallCommand#sendGameMail |
| ultimail.config.mail.recall.subject | In-game recall mail subject template; `{SERVER}` placeholder | config | `config/mail.yml: recall.subject (default: Simplified Chinese text, not reproduced per D-02 -- see this same file's source line for the exact characters)` | n/a | n/a | admin | brief | RecallCommand#sendGameMail |
| ultimail.config.mail.send-cooldown | Minimum seconds between a player's own `/sendmail`/mail-sending actions | config | `config/mail.yml: send-cooldown (default: 10)` | n/a | n/a | admin | brief | MailService#isOnCooldown |
