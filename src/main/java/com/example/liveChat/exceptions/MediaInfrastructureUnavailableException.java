package com.example.liveChat.exceptions;

public class MediaInfrastructureUnavailableException extends RuntimeException {
    public MediaInfrastructureUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public MediaInfrastructureUnavailableException(String message) {
        super(message);
    }
}
