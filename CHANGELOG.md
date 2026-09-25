# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

- The five text settings in `config/mail.yml` (`messages.mail-received`, `recall.subject`,
  `recall.content`, `email.recall-subject`, `email.recall-content`) now follow `language` unless you
  have customised them. Their default is now blank, and a blank value uses the language file's text in
  the server's language; previously the default was fixed Chinese text, so `language: en` had no
  effect on the new-mail notice or on recall mails. On upgrade, at start-up and on every
  `/ul reload`, a value that is exactly the Chinese default an earlier version shipped is replaced
  with a blank value and the file is saved; any other value is yours and is used as written. The
  language file's new-mail notice names the sender `{0}`; a configured value keeps using `{SENDER}`.
  `recall.server-name` is unchanged: it is your server's name.
- `config/mail.yml` 中的五项文本设置（`messages.mail-received`、`recall.subject`、`recall.content`、
  `email.recall-subject`、`email.recall-content`）现在除非被你自定义，否则跟随 `language`。它们的默认值现为空，
  空值会以服务器语言使用语言文件中的文本；此前默认值是写死的中文，所以 `language: en` 对新邮件提醒和召回邮件都不起作用。
  升级后，在启动时以及每次 `/ul reload` 时，与旧版本出厂中文默认值完全相同的值会被替换为空值并保存文件；其他任何值都视为
  你的自定义，按原样使用。语言文件里的新邮件提醒用 `{0}` 表示发件人；你配置的值继续使用 `{SENDER}`。`recall.server-name`
  不变：它是你服务器的名字。

### Fixed

- A mail's attachment and its attached commands are now recorded as claimed before they are handed
  over, and a claim that cannot be recorded is refused instead of being repeatable. When the storage
  cannot record the claim, `/mail claim` and the mailbox window answer
  `Your claim could not be recorded, so nothing was given. Please try again.` and give nothing; when it
  cannot record that the attached commands ran, none of them runs and the reader is told to read the
  mail again. An attached command that fails is logged with the mail and the command, is not run
  again, and no longer stops the commands after it. `/mail claim` on an attachment that cannot be read
  now answers `This mail has no attachments!` instead of reporting 0 items claimed, and a storage error
  while marking a mail read is logged instead of aborting the read. A mail whose attached commands an
  earlier version left unfinished (a command that failed part-way, or a failed record) runs them once
  more when it is next read, then never again (UltiKits/UltiMail#31).
- 修复：邮件附件与附带命令先写入已领取记录再发放，记录写失败时拒绝领取，不再可以重复领取。存储无法记录领取时，`/mail claim`
  和收件箱界面会回复「领取记录无法保存，未发放任何物品，请重试。」且不发放任何物品；无法记录附带命令已执行时，一条命令都不执行，
  并提示读者重新阅读邮件。执行失败的附带命令会连同邮件和命令一起记入日志，不会再次执行，也不再阻止其后的命令。对无法读取的附件执行
  `/mail claim` 现在回复「这封邮件没有附件！」，而不是报告领取了 0 个物品；标记邮件已读时的存储错误会被记入日志，不再中断阅读。
  旧版本未执行完附带命令的邮件（命令中途失败或记录写入失败），下次阅读时会把这些命令再执行一次，之后不再执行（UltiKits/UltiMail#31）。

- `/mail help` now lists `/mail sentgui`, and each help line shows its command once, as does the
  attach line of `/sendmail` help. Those lines used to print the command twice, because the language
  file's help text already starts with it (UltiKits/UltiMail#21).
- `language: en` now applies to the unread-mail notice another module can send through UltiMail's
  mail service, which was fixed Chinese text (UltiKits/UltiMail#21), and to everything `/recall`
  prints — the permission refusal, the progress and summary lines and its help — and to the recall
  mail and email text (UltiKits/UltiMail#22). The three commands' descriptions (shown by `/help`)
  follow `language` too.
- The join notification now shows the real unread count, one pair of brackets around
  `[Click to view]`, and colour where it used to show `&` codes: it read
  `&e[Mail] &fYou have &a{0} &funread mail(s)! [&e[Click to view]]`. Clicking it still opens the
  inbox (UltiKits/UltiMail#24).
- The enable line on the console now follows `language` (it looked up a Chinese sentence that no
  language file carried), and under `language: zh` the join notification's hover text comes from the
  Chinese language file (UltiKits/UltiMail#28). `language: zh` also applies to the console lines that
  were fixed English text: a failed mail read, claim, command run, update or item conversion, a
  failed recall email, the missing-UltiLogin line of `/recall`, a selector that cannot be closed when
  the module unloads (now logged through the module's own logger), and the warning about a key this
  version no longer reads. Their English wording is unchanged, except that the warning for a
  leftover `messages.new-mail` no longer says the notification leaves the count out.
- `/mail help` 现在会列出 `/mail sentgui`，而且每行帮助只显示一次命令，`/sendmail` 帮助中关于附件的那一行也是如此。
  此前这些行都会把命令打印两遍，因为语言文件里的帮助文本本身已经以命令开头（UltiKits/UltiMail#21）。
- `language: en` 现在对其他模块可通过 UltiMail 的邮件服务发送的未读邮件提醒生效（原先是写死的中文，UltiKits/UltiMail#21），
  也对 `/recall` 打印的全部内容生效——权限拒绝、进度与汇总行以及帮助——并对召回邮件和电子邮件的文本生效
  （UltiKits/UltiMail#22）。三个命令的描述（由 `/help` 显示）也跟随 `language`。
- 登录时的邮件提醒现在会显示真实的未读数量、`[Click to view]` 只有一对方括号，原先显示 `&` 代码的地方现在显示为颜色：
  此前它显示为 `&e[Mail] &fYou have &a{0} &funread mail(s)! [&e[Click to view]]`。点击它仍会打开收件箱
  （UltiKits/UltiMail#24）。
- 控制台上的启用日志现在跟随 `language`（它原先查找的是任何语言文件都没有的中文句子），`language: zh` 下登录提醒的
  悬停文本来自中文语言文件（UltiKits/UltiMail#28）。`language: zh` 也对原先写死为英文的控制台日志生效：读取、领取、
  执行命令、更新邮件或转换物品失败，召回电子邮件发送失败，`/recall` 找不到 UltiLogin 的提示、模块卸载时无法关闭附件选择界面（现在通过本模块自己的日志器输出），
  以及本版本不再读取的配置键的警告。它们的英文措辞不变，只是残留的 `messages.new-mail` 的警告不再说提醒会漏掉数量。

- Reloading this module (`/ul reload UltiMail`, or `/ul reload` for every module) now re-reads
  `config/mail.yml` and refreshes the language files, so an edited value such as
  `max-subject-length` applies to the next mail without a restart. Previously this module's reload
  method replaced the framework's and only logged a line, so neither step ran. UltiTools 6.3.0 also
  reports `@ConditionalOnConfig` drift and logs its own per-module reload line at this point
  (UltiKits/UltiMail#20).
- Unloading this module (`/upm uninstall UltiMail`) now runs the framework's command
  unregistration and then its listener unregistration, so the module's commands are really removed
  and its listeners stop firing; this module has no unload work of its own. Previously this module's
  unload method replaced the framework's and only logged a line, so after `/upm uninstall UltiMail`
  both its commands and its listeners stayed active until the server restarted (UltiKits/UltiMail#20).
- An item placed in the attachment selector is now given back when that GUI is closed without
  clicking Confirm — the selector reached by `/sendmail <player> <subject> attach` for a sender
  holding `ultimail.admin.multiattach`, and by `/mail sendall <content> items`. Previously the item
  was destroyed: the module recognised the closing page by the inventory's holder, which the GUI
  library always leaves empty, so the code that gives items back could never run. The items come
  back exactly once, and any stack the sender's inventory no longer has room for is dropped at
  their feet instead of vanishing (UltiKits/UltiMail#27).
- The three other points where a mail attachment is handed back now also drop what no longer fits
  at the sender's feet instead of destroying it: the items above `max-items` when Confirm is
  clicked, a content prompt that ends without sending, and a send the server refuses (for
  example an unknown receiver). Each of these previously discarded whatever the sender's inventory
  could not hold (UltiKits/UltiMail#27).
- Unloading this module (`/upm uninstall UltiMail`) now hands back every item still sitting in an
  open attachment selector, and closes that selector, before the module stops listening. Previously
  those items were destroyed: the code that gives them back runs from an inventory-close event, and
  unloading the module unregisters its listeners, so no close event could reach it. An item placed
  in that GUI exists only in memory until the mail is created, which is why there was nothing to
  recover afterwards (UltiKits/UltiMail#27).
- An attachment waiting at the content prompt is now given back whenever that prompt ends without
  the mail being sent, however it ends: typing `cancel` in any capitalisation, the prompt's
  120-second timeout, the sender leaving the server, or another plugin ending the prompt.
  Previously only the exact lower-case word `cancel` gave it back. Typing `Cancel` or `CANCEL`
  ended the prompt by a different route, printed nothing at all, and destroyed the attachment —
  so a sender who held one copper ingot, ran `/sendmail <player> <subject> attach` and then typed
  `Cancel` was left with no ingot, nothing on the ground and no mail. The sender is now also told
  their send was cancelled on every one of those routes, instead of only some of them
  (UltiKits/UltiMail#27).
- 重载本模块（`/ul reload UltiMail`，或对所有模块执行 `/ul reload`）现在会重新读取 `config/mail.yml`
  并刷新语言文件，修改后的 `max-subject-length` 等配置无需重启即可对下一封邮件生效。此前本模块的
  重载方法替换了框架的重载方法且只输出一行日志，这两步都不会执行。UltiTools 6.3.0 还会在此时报告
  `@ConditionalOnConfig` 漂移并输出框架自身的模块重载日志（UltiKits/UltiMail#20）。
- 卸载本模块（`/upm uninstall UltiMail`）现在会先由框架注销命令，再注销监听器，本模块的命令会被真正移除，
  其监听器也不再触发；本模块自身没有卸载工作。此前本模块的卸载方法替换了框架的卸载方法且只输出一行日志，
  因此执行 `/upm uninstall UltiMail` 后，其命令和监听器都会保持生效，直到服务器重启（UltiKits/UltiMail#20）。
- 放入附件选择界面的物品在未点击"确认"就关闭该界面时会被归还——该界面可由持有
  `ultimail.admin.multiattach` 的发送者执行 `/sendmail <玩家> <主题> attach`，或执行
  `/mail sendall <内容> items` 打开。此前物品会被销毁：模块通过容器 holder 判断关闭的是哪个界面，
  而 GUI 库打开界面时 holder 始终为空，因此归还物品的代码永远不会执行。现在物品只会被归还一次，
  发送者背包放不下的部分会掉落在其脚下，而不再消失（UltiKits/UltiMail#27）。
- 另外三处归还邮件附件的位置现在同样会把放不下的物品掉落在发送者脚下，而不再销毁：点击"确认"时超出
  `max-items` 的部分、内容输入在未发送的情况下结束、以及服务器拒绝发送（例如收件人不存在）。此前这三处
  都会丢弃发送者背包容纳不下的物品（UltiKits/UltiMail#27）。
- 卸载本模块（`/upm uninstall UltiMail`）现在会在模块停止监听之前，把仍留在打开着的附件选择界面中的
  每一件物品归还发送者，并关闭该界面。此前这些物品会被销毁：归还物品的代码由容器关闭事件触发，而卸载
  模块会注销其监听器，因此不会有任何关闭事件到达它。放入该界面的物品在邮件创建之前只存在于内存中，
  所以事后也无从恢复（UltiKits/UltiMail#27）。
- 等在内容输入提示处的附件，现在只要该提示在邮件未发出的情况下结束就会被归还，无论以何种方式结束：
  输入任意大小写的 `cancel`、该提示 120 秒超时、发送者离开服务器，或由其他插件结束该提示。此前只有
  输入全小写的 `cancel` 才会归还。输入 `Cancel` 或 `CANCEL` 会走另一条路径结束该提示，既不输出任何
  提示也会销毁附件——因此手持一个铜锭、执行 `/sendmail <玩家> <主题> attach` 后输入 `Cancel` 的发送者，
  既拿不回铜锭，地上也没有掉落物，邮件同样不存在。现在上述每一条路径都会告知发送者发送已取消，而不再
  只有其中一部分会告知（UltiKits/UltiMail#27）。

### Removed

- 26 language-file entries that no code displayed, from `lang/en.yml` and `lang/zh.yml`: the
  `gui_*` page and button words (the pages use UltiTools' own), the `delete_confirm_*` lines (there is
  no delete confirmation page), the `send_*` lines that other entries replaced (`input_content_prompt`,
  `mail_sent_success`, `error_no_item_in_hand`), `attachment_gui_title`, `lore_subject`,
  `lore_items_count`, `error_no_permission`, `sendall_no_permission` (a permission refusal comes from
  UltiTools' own message), `arg_number` and `arg_content`; and `notify_hover_hint` from
  `lang/en.yml`, whose text the join notification now reads from `notify_hover_text`.
- 从 `lang/en.yml` 与 `lang/zh.yml` 中移除 26 条从未被任何代码显示的条目：`gui_*` 翻页与按钮文字（页面使用 UltiTools 自带的）、
  `delete_confirm_*`（并不存在删除确认页面）、已被其他条目取代的 `send_*`（`input_content_prompt`、`mail_sent_success`、
  `error_no_item_in_hand`）、`attachment_gui_title`、`lore_subject`、`lore_items_count`、`error_no_permission`、
  `sendall_no_permission`（权限拒绝消息来自 UltiTools 自身）、`arg_number` 与 `arg_content`；并从 `lang/en.yml` 中移除
  `notify_hover_hint`，登录提醒的悬停文本现在读取 `notify_hover_text`。

- The module's own console lines on unload and on reload (Chinese sentences meaning "UltiMail
  disabled!" and "UltiMail configuration reloaded!", printed in Chinese under either `language`
  setting), and the never-consulted `mail_disabled` and `mail_reloaded` language keys that
  described them. UltiTools 6.3.0 logs one reload line per module (`Module 'UltiMail' reloaded.`)
  (UltiKits/UltiMail#20).
- The `mail-expire-days` setting in `config/mail.yml` (default `30`). It never took effect in any
  version: nothing read it, this module has no mail expiry of any kind, and a mail was always kept
  until a player deleted it, whatever the value. Mail behaviour is unchanged. Removing the setting
  is not a rejection of mail expiry: a real implementation, which among other things has to decide
  what happens to a mail whose attachment was never claimed, is tracked as a feature request in
  UltiKits/UltiMail#34. A server upgraded from an earlier version keeps the key in its `mail.yml`,
  because the framework never deletes a key from an operator's file; while it is there, the module
  logs one warning at startup and on every reload of this module (`/ul reload` or
  `/ul reload UltiMail`), naming the file and the key, and the key can simply be deleted
  (UltiKits/UltiMail#23).
- The `messages.new-mail` and `messages.mail-sent` settings in `config/mail.yml`. Neither ever took
  effect: nothing read them, so editing them never changed what a player saw. The unread-mail
  notification on join and the confirmation a sender gets after sending already take their text
  from this module's language file, entries `notify_new_mail` and `mail_sent_success` in
  `lang/<language>.yml` beside the `config` folder, so they follow the server's `language` setting
  and are customised there. Their placeholders differ from the removed keys': `notify_new_mail`
  carries `{0}` where the old key used `{COUNT}`, and `mail_sent_success` takes the receiver's
  name as `{RECEIVER}` (not `{PLAYER}`); text pasted across with the old placeholder is refused by
  the framework, which then uses the bundled text. The join notification does not yet put the
  unread count into `{0}` (UltiKits/UltiMail#24). What players see is unchanged. `messages.mail-received`, the setting
  for the message an online receiver gets when a mail arrives, is read and stays. A server upgraded
  from an earlier version keeps both removed keys in its `mail.yml`; while either is there, the
  module logs one warning for it at startup and on every reload of this module, naming the file,
  the key and the language entry to edit instead, and the key can simply be deleted
  (UltiKits/UltiMail#23).
- 移除本模块在卸载与重载时输出的"UltiMail 已禁用！"与"UltiMail 配置已重载！"控制台行，以及未被使用的
  `mail_disabled`、`mail_reloaded` 语言键。UltiTools 6.3.0 会为每个模块输出一行重载日志
  （UltiKits/UltiMail#20）。
- 移除 `config/mail.yml` 中的 `mail-expire-days` 设置项（默认 `30`）。它在任何版本中都从未生效：没有任何代码
  读取它，本模块也没有任何形式的邮件过期，无论取值为何，邮件都会一直保留到玩家自行删除为止。邮件的行为不变。
  移除该设置项并不代表否决邮件过期功能：真正的实现需要决定的事情之一，是附件从未领取的邮件过期后如何处理，
  该功能请求由 UltiKits/UltiMail#34 跟踪。从旧版本升级的服务器，其 `mail.yml` 中仍会保留该键，因为框架从不删除
  运维文件中的键；只要该键还在，本模块会在启动时以及每次重载本模块时（`/ul reload` 或
  `/ul reload UltiMail`）记录一条警告，指出文件与键名，直接删除该键即可（UltiKits/UltiMail#23）。
- 移除 `config/mail.yml` 中的 `messages.new-mail` 与 `messages.mail-sent` 设置项。二者都从未生效：没有任何代码
  读取它们，修改它们从未改变玩家看到的内容。玩家登录时的未读邮件提醒，以及发送者发出邮件后收到的确认消息，
  本来就从本模块的语言文件读取文本——`config` 文件夹旁 `lang/<语言>.yml` 中的 `notify_new_mail` 与
  `mail_sent_success` 条目——因此会跟随服务器的 `language` 设置，也应在那里修改。这两个条目的占位符与被移除的
  键不同：`notify_new_mail` 使用 `{0}`（旧键为 `{COUNT}`），`mail_sent_success` 以 `{RECEIVER}`
  （而非 `{PLAYER}`）表示收件人名称；照搬旧占位符的文本会被框架拒绝，并改用内置文本。登录提醒目前尚未把未读数量
  填入 `{0}`（UltiKits/UltiMail#24）。玩家看到的内容不变。
  同一段中的 `messages.mail-received`（在线收件人收到新邮件时看到的消息）会被读取，予以保留。从旧版本升级的
  服务器，其 `mail.yml` 中仍会保留这两个被移除的键；只要其中任一个还在，本模块会在启动时以及每次重载本模块时
  为它记录一条警告，指出文件、键名以及应改为修改的语言条目，直接删除该键即可（UltiKits/UltiMail#23）。
