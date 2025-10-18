package com.curihous.qbit.api.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 구글 네이티브 앱 로그인 요청 DTO
 * 
 * 사용 API:
 * - POST /auth/google/login
 */
public record GoogleLoginRequestDto(
    @NotBlank
    String googleIdToken
) {
}

