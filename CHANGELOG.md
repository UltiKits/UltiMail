# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

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
  and are customised there. What players see is unchanged. `messages.mail-received`, the setting
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
  `mail_sent_success` 条目——因此会跟随服务器的 `language` 设置，也应在那里修改。玩家看到的内容不变。
  同一段中的 `messages.mail-received`（在线收件人收到新邮件时看到的消息）会被读取，予以保留。从旧版本升级的
  服务器，其 `mail.yml` 中仍会保留这两个被移除的键；只要其中任一个还在，本模块会在启动时以及每次重载本模块时
  为它记录一条警告，指出文件、键名以及应改为修改的语言条目，直接删除该键即可（UltiKits/UltiMail#23）。
