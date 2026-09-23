package com.example.liveChat.infra.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpPasswordResetMailSender implements PasswordResetMailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetMailSender(JavaMailSender mailSender,
                                       @Value("${livechat.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendPasswordReset(String recipient, String resetUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Recuperação de senha");
        message.setText("Use o link abaixo para definir uma nova senha. Se você não solicitou a recuperação, ignore este e-mail.\n\n"
                + resetUrl);
        mailSender.send(message);
    }
}
