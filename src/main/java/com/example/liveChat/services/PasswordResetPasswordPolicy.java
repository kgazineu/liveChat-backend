package com.example.liveChat.services;

import com.example.liveChat.exceptions.InvalidRequestException;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetPasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    public void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new InvalidRequestException("Password must be between 8 and 72 characters");
        }
    }
}
