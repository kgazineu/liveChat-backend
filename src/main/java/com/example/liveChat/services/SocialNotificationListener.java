package com.example.liveChat.services;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class SocialNotificationListener {
    private final SimpMessagingTemplate messagingTemplate;

    public SocialNotificationListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(SocialNotificationEvent event) {
        event.recipientEmails().stream()
                .distinct()
                .forEach(email -> messagingTemplate.convertAndSendToUser(email, event.destination(), event.payload()));
    }
}
