package com.curihous.qbit.api.domain.auth.controller;

import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.alpaca.dto.internal.AlpacaConnectionStatus;
import com.curihous.qbit.alpaca.service.AlpacaOAuthService;
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
@RequestMapping("/api/oauth/alpaca")
@RequiredArgsConstructor
@Tag(name = "Alpaca OAuth", description = "Alpaca OAuth 인증 API")
public class AlpacaOAuthController {

    private final AlpacaOAuthService alpacaOAuthService;

    @GetMapping("/authorize")
    @Operation(summary = "Alpaca OAuth 인증 시작", description = "Alpaca OAuth 승인 페이지로 리디렉션")
    public void authorize(HttpServletResponse response, @AuthenticationPrincipal CustomUserDetails userDetails) throws IOException {
        String authUrl = alpacaOAuthService.generateAuthUrl(userDetails.getUserId().toString());
        response.sendRedirect(authUrl);
    }

    @GetMapping("/authorize-url")
    @Operation(summary = "Alpaca OAuth 인증 URL 조회", description = "Alpaca OAuth 승인 URL을 반환 (Swagger용)")
    public ResponseEntity<String> getAuthorizeUrl(@AuthenticationPrincipal CustomUserDetails userDetails) {
        String authUrl = alpacaOAuthService.generateAuthUrl(userDetails.getUserId().toString());
        return ResponseEntity.ok(authUrl);
    }

    @GetMapping("/callback")
    @Operation(summary = "OAuth 콜백 처리", description = "Alpaca에서 리디렉션된 코드를 처리하여 토큰 저장")
    public ResponseEntity<AlpacaOAuthConnection> callback(
            @RequestParam String code,
            @RequestParam String state,
            @RequestParam(required = false) String error) {
        if (error != null) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
        Long userId = Long.parseLong(state);
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
    public ResponseEntity<AlpacaConnectionStatus> getConnectionStatus(@AuthenticationPrincipal CustomUserDetails userDetails) {
        AlpacaConnectionStatus status = alpacaOAuthService.getConnectionStatus(userDetails.getUserId());
        return ResponseEntity.ok(status);
    }
}
