package com.curihous.qbit.api.domain.alpaca.controller;

import com.curihous.qbit.api.domain.alpaca.dto.response.AccountInfoResponseDto;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
import com.curihous.qbit.domain.alpaca.service.AlpacaOAuthConnectionService;
import com.curihous.qbit.infra.alpaca.port.AlpacaTradingPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    
}
