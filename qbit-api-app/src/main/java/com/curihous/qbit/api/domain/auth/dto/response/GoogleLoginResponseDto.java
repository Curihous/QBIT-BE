package com.curihous.qbit.api.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 구글 네이티브 앱 로그인 응답 DTO
 * 
 * 사용 API:
 * - POST /auth/google/login
 */
public record GoogleLoginResponseDto(
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

