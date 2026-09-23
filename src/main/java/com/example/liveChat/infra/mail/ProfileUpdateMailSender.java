package com.example.liveChat.infra.mail;

public interface ProfileUpdateMailSender {
    void sendProfileUpdateConfirmation(String recipient, String confirmationUrl);
}
