package com.example.liveChat.services;

public record PasswordResetRequestedEvent(String recipient, String resetUrl, Long tokenId) {
}
