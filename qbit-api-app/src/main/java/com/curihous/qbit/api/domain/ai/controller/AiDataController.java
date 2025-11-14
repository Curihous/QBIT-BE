package com.curihous.qbit.api.domain.ai.controller;

import com.curihous.qbit.api.domain.ai.dto.response.ReportTradeCycleResponseDto;
import com.curihous.qbit.api.domain.ai.service.AiDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/trade-cycles")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI 서버용 데이터 제공 API")
public class AiDataController {
    
    private final AiDataService aiDataService;
    
    @GetMapping("/{tradeCycleId}")
    @Operation(summary = "AI 서버용 TradeCycle 데이터 조회", 
               description = "AI 서버가 특정 TradeCycle을 분석하기 위한 모든 데이터를 반환합니다. 인증 없이 접근 가능합니다.")
    public ResponseEntity<ReportTradeCycleResponseDto> getTradeCycleForAi(
        @PathVariable Long tradeCycleId
    ) {
        log.info("AI용 TradeCycle 데이터 조회 요청: tradeCycleId={}", tradeCycleId);   
        ReportTradeCycleResponseDto response = aiDataService.getTradeCycleForAi(tradeCycleId);     
        return ResponseEntity.ok(response);
    }
}

