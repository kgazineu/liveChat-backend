package com.example.liveChat.dto;

import com.example.liveChat.models.User;

public record UserResponseDTO (String id, String name, String email) {
    public UserResponseDTO(User user) {
        this(user.getId(), user.getName(), user.getEmail());
    }

    public static UserResponseDTO forRegister(User user) {
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail());
    }
}
