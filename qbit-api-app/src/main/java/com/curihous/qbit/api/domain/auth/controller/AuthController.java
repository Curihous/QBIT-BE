package com.curihous.qbit.api.domain.auth.controller;

import com.curihous.qbit.api.domain.auth.dto.KakaoLoginRequest;
import com.curihous.qbit.api.domain.auth.dto.KakaoLoginResponse;
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
