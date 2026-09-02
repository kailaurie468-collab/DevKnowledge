package com.devknowledge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 开发者通知服务。
 * 邮件发送属于阻塞 IO，统一放到 boundedElastic，且发送失败不能影响用户主请求。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.notifications.developer-email:}")
    private String developerEmail;

    @Value("${spring.mail.from:${spring.mail.username:}}")
    private String fromEmail;

    public NotificationService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * 异步发送开发者邮件。未配置收件人或 SMTP 时仅记录状态，不阻断业务流程。
     */
    public void sendAsync(String subject, String body) {
        if (developerEmail == null || developerEmail.isBlank()) {
            log.debug("未配置开发者邮箱，跳过通知: subject={}", subject);
            return;
        }

        Mono.fromRunnable(() -> send(subject, body))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> {
                        },
                        error -> log.warn("开发者邮件发送失败: subject={}, reason={}",
                                subject, error.getMessage()));
    }

    private void send(String subject, String body) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("未配置 spring.mail.host，跳过开发者邮件: subject={}", subject);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(developerEmail);
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail);
        }
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
