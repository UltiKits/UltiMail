# Releasing UltiMail

Maintainer checklist for a module release. Players and server operators do not need this file.

1. **Reference audit (UltiKits/UltiMail#51, #68).** Repeat the binary reference audit using the release
   candidate's compiled JAR and the **released** UltiTools-API JAR for the module's declared minimum.
   Include class, field and method references (including interface methods), resolve inherited members
   with their JVM descriptors, and record the artifact versions, SHA-256 hashes and any unresolved
   symbols. A metadata test or a successful Maven build alone must not be reported as that full audit.
   Then update the README section "Compatibility verification" with the result.
2. **Change log.** Move the `## [Unreleased]` entries of `CHANGELOG.md` under the new version heading and
   date.
3. **README markers.** Drop "(unreleased)" from feature headings that ship in this release (for example
   "Optional mail expiry"), and the matching "(unreleased)" section headings in `UAT-CHECKLIST.md`.

## 发布清单（中文摘要）

1. 用候选模块 JAR 与声明下限对应的**正式版**框架 JAR 重新核查类、字段和方法引用（含接口方法及继承成员，按 JVM
   描述符解析），记录版本、SHA-256 和未解析符号；元数据测试或 Maven 构建通过不能代替这项核查。结果写回 README
   的「Compatibility verification」一节。
2. 把 `CHANGELOG.md` 的 `## [Unreleased]` 条目移到新版本标题下。
3. 去掉本次发布的功能标题上的 “(unreleased)” 标记（README 与 UAT-CHECKLIST）。
