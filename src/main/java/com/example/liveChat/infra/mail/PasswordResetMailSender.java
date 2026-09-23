package com.example.liveChat.infra.mail;

public interface PasswordResetMailSender {
    void sendPasswordReset(String recipient, String resetUrl);
}
