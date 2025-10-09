package com.curihous.qbit.api.domain.auth.controller;

import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.infra.alpaca.dto.internal.AlpacaConnectionStatusDto;
import com.curihous.qbit.infra.alpaca.service.AlpacaOAuthService;
import com.curihous.qbit.infra.security.util.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/auth/alpaca")
@RequiredArgsConstructor
@Tag(name = "Alpaca OAuth", description = "Alpaca OAuth 인증 API입니다.")
public class AlpacaOAuthController {

    private final AlpacaOAuthService alpacaOAuthService;

    @GetMapping("/authorize")
    @Operation(summary = "Alpaca OAuth 인증 시작", description = "Alpaca OAuth 승인 페이지로 리디렉션")
    public void authorize(HttpServletResponse response, @AuthenticationPrincipal CustomUserDetails userDetails) throws IOException {
        String authUrl = alpacaOAuthService.generateAuthUrl(userDetails.getUserId());
        response.sendRedirect(authUrl);
    }

    @GetMapping("/authorize-url")
    @Operation(summary = "Alpaca OAuth 인증 URL 조회 (Swagger용)", description = "Alpaca OAuth 승인 URL을 반환")
    public ResponseEntity<String> getAuthorizeUrl(@AuthenticationPrincipal CustomUserDetails userDetails) {
        String authUrl = alpacaOAuthService.generateAuthUrl(userDetails.getUserId());
        return ResponseEntity.ok(authUrl);
    }

    @GetMapping("/callback")
    @Operation(summary = "OAuth 콜백 처리", description = "Alpaca에서 리디렉션된 코드를 처리하여 토큰 저장")
    public ResponseEntity<AlpacaOAuthConnection> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        
        // OAuth 에러 처리
        if (error != null) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
        
        // 필수 파라미터 검증
        if (code == null || code.isEmpty()) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
        
        if (state == null || state.isEmpty()) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
        
        // 보안 강화된 상태값 검증 및 사용자 ID 추출
        Long userId = alpacaOAuthService.validateStateAndExtractUserId(state);
        
        // 토큰 교환 및 저장
        AlpacaOAuthConnection connection = alpacaOAuthService.exchangeTokenAndSave(userId, code);
        
        return ResponseEntity.ok(connection);
    }

    @PostMapping("/refresh")
    @Operation(summary = "액세스 토큰 갱신", description = "만료된 액세스 토큰을 갱신")
    public ResponseEntity<AlpacaOAuthConnection> refreshToken(@AuthenticationPrincipal CustomUserDetails userDetails) {
        AlpacaOAuthConnection connection = alpacaOAuthService.refreshToken(userDetails.getUserId());
        return ResponseEntity.ok(connection);
    }

    @DeleteMapping("/disconnect")
    @Operation(summary = "Alpaca 계정 연결 해제", description = "Alpaca OAuth 연결을 해제")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal CustomUserDetails userDetails) {
        alpacaOAuthService.disconnect(userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    @Operation(summary = "연결 상태 확인", description = "Alpaca 계정 연결 상태 확인")
    public ResponseEntity<AlpacaConnectionStatusDto> getConnectionStatus(@AuthenticationPrincipal CustomUserDetails userDetails) {
        AlpacaConnectionStatusDto status = alpacaOAuthService.getConnectionStatus(userDetails.getUserId());
        return ResponseEntity.ok(status);
    }
}
