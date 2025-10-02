package com.curihous.qbit.api.domain.auth.controller;

import com.curihous.qbit.api.domain.auth.dto.response.TokenResponseDto;
import com.curihous.qbit.api.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "auth", description = "Auth 관련 API입니다.")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/refresh")
    @Operation(summary = "액세스 토큰과 리프레시 토큰 재발급",
            description = "만료된 액세스 토큰과 만료되지 않은 리프레시 토큰을 통해 두 토큰 모두의 재발급을 요청합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 리프레시 토큰")
    })
    public ResponseEntity<TokenResponseDto> refreshTokens(
            @RequestHeader("Authorization") String authorizationHeader,
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        
        String accessToken = authorizationHeader.replace("Bearer ", "");
        TokenResponseDto result = authService.refreshTokens(accessToken, refreshToken, response);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자를 로그아웃하고 리프레시 토큰 쿠키를 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        authService.logout(request, response);
        return ResponseEntity.ok().build();
    }
}
