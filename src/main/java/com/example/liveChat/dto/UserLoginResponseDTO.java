package com.example.liveChat.dto;


import java.time.Instant;

public record UserLoginResponseDTO (String name, String token, Instant expiresIn){}
