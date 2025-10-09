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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    public ResponseEntity<String> callback(
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
        
        // 토큰 교환 및 저장 (백엔드에서 토큰 저장)
        alpacaOAuthService.exchangeTokenAndSave(userId, code);
        
        // 프론트엔드에 HTML 페이지 반환
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Alpaca 연동 완료</title>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <link rel="preconnect" href="https://cdn.jsdelivr.net">
                    <link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css" rel="stylesheet">
                </head>
                <body>
                    <div style="text-align: center; padding: 50px; font-family: 'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, Roboto, 'Helvetica Neue', 'Segoe UI', 'Apple SD Gothic Neo', 'Noto Sans KR', 'Malgun Gothic', 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', sans-serif;">
                        <h1 style="color: #2E7D32; font-size: 24px; font-weight: 600; margin-bottom: 16px;">Alpaca 계정 연동이 완료되었습니다!</h1>
                        <p style="color: #666; font-size: 16px; margin-bottom: 30px;">잠시 후 앱으로 돌아갑니다...</p>
                        <div style="margin-top: 30px;">
                            <div style="display: inline-block; width: 40px; height: 40px; border: 4px solid #f3f3f3; border-top: 4px solid #2196F3; border-radius: 50%; animation: spin 1s linear infinite;"></div>
                        </div>
                    </div>
                    
                    <script>
                        // 앱으로 복귀하는 로직
                        setTimeout(() => {
                            // 딥링크로 앱 직접 실행
                            window.location.href = 'qbit://alpaca/callback/success';
                            
                            // 앱이 열리지 않으면 안내 메시지
                            setTimeout(() => {
                                document.body.innerHTML = '<div style="text-align:center;padding:50px;font-family:Pretendard,-apple-system,sans-serif;"><h2 style="color:#666;">오류가 발생하여 큐빗 어플리케이션 접속이 불가합니다.</h2>/div>';
                            }, 2000);
                        }, 1500);
                    </script>
                    
                    <style>
                        @keyframes spin {
                            0% { transform: rotate(0deg); }
                            100% { transform: rotate(360deg); }
                        }
                        
                        body {
                            margin: 0;
                            padding: 0;
                            background-color: #fafafa;
                        }
                    </style>
                </body>
                </html>
                """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);
        
        return new ResponseEntity<>(html, headers, HttpStatus.OK);
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
