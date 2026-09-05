package com.example.liveChat.dto;

public record UserRegisterDTO(
        String name,
        String email,
        String password
) {}