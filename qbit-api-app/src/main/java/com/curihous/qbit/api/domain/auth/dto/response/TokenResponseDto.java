package com.curihous.qbit.api.domain.auth.dto.response;

public record TokenResponseDto(
    String accessToken,
    long expiresIn
) {}
