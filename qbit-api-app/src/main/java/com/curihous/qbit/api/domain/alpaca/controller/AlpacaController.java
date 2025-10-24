package com.curihous.qbit.api.domain.alpaca.controller;

import com.curihous.qbit.api.domain.alpaca.dto.response.AccountInfoResponseDto;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
import com.curihous.qbit.domain.alpaca.entity.AlpacaOAuthConnection;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.infra.alpaca.port.AlpacaTradingPort;
import com.curihous.qbit.infra.alpaca.dto.request.CryptoAgreementRequest;
import com.curihous.qbit.infra.alpaca.dto.response.AlpacaAccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * Alpaca 계정 관련 API
 * 
 * Alpaca Paper Trading 계정의 실시간 정보를 제공합니다.
 * 이 정보들은 외부 API에서 가져오며 DB에 저장되지 않습니다.
 */
@Slf4j
@Tag(name = "Alpaca Account", description = "Alpaca Paper Trading 계정 정보 API입니다.")
@RestController
@RequestMapping("/alpaca")
@RequiredArgsConstructor
public class AlpacaController {

    private final TradingPort tradingPort;
    private final UserSecurityFacade userSecurityFacade;
    private final AlpacaOAuthConnectionService alpacaOAuthConnectionService;
    private final AlpacaTradingPort alpacaTradingPort;

    @Operation(
        summary = "Alpaca 계정 정보 조회", 
        description = "Alpaca Paper Trading 계정의 실시간 자산 정보와 상태를 조회합니다."
    )
    @GetMapping("/account")
    public ResponseEntity<AccountInfoResponseDto> getAccountInfo() {
        User user = userSecurityFacade.getCurrentUser();
        TradingPort.AccountInfo accountInfo = tradingPort.getAccountInfo(user);
        return ResponseEntity.ok(AccountInfoResponseDto.from(accountInfo));
    }
    
    @Operation(summary = "암호화폐 거래 동의서 서명", description = "Alpaca 계정에 암호화폐 거래 동의서를 서명합니다.")
    @PostMapping("/crypto/agreements")
    public ResponseEntity<Void> signCryptoAgreement(HttpServletRequest request) {
        User user = userSecurityFacade.getCurrentUser();

        AlpacaOAuthConnection connection = alpacaOAuthConnectionService.getValidConnection(user.getId());
        AlpacaAccountResponse account = alpacaTradingPort.getAccount("Bearer " + connection.getAccessToken());
        
        // 클라이언트 IP 주소 가져오기
        String clientIp = getClientIpAddress(request);
        
        // crypto agreement 서명 요청 생성
        CryptoAgreementRequest agreementRequest = new CryptoAgreementRequest(
            new CryptoAgreementRequest.Agreement[] {
                new CryptoAgreementRequest.Agreement(
                    "crypto_agreement",
                    OffsetDateTime.now(),
                    clientIp
                )
            }
        );
        
        alpacaTradingPort.signCryptoAgreement("Bearer " + connection.getAccessToken(), account.id(), agreementRequest);
        
        return ResponseEntity.ok().build();
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
