package com.curihous.qbit.api.domain.portfolio.controller;

import com.curihous.qbit.api.domain.portfolio.dto.response.PositionResponseDto;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 포트폴리오 관리 API
 * 
 * 사용자의 보유 주식 포지션을 관리합니다.
 * Portfolio 테이블과 연동되어 DB에 저장된 포지션 데이터를 제공합니다.
 */
@Tag(name = "Portfolio", description = "포트폴리오 관리 API입니다.")
@RestController
@RequestMapping("/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final TradingPort tradingPort;
    private final UserSecurityFacade userSecurityFacade;

    @Operation(
        summary = "보유 포지션 조회", 
        description = "현재 보유 중인 주식 포지션 목록을 조회합니다.(DB에 저장된 포지션 데이터 기반)"
    )
    @GetMapping("/positions")
    public ResponseEntity<List<PositionResponseDto>> getPositions() {
        User user = userSecurityFacade.getCurrentUser();
        List<TradingPort.PositionInfo> positions = tradingPort.getPositions(user);
        List<PositionResponseDto> response = positions.stream()
                .map(PositionResponseDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
