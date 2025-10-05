package com.curihous.qbit.infra.security.auth.dto;

public record TokenResponseDto(
    String accessToken,
    long expiresIn
) {}
