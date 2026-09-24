package com.example.liveChat.services;

import com.example.liveChat.repositories.PasswordResetTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetTokenCleanupService {
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public PasswordResetTokenCleanupService(PasswordResetTokenRepository passwordResetTokenRepository) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeFailedToken(Long tokenId) {
        passwordResetTokenRepository.deleteById(tokenId);
        passwordResetTokenRepository.flush();
    }
}
