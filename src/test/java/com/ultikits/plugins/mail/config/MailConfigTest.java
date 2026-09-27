package com.ultikits.plugins.mail.config;

import com.ultikits.ultitools.annotations.ConfigEntry;

import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MailConfig entity.
 * <p>
 * 测试邮件配置实体的各项功能。
 */
@DisplayName("MailConfig 实体测试")
class MailConfigTest {

    private MailConfig config;

    @BeforeEach
    void setUp() {
        config = new MailConfig();
    }

    /**
     * UltiKits/UltiMail#23. Three keys were declared, validated and written into every operator's
     * {@code config/mail.yml}, and read by nothing: {@code mail-expire-days} described an expiry
     * that does not exist (feature request UltiKits/UltiMail#34), and {@code messages.new-mail} /
     * {@code messages.mail-sent} duplicated text the language catalogue already supplies
     * ({@code notify_new_mail}, {@code mail_sent_success}) -- the maintainer's message-text decision
     * of 2026-09-22. All three are removed rather than wired.
     * <p>
     * Asserted over the declared surface, because that is the only place the framework learns which
     * keys to write: a {@code @ConfigEntry} carrying one of these paths would put the key back into
     * every fresh {@code mail.yml}. The written form is "it must declare exactly this surface", so it
     * fails while the keys are still declared, not merely stops compiling once they are gone.
     */
    @Nested
    @DisplayName("removed keys (UltiKits/UltiMail#23)")
    class RemovedKeyTests {

        private List<String> declaredPaths() {
            List<String> paths = new ArrayList<>();
            for (Field field : MailConfig.class.getDeclaredFields()) {
                ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
                if (entry != null) {
                    paths.add(entry.path());
                }
            }
            return paths;
        }

        @Test
        @DisplayName("declares none of mail-expire-days, messages.new-mail, messages.mail-sent; still declares their siblings")
        void declaresNoneOfTheRemovedKeys() {
            List<String> paths = declaredPaths();

            // Positive controls: the scan reads the annotations, and the one message key in the
            // same block that IS read (MailService#notifyReceiver) stays.
            assertThat(paths).contains("max-items", "notify-on-join", "messages.mail-received");
            assertThat(paths).doesNotContain(
                    "mail-expire-days", "messages.new-mail", "messages.mail-sent");
        }

        @Test
        @DisplayName("declares exactly 20 keys, three fewer than before UltiKits/UltiMail#23")
        void declaresTwentyKeys() {
            // FEATURES.md's ## Configuration and UAT-CHECKLIST.md's ultimail.config.mail-yml row both
            // count this number; 23 before the removal.
            assertThat(declaredPaths()).hasSize(20);
        }
    }

    @Nested
    @DisplayName("默认值测试")
    class DefaultValueTests {

        @Test
        @DisplayName("notifyOnJoin 默认应该为 true")
        void shouldDefaultNotifyOnJoinToTrue() {
            assertThat(config.isNotifyOnJoin()).isTrue();
        }

        @Test
        @DisplayName("notifyDelay 默认应该为 3")
        void shouldDefaultNotifyDelayTo3() {
            assertThat(config.getNotifyDelay()).isEqualTo(3);
        }

        @Test
        @DisplayName("maxSubjectLength 默认应该为 50")
        void shouldDefaultMaxSubjectLengthTo50() {
            assertThat(config.getMaxSubjectLength()).isEqualTo(50);
        }

        @Test
        @DisplayName("maxContentLength 默认应该为 500")
        void shouldDefaultMaxContentLengthTo500() {
            assertThat(config.getMaxContentLength()).isEqualTo(500);
        }

        @Test
        @DisplayName("maxItems 默认应该为 27")
        void shouldDefaultMaxItemsTo27() {
            assertThat(config.getMaxItems()).isEqualTo(27);
        }

        @Test
        @DisplayName("sendCooldown 默认应该为 10")
        void shouldDefaultSendCooldownTo10() {
            assertThat(config.getSendCooldown()).isEqualTo(10);
        }
        
        @Test
        @DisplayName("emailEnabled 默认应该为 false")
        void shouldDefaultEmailEnabledToFalse() {
            assertThat(config.isEmailEnabled()).isFalse();
        }
        
        @Test
        @DisplayName("smtpPort 默认应该为 587")
        void shouldDefaultSmtpPortTo587() {
            assertThat(config.getSmtpPort()).isEqualTo(587);
        }
        
        @Test
        @DisplayName("smtpSsl 默认应该为 false")
        void shouldDefaultSmtpSslToFalse() {
            assertThat(config.isSmtpSsl()).isFalse();
        }
        
        @Test
        @DisplayName("smtpStartTls 默认应该为 true")
        void shouldDefaultSmtpStartTlsToTrue() {
            assertThat(config.isSmtpStartTls()).isTrue();
        }
    }

    @Nested
    @DisplayName("Getter/Setter 测试")
    class GetterSetterTests {

        @Test
        @DisplayName("应该正确设置和获取 notifyOnJoin")
        void shouldSetAndGetNotifyOnJoin() {
            config.setNotifyOnJoin(false);
            assertThat(config.isNotifyOnJoin()).isFalse();

            config.setNotifyOnJoin(true);
            assertThat(config.isNotifyOnJoin()).isTrue();
        }

        @Test
        @DisplayName("应该正确设置和获取 notifyDelay")
        void shouldSetAndGetNotifyDelay() {
            config.setNotifyDelay(5);
            assertThat(config.getNotifyDelay()).isEqualTo(5);

            config.setNotifyDelay(0);
            assertThat(config.getNotifyDelay()).isEqualTo(0);
        }

        @Test
        @DisplayName("应该正确设置和获取消息模板")
        void shouldSetAndGetMessageTemplates() {
            String receivedMsg = "&e收到新邮件！";

            config.setMailReceivedMessage(receivedMsg);

            assertThat(config.getMailReceivedMessage()).isEqualTo(receivedMsg);
        }

        @Test
        @DisplayName("应该正确设置和获取限制值")
        void shouldSetAndGetLimits() {
            config.setMaxSubjectLength(100);
            config.setMaxContentLength(1000);
            config.setMaxItems(54);
            config.setSendCooldown(30);

            assertThat(config.getMaxSubjectLength()).isEqualTo(100);
            assertThat(config.getMaxContentLength()).isEqualTo(1000);
            assertThat(config.getMaxItems()).isEqualTo(54);
            assertThat(config.getSendCooldown()).isEqualTo(30);
        }
        
        @Test
        @DisplayName("应该正确设置和获取召回功能配置")
        void shouldSetAndGetRecallConfig() {
            config.setServerName("TestServer");
            config.setRecallSubject("回来吧");
            config.setRecallContent("想念你");
            
            assertThat(config.getServerName()).isEqualTo("TestServer");
            assertThat(config.getRecallSubject()).isEqualTo("回来吧");
            assertThat(config.getRecallContent()).isEqualTo("想念你");
        }
        
        @Test
        @DisplayName("应该正确设置和获取邮件服务器配置")
        void shouldSetAndGetSmtpConfig() {
            config.setSmtpHost("smtp.test.com");
            config.setSmtpPort(465);
            config.setSmtpUsername("user");
            config.setSmtpPassword("pass");
            config.setSmtpFromEmail("from@test.com");
            config.setSmtpSsl(true);
            config.setSmtpStartTls(false);
            
            assertThat(config.getSmtpHost()).isEqualTo("smtp.test.com");
            assertThat(config.getSmtpPort()).isEqualTo(465);
            assertThat(config.getSmtpUsername()).isEqualTo("user");
            assertThat(config.getSmtpPassword()).isEqualTo("pass");
            assertThat(config.getSmtpFromEmail()).isEqualTo("from@test.com");
            assertThat(config.isSmtpSsl()).isTrue();
            assertThat(config.isSmtpStartTls()).isFalse();
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("notifyDelay 可以设置为 0")
        void shouldAllowZeroNotifyDelay() {
            config.setNotifyDelay(0);
            assertThat(config.getNotifyDelay()).isEqualTo(0);
        }

        @Test
        @DisplayName("maxItems 可以设置为大值")
        void shouldAllowLargeMaxItems() {
            config.setMaxItems(54);
            assertThat(config.getMaxItems()).isEqualTo(54);
        }

        @Test
        @DisplayName("sendCooldown 可以设置为 0 (无冷却)")
        void shouldAllowZeroCooldown() {
            config.setSendCooldown(0);
            assertThat(config.getSendCooldown()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("消息占位符测试")
    class PlaceholderTests {

        @Test
        @DisplayName("收到邮件消息应该包含 {SENDER} 发件人占位符")
        void receivedMessageShouldHaveSenderPlaceholder() throws Exception {
            String message = config.getMailReceivedMessage();
            assertThat(message).contains("{SENDER}");
        }
        
        @Test
        @DisplayName("召回邮件标题应该包含 {SERVER} 占位符")
        void recallSubjectShouldHaveServerPlaceholder() throws Exception {
            String subject = config.getRecallSubject();
            assertThat(subject).contains("{SERVER}");
        }
        
        @Test
        @DisplayName("召回邮件内容应该包含 {SERVER} 和 {SENDER} 占位符")
        void recallContentShouldHavePlaceholders() throws Exception {
            String content = config.getRecallContent();
            assertThat(content).contains("{SERVER}");
            assertThat(content).contains("{SENDER}");
        }
    }
    
    @Nested
    @DisplayName("邮件服务器配置测试")
    class EmailConfigTests {
        
        @Test
        @DisplayName("默认服务器地址应该是示例地址")
        void shouldHaveExampleSmtpHost() {
            assertThat(config.getSmtpHost()).isEqualTo("smtp.example.com");
        }
        
        @Test
        @DisplayName("默认用户名和密码应该为空")
        void shouldHaveEmptyCredentials() {
            assertThat(config.getSmtpUsername()).isEmpty();
            assertThat(config.getSmtpPassword()).isEmpty();
        }
        
        @Test
        @DisplayName("召回电子邮件配置应该有正确的占位符")
        void recallEmailShouldHavePlaceholders() throws Exception {
            assertThat(config.getRecallEmailSubject()).contains("{SERVER}");
            assertThat(config.getRecallEmailContent()).contains("{PLAYER}");
            assertThat(config.getRecallEmailContent()).contains("{SERVER}");
            assertThat(config.getRecallEmailContent()).contains("{SENDER}");
        }
    }

    @Nested
    @DisplayName("Setter 测试")
    class SetterTests {

        @Test
        @DisplayName("应该正确设置 mailReceivedMessage")
        void shouldSetMailReceivedMessage() {
            config.setMailReceivedMessage("received");
            assertThat(config.getMailReceivedMessage()).isEqualTo("received");
        }

        @Test
        @DisplayName("应该正确设置 serverName")
        void shouldSetServerName() {
            config.setServerName("MyServer");
            assertThat(config.getServerName()).isEqualTo("MyServer");
        }

        @Test
        @DisplayName("应该正确设置 recallSubject")
        void shouldSetRecallSubject() {
            config.setRecallSubject("Recall Subject");
            assertThat(config.getRecallSubject()).isEqualTo("Recall Subject");
        }

        @Test
        @DisplayName("应该正确设置 recallContent")
        void shouldSetRecallContent() {
            config.setRecallContent("Come back!");
            assertThat(config.getRecallContent()).isEqualTo("Come back!");
        }

        @Test
        @DisplayName("应该正确设置 smtpFromEmail")
        void shouldSetSmtpFromEmail() {
            config.setSmtpFromEmail("from@test.com");
            assertThat(config.getSmtpFromEmail()).isEqualTo("from@test.com");
        }

        @Test
        @DisplayName("应该正确设置 recallEmailSubject")
        void shouldSetRecallEmailSubject() {
            config.setRecallEmailSubject("Email Subject");
            assertThat(config.getRecallEmailSubject()).isEqualTo("Email Subject");
        }

        @Test
        @DisplayName("应该正确设置 recallEmailContent")
        void shouldSetRecallEmailContent() {
            config.setRecallEmailContent("Email Content");
            assertThat(config.getRecallEmailContent()).isEqualTo("Email Content");
        }
    }
}
