package com.curihous.qbit.api.domain.auth.controller;

import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.exception.ErrorCode;
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
                        // 웹 브릿지 패턴: HTTP URL을 거쳐서 딥링크 시도
                        window.location.href = 'https://api.qbit.o-r.kr/auth/alpaca/redirect?success=true';
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

    @DeleteMapping("/disconnect")
    @Operation(summary = "Alpaca 계정 연결 해제", description = "Alpaca OAuth 연결을 해제")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal CustomUserDetails userDetails) {
        alpacaOAuthService.disconnect(userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/redirect")
    @Operation(summary = "웹 브릿지 페이지", description = "HTTP URL을 통해 딥링크를 시도하는 브릿지 페이지")
    public ResponseEntity<String> redirect(@RequestParam(required = false) String success) {
        if (!"true".equals(success)) {
            throw new QbitException(ErrorCode.OAUTH2_LOGIN_FAILED);
        }
        
        String redirectHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>앱으로 이동</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body>
                    <script>
                        // 딥링크로 앱 실행
                        window.location.href = 'qbit://auth/alpaca/callback?success=true';
                        
                        // 앱이 열리지 않으면 오류 메시지
                        setTimeout(() => {
                            document.body.innerHTML = `
                                <div style="text-align:center;padding:50px;font-family:Pretendard,-apple-system,sans-serif;max-width:600px;margin:0 auto;">
                                    <div style="margin-bottom:30px;">
                                        <svg width="80" height="80" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                            <circle cx="12" cy="12" r="10" stroke="#d32f2f" stroke-width="2"/>
                                            <path d="M12 8v4M12 16h.01" stroke="#d32f2f" stroke-width="2" stroke-linecap="round"/>
                                        </svg>
                                    </div>
                                    <h2 style="color:#d32f2f;font-size:24px;font-weight:600;margin-bottom:16px;">앱 실행 실패</h2>
                                    <p style="color:#666;font-size:16px;line-height:1.6;margin-bottom:30px;">
                                        큐빗 앱을 실행할 수 없습니다.<br>
                                    </p>
                                    <button onclick="window.location.href='qbit://auth/alpaca/callback?success=true'" 
                                            style="background:#2196F3;color:white;border:none;border-radius:8px;padding:14px 32px;font-size:16px;font-weight:600;cursor:pointer;font-family:inherit;">
                                        앱 실행 재시도
                                    </button>
                                </div>
                            `;
                        }, 2000);
                    </script>
                </body>
                </html>
                """;
        
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(redirectHtml);
    }
}
