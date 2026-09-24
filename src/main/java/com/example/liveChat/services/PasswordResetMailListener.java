package com.example.liveChat.services;

import com.example.liveChat.infra.mail.PasswordResetMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetMailListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetMailListener.class);

    private final PasswordResetMailSender mailSender;
    private final PasswordResetTokenCleanupService tokenCleanupService;

    public PasswordResetMailListener(PasswordResetMailSender mailSender,
                                     PasswordResetTokenCleanupService tokenCleanupService) {
        this.mailSender = mailSender;
        this.tokenCleanupService = tokenCleanupService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PasswordResetRequestedEvent event) {
        try {
            mailSender.sendPasswordReset(event.recipient(), event.resetUrl());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not send password reset e-mail; removing the unusable token ({})",
                    exception.getClass().getSimpleName());
            try {
                tokenCleanupService.removeFailedToken(event.tokenId());
            } catch (RuntimeException cleanupException) {
                LOGGER.error("Could not remove the token after password reset e-mail failure ({})",
                        cleanupException.getClass().getSimpleName());
            }
        }
    }
}
