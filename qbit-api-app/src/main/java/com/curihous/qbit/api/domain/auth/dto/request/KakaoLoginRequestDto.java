package com.curihous.qbit.api.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 카카오 네이티브 앱 로그인 요청 DTO
 * 
 * 사용 API:
 * - POST /auth/kakao/login
 */
public record KakaoLoginRequestDto(
    @NotBlank
    String kakaoAccessToken
) {
}

