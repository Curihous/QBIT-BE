package com.curihous.qbit.domain.stock.entity;

import com.curihous.qbit.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long id;

    // === Alpaca Assets API 응답 구조와 동일하게 ===
    
    @Column(name = "symbol", nullable = false, unique = true)
    private String symbol;

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Column(name = "exchange")
    private String exchange;

    @Column(name = "asset_class")
    private String assetClass;

    @Column(name = "status")
    private String status;

    @Column(name = "tradable")
    private Boolean tradable;

    @Column(name = "fractionable")
    private Boolean fractionable;

    @Column(name = "min_order_size")
    private String minOrderSize;

    @Column(name = "min_trade_increment")
    private String minTradeIncrement;

    @Column(name = "price_increment")
    private String priceIncrement;

    // === 기업 도메인 (Clearbit 로고 API용)===
    @Column(name = "company_domain")
    private String companyDomain;
    
    // === Binance 심볼 (차트 데이터용) ===
    @Column(name = "binance_symbol")
    private String binanceSymbol;

    // AlpacaAssetResponse로부터 Stock 생성
    @Builder
    public Stock(String symbol, String stockName, String exchange, String assetClass, String status,
                 Boolean tradable, Boolean fractionable,
                 String minOrderSize, String minTradeIncrement, String priceIncrement, String companyDomain,
                 String binanceSymbol) {
        this.symbol = symbol;
        this.stockName = stockName;
        this.exchange = exchange;
        this.assetClass = assetClass;
        this.status = status;
        this.tradable = tradable;
        this.fractionable = fractionable;
        this.minOrderSize = minOrderSize;
        this.minTradeIncrement = minTradeIncrement;
        this.priceIncrement = priceIncrement;
        this.companyDomain = companyDomain;
        this.binanceSymbol = binanceSymbol;
    }

    // 종목 정보 업데이트 (배치 작업용)
    public void updateFromAlpaca(String stockName, String exchange, String assetClass, 
                                 String status, Boolean tradable, Boolean fractionable,
                                 String minOrderSize, String minTradeIncrement, String priceIncrement) {
        this.stockName = stockName;
        this.exchange = exchange;
        this.assetClass = assetClass;
        this.status = status;
        this.tradable = tradable;
        this.fractionable = fractionable;
        this.minOrderSize = minOrderSize;
        this.minTradeIncrement = minTradeIncrement;
        this.priceIncrement = priceIncrement;
    }

    // 도메인 설정 (Clearbit 로고용)
    public void setCompanyDomain(String companyDomain) {
        this.companyDomain = companyDomain;
    }
    
    // Binance 심볼 설정 (차트 데이터용)
    public void setBinanceSymbol(String binanceSymbol) {
        this.binanceSymbol = binanceSymbol;
    }

    // Clearbit 로고 URL 반환
    public String getLogoUrl() {
        if (companyDomain == null || companyDomain.isBlank()) {
            return null; // 도메인이 없으면 null 반환  -> 프론트에서 기본 로고 처리
        }
        return "https://logo.clearbit.com/" + companyDomain;
    }

    // Alpaca API name 필드 기반 companyDomain 생성
    public static String generateDomainFromName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return null;
        }
        
        String original = companyName;
        
        // 1. 주식 타입 제거 (Common Stock, Ordinary Shares, Preferred Stock, Class A/B 등)
        String cleaned = companyName
                .replaceAll("(?i)\\s+Common\\s+Stock", "")
                .replaceAll("(?i)\\s+Ordinary\\s+Shares", "")
                .replaceAll("(?i)\\s+Preferred\\s+Stock", "")
                .replaceAll("(?i)\\s+Class\\s+[A-Z]", "")
                .trim();
        
        // 2. 회사 접미사 제거 (Inc., Corp., Ltd., LLC, Co., Company 등)
        cleaned = cleaned
                .replaceAll("(?i),\\s*Inc\\.?$", "")
                .replaceAll("(?i)\\s+Inc\\.?$", "")
                .replaceAll("(?i)\\s+Corp\\.?$", "")
                .replaceAll("(?i)\\s+Corporation$", "")
                .replaceAll("(?i)\\s+Ltd\\.?$", "")
                .replaceAll("(?i)\\s+Limited$", "")
                .replaceAll("(?i)\\s+LLC$", "")
                .replaceAll("(?i)\\s+L\\.L\\.C\\.?$", "")
                .replaceAll("(?i)\\s+Co\\.?$", "")
                .replaceAll("(?i)\\s+Company$", "")
                .replaceAll("(?i)\\s+Group$", "")
                .replaceAll("(?i)\\s+Plc$", "")
                .replaceAll("(?i)\\s+PLC$", "")
                .replaceAll("(?i)\\s+AG$", "")
                .trim();
        
        // 3. 특수문자 제거
        cleaned = cleaned.replaceAll("[,.()'\"&]", "").trim();
        
        // 4. 첫 번째 단어만 추출
        String[] words = cleaned.split("\\s+");
        String firstWord = words.length > 0 && !words[0].isEmpty() ? words[0] : cleaned;
        
        // 5. 소문자 변환 + .com 추가
        String result = firstWord.toLowerCase() + ".com";
        
        // 디버그 로그 
        if (!original.equals(companyName)) {
            System.out.println("[DEBUG] Domain 생성: " + original + " → " + result);
        }
        
        return result;
    }
}
