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
  clicked, a content prompt that is cancelled or times out, and a send the server refuses (for
  example an unknown receiver). Each of these previously discarded whatever the sender's inventory
  could not hold (UltiKits/UltiMail#27).
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
  `max-items` 的部分、内容输入被取消或超时、以及服务器拒绝发送（例如收件人不存在）。此前这三处都会
  丢弃发送者背包容纳不下的物品（UltiKits/UltiMail#27）。

### Removed

- The module's own console lines on unload and on reload (Chinese sentences meaning "UltiMail
  disabled!" and "UltiMail configuration reloaded!", printed in Chinese under either `language`
  setting), and the never-consulted `mail_disabled` and `mail_reloaded` language keys that
  described them. UltiTools 6.3.0 logs one reload line per module (`Module 'UltiMail' reloaded.`)
  (UltiKits/UltiMail#20).
- 移除本模块在卸载与重载时输出的"UltiMail 已禁用！"与"UltiMail 配置已重载！"控制台行，以及未被使用的
  `mail_disabled`、`mail_reloaded` 语言键。UltiTools 6.3.0 会为每个模块输出一行重载日志
  （UltiKits/UltiMail#20）。
