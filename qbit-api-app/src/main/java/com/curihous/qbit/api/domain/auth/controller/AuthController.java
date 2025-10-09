package com.curihous.qbit.api.domain.auth.controller;

import com.curihous.qbit.api.domain.auth.dto.KakaoLoginRequest;
import com.curihous.qbit.api.domain.auth.dto.KakaoLoginResponse;
import com.curihous.qbit.infra.kakao.service.KakaoLoginService;
import com.curihous.qbit.infra.security.auth.dto.TokenResponseDto;
import com.curihous.qbit.infra.security.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Auth 관련 API입니다.")
public class AuthController {

    private final AuthService authService;
    private final KakaoLoginService kakaoLoginService;

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

    @PostMapping("/kakao/login")
    @Operation(summary = "카카오 네이티브 앱 로그인", 
            description = "Flutter SDK에서 받은 카카오 액세스 토큰으로 로그인합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 카카오 액세스 토큰")
    })
    public ResponseEntity<KakaoLoginResponse> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request,
            HttpServletResponse response) {
        
        Map<String, Object> result = kakaoLoginService.loginWithKakao(
                request.kakaoAccessToken(), 
                response
        );
        
        KakaoLoginResponse loginResponse = new KakaoLoginResponse(
                (String) result.get("accessToken"),
                (Integer) result.get("expiresIn"),
                (Boolean) result.get("isNewUser"),
                (Long) result.get("userId"),
                (String) result.get("email"),
                (String) result.get("nickname")
        );
        
        return ResponseEntity.ok(loginResponse);
    }
}
