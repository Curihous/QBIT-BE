package com.curihous.qbit.api.domain.auth.controller;

import com.curihous.qbit.api.domain.auth.dto.request.GoogleLoginRequestDto;
import com.curihous.qbit.api.domain.auth.dto.request.KakaoLoginRequestDto;
import com.curihous.qbit.api.domain.auth.dto.response.GoogleLoginResponseDto;
import com.curihous.qbit.api.domain.auth.dto.response.KakaoLoginResponseDto;
import com.curihous.qbit.infra.google.service.GoogleLoginService;
import com.curihous.qbit.infra.kakao.service.KakaoLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    private final KakaoLoginService kakaoLoginService;
    private final GoogleLoginService googleLoginService;

    @PostMapping("/kakao/login")
    @Operation(summary = "카카오 네이티브 앱 로그인", 
            description = "Flutter SDK에서 받은 카카오 액세스 토큰으로 로그인합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 카카오 액세스 토큰")
    })
    public ResponseEntity<KakaoLoginResponseDto> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequestDto request,
            HttpServletResponse response) {
        
        Map<String, Object> result = kakaoLoginService.loginWithKakao(
                request.kakaoAccessToken(), 
                response
        );
        
        KakaoLoginResponseDto loginResponse = new KakaoLoginResponseDto(
                (String) result.get("accessToken"),
                (Integer) result.get("expiresIn"),
                (Boolean) result.get("isNewUser"),
                (Long) result.get("userId"),
                (String) result.get("email"),
                (String) result.get("nickname")
        );
        
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/google/login")
    @Operation(summary = "구글 네이티브 앱 로그인", 
            description = "Flutter SDK에서 받은 구글 ID 토큰으로 로그인합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 구글 ID 토큰")
    })
    public ResponseEntity<GoogleLoginResponseDto> googleLogin(
            @Valid @RequestBody GoogleLoginRequestDto request,
            HttpServletResponse response) {
        
        Map<String, Object> result = googleLoginService.loginWithGoogle(
                request.googleIdToken(), 
                response
        );
        
        GoogleLoginResponseDto loginResponse = new GoogleLoginResponseDto(
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
