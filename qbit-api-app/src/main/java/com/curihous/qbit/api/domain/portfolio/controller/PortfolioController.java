package com.curihous.qbit.api.domain.portfolio.controller;
                                                   
import com.curihous.qbit.api.domain.portfolio.dto.response.PortfolioOverviewResponseDto;
import com.curihous.qbit.api.domain.portfolio.dto.response.PositionResponseDto;
import com.curihous.qbit.api.domain.portfolio.dto.response.PositionWithAccountResponseDto;
import com.curihous.qbit.api.domain.portfolio.service.PortfolioOverviewService;
import com.curihous.qbit.common.dto.PaginatedResponseDto;
import com.curihous.qbit.common.util.PagingValidator;
import com.curihous.qbit.domain.order.port.TradingPort;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.infra.security.facade.UserSecurityFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포트폴리오 관리 API
 * 
 * 사용자의 현재 보유 주식 포지션을 관리합니다.
 */
@Tag(name = "Portfolio", description = "포트폴리오 관리 API입니다.")
@RestController
@RequestMapping("/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final TradingPort tradingPort;
    private final UserSecurityFacade userSecurityFacade;
    private final PortfolioOverviewService portfolioOverviewService;

    @Operation(
        summary = "보유 포지션 조회", 
        description = "현재 보유 중인 주식 포지션 목록을 조회합니다.(알파카 기반, 페이징 지원)"
    )
    @GetMapping("/positions")
    public ResponseEntity<PaginatedResponseDto<PositionResponseDto>> getPositions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(value = "asset", required = false) String asset
    ) {
        PagingValidator.validate(page, size);

        User user = userSecurityFacade.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size);
        Page<TradingPort.PositionInfo> positionsPage = tradingPort.getPositions(user, asset, pageable);
        Page<PositionResponseDto> responsePage = positionsPage.map(PositionResponseDto::from);
        PaginatedResponseDto<PositionResponseDto> response = PaginatedResponseDto.from(responsePage);
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "특정 종목 포지션 조회", 
        description = "특정 종목의 포지션 정보와 계정 정보를 조회합니다. 매도 시 최대 수량, 매수 시 최대 금액을 계산할 수 있습니다."
    )
    @GetMapping("/positions/detail")
    public ResponseEntity<PositionWithAccountResponseDto> getPositionBySymbol(
        @Parameter(description = "종목 심볼", example = "AAPL")
        @RequestParam(value = "symbol") String symbol
    ) {
        User user = userSecurityFacade.getCurrentUser();
        TradingPort.SimplePositionWithAccountInfo result = tradingPort.getPositionBySymbol(user, symbol);
        PositionWithAccountResponseDto response = PositionWithAccountResponseDto.from(result);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "포트폴리오 오버뷰 조회",
        description = "자산 그래프를 그리기 위한 포트폴리오 오버뷰를 조회합니다.\n\n" +
            "period별 자동 timeframe 맵핑:\n" +
            "- 1D (1일): 15Min 간격\n" +
            "- 1W (1주): 1H 간격\n" +
            "- 1M (1개월): 1D 간격\n" +
            "- 1A (1년): 1D 간격"
    )
    @GetMapping("/overview")
    public ResponseEntity<PortfolioOverviewResponseDto> getPortfolioOverview(
        @Parameter(description = "조회 기간 (1D=1일, 1W=1주, 1M=1개월, 1A=1년). 기본값: 1M", example = "1M")
        @RequestParam(value = "period", required = false) String period
    ) {
        User user = userSecurityFacade.getCurrentUser();
        PortfolioOverviewResponseDto response = portfolioOverviewService.getOverview(user, period);
        return ResponseEntity.ok(response);
    }

}
