package com.ultikits.plugins.mail.entity;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MailData entity.
 * <p>
 * 测试邮件数据实体的各项功能。
 */
@DisplayName("MailData 实体测试")
class MailDataTest {

    private MailData mailData;

    @BeforeEach
    void setUp() {
        mailData = new MailData();
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("默认构造函数应该初始化所有布尔字段为 false")
        void shouldInitializeBooleanFieldsToFalse() {
            MailData mail = new MailData();

            assertThat(mail.isRead()).isFalse();
            assertThat(mail.isClaimed()).isFalse();
            assertThat(mail.isCommandsExecuted()).isFalse();
            assertThat(mail.isDeletedBySender()).isFalse();
            assertThat(mail.isDeletedByReceiver()).isFalse();
        }

        @Test
        @DisplayName("默认构造函数应该设置发送时间")
        void shouldSetSentTime() {
            long before = System.currentTimeMillis();
            MailData mail = new MailData();
            long after = System.currentTimeMillis();

            assertThat(mail.getSentTime()).isBetween(before, after);
        }
    }

    @Nested
    @DisplayName("hasItems() 方法测试")
    class HasItemsTests {

        @Test
        @DisplayName("items 为 null 时应返回 false")
        void shouldReturnFalseWhenItemsIsNull() {
            mailData.setItems(null);
            
            assertThat(mailData.hasItems()).isFalse();
        }

        @Test
        @DisplayName("items 为空字符串时应返回 false")
        void shouldReturnFalseWhenItemsIsEmpty() {
            mailData.setItems("");
            
            assertThat(mailData.hasItems()).isFalse();
        }

        @Test
        @DisplayName("items 有内容时应返回 true")
        void shouldReturnTrueWhenItemsHasContent() {
            mailData.setItems("rO0ABXcEAAAAAXNyABxvcmcuYnVra2l0Lmludm..."); // Base64 encoded
            
            assertThat(mailData.hasItems()).isTrue();
        }
    }

    @Nested
    @DisplayName("hasCommands() 方法测试")
    class HasCommandsTests {

        @Test
        @DisplayName("commands 为 null 时应返回 false")
        void shouldReturnFalseWhenCommandsIsNull() {
            mailData.setCommands(null);
            
            assertThat(mailData.hasCommands()).isFalse();
        }

        @Test
        @DisplayName("commands 为空字符串时应返回 false")
        void shouldReturnFalseWhenCommandsIsEmpty() {
            mailData.setCommands("");
            
            assertThat(mailData.hasCommands()).isFalse();
        }

        @Test
        @DisplayName("commands 为空数组 [] 时应返回 false")
        void shouldReturnFalseWhenCommandsIsEmptyArray() {
            mailData.setCommands("[]");
            
            assertThat(mailData.hasCommands()).isFalse();
        }

        @Test
        @DisplayName("commands 有内容时应返回 true")
        void shouldReturnTrueWhenCommandsHasContent() {
            mailData.setCommands("[\"give %player% diamond 1\"]");
            
            assertThat(mailData.hasCommands()).isTrue();
        }

        @Test
        @DisplayName("commands 有多个命令时应返回 true")
        void shouldReturnTrueWithMultipleCommands() {
            mailData.setCommands("[\"give %player% diamond 1\",\"console:eco give %player% 100\"]");
            
            assertThat(mailData.hasCommands()).isTrue();
        }
    }

    @Nested
    @DisplayName("Getter/Setter 测试")
    class GetterSetterTests {

        @Test
        @DisplayName("应该正确设置和获取发送者信息")
        void shouldSetAndGetSenderInfo() {
            String uuid = "550e8400-e29b-41d4-a716-446655440000";
            String name = "TestSender";

            mailData.setSenderUuid(uuid);
            mailData.setSenderName(name);

            assertThat(mailData.getSenderUuid()).isEqualTo(uuid);
            assertThat(mailData.getSenderName()).isEqualTo(name);
        }

        @Test
        @DisplayName("应该正确设置和获取接收者信息")
        void shouldSetAndGetReceiverInfo() {
            String uuid = "660e8400-e29b-41d4-a716-446655440001";
            String name = "TestReceiver";

            mailData.setReceiverUuid(uuid);
            mailData.setReceiverName(name);

            assertThat(mailData.getReceiverUuid()).isEqualTo(uuid);
            assertThat(mailData.getReceiverName()).isEqualTo(name);
        }

        @Test
        @DisplayName("应该正确设置和获取邮件内容")
        void shouldSetAndGetMailContent() {
            String subject = "测试标题";
            String content = "这是邮件内容";

            mailData.setSubject(subject);
            mailData.setContent(content);

            assertThat(mailData.getSubject()).isEqualTo(subject);
            assertThat(mailData.getContent()).isEqualTo(content);
        }

        @Test
        @DisplayName("应该正确设置和获取状态标志")
        void shouldSetAndGetStatusFlags() {
            mailData.setRead(true);
            mailData.setClaimed(true);
            mailData.setCommandsExecuted(true);
            mailData.setDeletedBySender(true);
            mailData.setDeletedByReceiver(true);

            assertThat(mailData.isRead()).isTrue();
            assertThat(mailData.isClaimed()).isTrue();
            assertThat(mailData.isCommandsExecuted()).isTrue();
            assertThat(mailData.isDeletedBySender()).isTrue();
            assertThat(mailData.isDeletedByReceiver()).isTrue();
        }
    }

    @Nested
    @DisplayName("equals 和 hashCode 测试")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("相同数据的两个对象应该相等")
        void shouldBeEqualWithSameData() {
            MailData mail1 = createTestMail();
            MailData mail2 = createTestMail();

            // Note: BaseDataEntity uses ID for equals
            mail1.setId("1");
            mail2.setId("1");

            assertThat(mail1).isEqualTo(mail2);
            assertThat(mail1.hashCode()).isEqualTo(mail2.hashCode());
        }

        @Test
        @DisplayName("不同ID的两个对象不应该相等")
        void shouldNotBeEqualWithDifferentId() {
            MailData mail1 = createTestMail();
            MailData mail2 = createTestMail();

            mail1.setId("1");
            mail2.setId("2");

            assertThat(mail1).isNotEqualTo(mail2);
        }

        @Test
        @DisplayName("与null不应相等")
        void shouldNotEqualNull() {
            MailData mail = createTestMail();
            mail.setId("1");
            assertThat(mail).isNotEqualTo(null);
        }

        @Test
        @DisplayName("与不同类型不应相等")
        void shouldNotEqualDifferentType() {
            MailData mail = createTestMail();
            mail.setId("1");
            assertThat(mail).isNotEqualTo("string");
        }

        @Test
        @DisplayName("自反性 - 应该与自身相等")
        void shouldEqualSelf() {
            MailData mail = createTestMail();
            mail.setId("1");
            assertThat(mail).isEqualTo(mail);
            assertThat(mail.hashCode()).isEqualTo(mail.hashCode());
        }

        @Test
        @DisplayName("不同数据相同ID - callSuper=true包含所有字段")
        void shouldNotBeEqualWithSameIdDifferentData() {
            MailData mail1 = new MailData();
            mail1.setId("1");
            mail1.setSenderUuid("uuid-a");
            mail1.setSenderName("Alice");
            mail1.setReceiverUuid("uuid-b");
            mail1.setReceiverName("Bob");
            mail1.setSubject("Hello");
            mail1.setContent("World");

            MailData mail2 = new MailData();
            mail2.setId("1");
            mail2.setSenderUuid("uuid-c");
            mail2.setSenderName("Charlie");
            mail2.setReceiverUuid("uuid-d");
            mail2.setReceiverName("Dave");
            mail2.setSubject("Different");
            mail2.setContent("Content");

            // @EqualsAndHashCode(callSuper = true) includes ALL fields (parent + child)
            // Since child fields differ, they are NOT equal even with same ID
            assertThat(mail1).isNotEqualTo(mail2);
        }

        private MailData createTestMail() {
            MailData mail = new MailData();
            mail.setSenderUuid("uuid1");
            mail.setSenderName("sender");
            mail.setReceiverUuid("uuid2");
            mail.setReceiverName("receiver");
            mail.setSubject("subject");
            mail.setContent("content");
            return mail;
        }
    }

    @Nested
    @DisplayName("toString 方法测试")
    class ToStringTests {

        @Test
        @DisplayName("toString 应该包含类名")
        void shouldContainClassName() {
            MailData mail = new MailData();
            mail.setSenderName("TestSender");
            String str = mail.toString();
            assertThat(str).contains("MailData");
        }

        @Test
        @DisplayName("toString 应该包含字段值")
        void shouldContainFieldValues() {
            MailData mail = new MailData();
            mail.setSenderName("Alice");
            mail.setReceiverName("Bob");
            mail.setSubject("HelloSubject");
            String str = mail.toString();
            assertThat(str).contains("Alice");
            assertThat(str).contains("Bob");
            assertThat(str).contains("HelloSubject");
        }
    }

    @Nested
    @DisplayName("@Table 注解测试")
    class TableAnnotationTests {

        @Test
        @DisplayName("应该有 @Table 注解")
        void shouldHaveTableAnnotation() {
            assertThat(MailData.class.isAnnotationPresent(
                com.ultikits.ultitools.annotations.Table.class
            )).isTrue();
        }

        @Test
        @DisplayName("@Table 值应为 mail_messages")
        void shouldHaveCorrectTableName() {
            com.ultikits.ultitools.annotations.Table annotation =
                MailData.class.getAnnotation(com.ultikits.ultitools.annotations.Table.class);
            assertThat(annotation.value()).isEqualTo("mail_messages");
        }
    }

    @Nested
    @DisplayName("@Column 注解测试")
    class ColumnAnnotationTests {

        @Test
        @DisplayName("senderUuid 应该有 @Column(sender_uuid)")
        void shouldHaveSenderUuidColumn() throws Exception {
            com.ultikits.ultitools.annotations.Column col =
                MailData.class.getDeclaredField("senderUuid")
                    .getAnnotation(com.ultikits.ultitools.annotations.Column.class);
            assertThat(col.value()).isEqualTo("sender_uuid");
        }

        @Test
        @DisplayName("items 列类型应为 TEXT")
        void shouldHaveTextTypeForItems() throws Exception {
            com.ultikits.ultitools.annotations.Column col =
                MailData.class.getDeclaredField("items")
                    .getAnnotation(com.ultikits.ultitools.annotations.Column.class);
            assertThat(col.type()).isEqualTo("TEXT");
        }

        @Test
        @DisplayName("sentTime 列类型应为 BIGINT")
        void shouldHaveBigintTypeForSentTime() throws Exception {
            com.ultikits.ultitools.annotations.Column col =
                MailData.class.getDeclaredField("sentTime")
                    .getAnnotation(com.ultikits.ultitools.annotations.Column.class);
            assertThat(col.type()).isEqualTo("BIGINT");
        }

        @Test
        @DisplayName("read 列类型应为 BOOLEAN")
        void shouldHaveBooleanTypeForRead() throws Exception {
            com.ultikits.ultitools.annotations.Column col =
                MailData.class.getDeclaredField("read")
                    .getAnnotation(com.ultikits.ultitools.annotations.Column.class);
            assertThat(col.type()).isEqualTo("BOOLEAN");
        }

        @Test
        @DisplayName("commands 列类型应为 TEXT")
        void shouldHaveTextTypeForCommands() throws Exception {
            com.ultikits.ultitools.annotations.Column col =
                MailData.class.getDeclaredField("commands")
                    .getAnnotation(com.ultikits.ultitools.annotations.Column.class);
            assertThat(col.type()).isEqualTo("TEXT");
        }
    }

    @Nested
    @DisplayName("默认ID生成测试")
    class DefaultIdTests {

        @Test
        @DisplayName("默认ID应该不为null")
        void shouldHaveNonNullId() {
            MailData mail = new MailData();
            assertThat(mail.getId()).isNotNull();
        }

        @Test
        @DisplayName("默认ID应该基于时间戳")
        void shouldHaveTimestampBasedId() {
            long before = System.currentTimeMillis();
            MailData mail = new MailData();
            long after = System.currentTimeMillis();

            long id = Long.parseLong(mail.getId());
            assertThat(id).isBetween(before, after);
        }

        @Test
        @DisplayName("两个实例的ID应该不同或相等(基于时间)")
        void shouldGenerateUniqueIds() {
            MailData mail1 = new MailData();
            MailData mail2 = new MailData();

            // IDs are timestamp-based, so they should be different if created at different ms
            // or equal if created at the same ms
            assertThat(mail1.getId()).isNotNull();
            assertThat(mail2.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("继承测试")
    class InheritanceTests {

        @Test
        @DisplayName("应该继承 BaseDataEntity")
        void shouldExtendBaseDataEntity() {
            assertThat(com.ultikits.ultitools.abstracts.data.BaseDataEntity.class)
                .isAssignableFrom(MailData.class);
        }

        @Test
        @DisplayName("setId 和 getId 应该正确工作")
        void shouldSetAndGetId() {
            MailData mail = new MailData();
            mail.setId("custom-id-123");
            assertThat(mail.getId()).isEqualTo("custom-id-123");
        }
    }

    @Nested
    @DisplayName("Lombok equals/hashCode 分支覆盖测试")
    class LombokBranchCoverageTests {

        @Test
        @DisplayName("所有字段相同的两个对象应该相等")
        void fullyEqualObjects() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            assertThat(m1).isEqualTo(m2);
            assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
        }

        @Test
        @DisplayName("senderUuid不同应不相等")
        void differentSenderUuid() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setSenderUuid("different-uuid");
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("senderName不同应不相等")
        void differentSenderName() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setSenderName("Different");
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("receiverUuid不同应不相等")
        void differentReceiverUuid() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setReceiverUuid("different-uuid");
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("receiverName不同应不相等")
        void differentReceiverName() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setReceiverName("Different");
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("subject不同应不相等")
        void differentSubject() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setSubject("Different");
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("content不同应不相等")
        void differentContent() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setContent("Different");
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("items不同应不相等")
        void differentItems() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setItems("different-base64");
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("commands不同应不相等")
        void differentCommands() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setCommands("[\"different\"]");
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("sentTime不同应不相等")
        void differentSentTime() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setSentTime(999L);
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("read不同应不相等")
        void differentRead() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setRead(true);
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("claimed不同应不相等")
        void differentClaimed() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setClaimed(true);
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("commandsExecuted不同应不相等")
        void differentCommandsExecuted() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setCommandsExecuted(true);
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("deletedBySender不同应不相等")
        void differentDeletedBySender() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setDeletedBySender(true);
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("deletedByReceiver不同应不相等")
        void differentDeletedByReceiver() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setDeletedByReceiver(true);
            assertThat(m1).isNotEqualTo(m2);
        }

        @Test
        @DisplayName("一个对象senderUuid为null另一个不为null应不相等")
        void nullVsNonNullSenderUuid() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m1.setSenderUuid(null);
            assertThat(m1).isNotEqualTo(m2);
            assertThat(m2).isNotEqualTo(m1);
        }

        @Test
        @DisplayName("两个对象senderUuid都为null应相等(其他字段相同)")
        void bothNullSenderUuid() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m1.setSenderUuid(null);
            m2.setSenderUuid(null);
            assertThat(m1).isEqualTo(m2);
        }

        @Test
        @DisplayName("一个对象items为null另一个不为null应不相等")
        void nullVsNonNullItems() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m1.setItems(null);
            assertThat(m1).isNotEqualTo(m2);
            assertThat(m2).isNotEqualTo(m1);
        }

        @Test
        @DisplayName("两个对象items都为null应相等(其他字段相同)")
        void bothNullItems() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m1.setItems(null);
            m2.setItems(null);
            assertThat(m1).isEqualTo(m2);
        }

        @Test
        @DisplayName("一个对象commands为null另一个不为null应不相等")
        void nullVsNonNullCommands() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m1.setCommands(null);
            assertThat(m1).isNotEqualTo(m2);
            assertThat(m2).isNotEqualTo(m1);
        }

        @Test
        @DisplayName("两个对象commands都为null应相等(其他字段相同)")
        void bothNullCommands() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m1.setCommands(null);
            m2.setCommands(null);
            assertThat(m1).isEqualTo(m2);
        }

        @Test
        @DisplayName("所有String字段为null的两个对象应相等")
        void allNullStringsEqual() {
            MailData m1 = new MailData();
            m1.setId("id1");
            m1.setSentTime(1000L);
            MailData m2 = new MailData();
            m2.setId("id1");
            m2.setSentTime(1000L);
            assertThat(m1).isEqualTo(m2);
            assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
        }

        @Test
        @DisplayName("hashCode应该对不同字段产生不同值")
        void hashCodeDiffers() {
            MailData m1 = createFullMail("id1");
            MailData m2 = createFullMail("id1");
            m2.setSenderUuid("different");
            assertThat(m1.hashCode()).isNotEqualTo(m2.hashCode());
        }

        @Test
        @DisplayName("hashCode应该处理null字段")
        void hashCodeWithNulls() {
            MailData m = new MailData();
            m.setId("id1");
            // All string fields null - should not throw
            int hash = m.hashCode();
            assertThat(hash).isNotNull();
        }

        private MailData createFullMail(String id) {
            MailData m = new MailData();
            m.setId(id);
            m.setSenderUuid("sender-uuid");
            m.setSenderName("Sender");
            m.setReceiverUuid("receiver-uuid");
            m.setReceiverName("Receiver");
            m.setSubject("Subject");
            m.setContent("Content");
            m.setItems("items-data");
            m.setCommands("[\"cmd1\"]");
            m.setSentTime(1000L);
            m.setRead(false);
            m.setClaimed(false);
            m.setCommandsExecuted(false);
            m.setDeletedBySender(false);
            m.setDeletedByReceiver(false);
            return m;
        }
    }
}
