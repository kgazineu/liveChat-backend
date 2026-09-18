package com.example.liveChat.exceptions;

public class DirectChannelNotFoundException extends RuntimeException {
    public DirectChannelNotFoundException(String message) {
        super(message);
    }
}
