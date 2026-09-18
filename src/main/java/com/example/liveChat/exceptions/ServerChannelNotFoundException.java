package com.example.liveChat.exceptions;

public class ServerChannelNotFoundException extends RuntimeException {
    public ServerChannelNotFoundException(String message) {
        super(message);
    }
}
