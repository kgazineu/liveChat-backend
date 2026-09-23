package com.example.liveChat.infra.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpProfileUpdateMailSender implements ProfileUpdateMailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpProfileUpdateMailSender(JavaMailSender mailSender,
                                       @Value("${livechat.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendProfileUpdateConfirmation(String recipient, String confirmationUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Confirmação de atualização da conta");
        message.setText("Use o link abaixo para confirmar a atualização da sua conta. "
                + "Se você não solicitou esta alteração, ignore este e-mail.\n\n" + confirmationUrl);
        mailSender.send(message);
    }
}
