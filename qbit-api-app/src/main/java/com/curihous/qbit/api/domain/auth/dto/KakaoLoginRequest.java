package com.curihous.qbit.api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 카카오 네이티브 앱 로그인 요청 DTO
 * Flutter SDK에서 받은 카카오 액세스 토큰을 전달받음
 */
public record KakaoLoginRequest(
    @NotBlank
    String kakaoAccessToken
) {
}

