package com.curihous.qbit.api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 카카오 네이티브 앱 로그인 응답 DTO
 */
public record KakaoLoginResponse(
    @Schema(description = "JWT 액세스 토큰")
    String accessToken,
    
    @Schema(description = "토큰 만료 시간(초)")
    int expiresIn,
    
    @Schema(description = "신규 가입 여부")
    boolean isNewUser,
    
    @Schema(description = "사용자 ID")
    Long userId,
    
    @Schema(description = "사용자 이메일")
    String email,
    
    @Schema(description = "사용자 닉네임")
    String nickname
) {
}

