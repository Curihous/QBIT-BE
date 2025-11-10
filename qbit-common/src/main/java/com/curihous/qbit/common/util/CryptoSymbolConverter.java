package com.curihous.qbit.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

// Alpaca와 Binance 간 암호화폐 심볼 형식 변환 유틸리티
@Slf4j
public class CryptoSymbolConverter {
    
    private static final Pattern ALPACA_CRYPTO_PATTERN = Pattern.compile("^([A-Z]+)/([A-Z]+)$");
    private static final Pattern BINANCE_CRYPTO_PATTERN = Pattern.compile("^([A-Z]+)(USDT|USD|BTC|ETH|BNB|BUSD)$");
    
    private static final Map<String, String> ALPACA_TO_BINANCE_QUOTE = new HashMap<>();
    private static final Map<String, String> BINANCE_TO_ALPACA_QUOTE = new HashMap<>();
    
    static {
        // Alpaca USD → Binance USDT
        ALPACA_TO_BINANCE_QUOTE.put("USD", "USDT");
        ALPACA_TO_BINANCE_QUOTE.put("BTC", "BTC");
        ALPACA_TO_BINANCE_QUOTE.put("ETH", "ETH");
        ALPACA_TO_BINANCE_QUOTE.put("BNB", "BNB");
        ALPACA_TO_BINANCE_QUOTE.put("BUSD", "BUSD");
        
        // Binance USDT → Alpaca USD
        BINANCE_TO_ALPACA_QUOTE.put("USDT", "USD");
        BINANCE_TO_ALPACA_QUOTE.put("USD", "USD");
        BINANCE_TO_ALPACA_QUOTE.put("BTC", "BTC");
        BINANCE_TO_ALPACA_QUOTE.put("ETH", "ETH");
        BINANCE_TO_ALPACA_QUOTE.put("BNB", "BNB");
        BINANCE_TO_ALPACA_QUOTE.put("BUSD", "BUSD");
    }
    
    // assetClass 기준으로 Alpaca → Binance 변환   
    public static String convertToBinance(String alpacaSymbol, String assetClass) {
        if (alpacaSymbol == null || alpacaSymbol.isBlank()) {
            return null;
        }
        
        // 암호화폐만 변환
        if (!"crypto".equalsIgnoreCase(assetClass)) {
            // log.debug("암호화폐가 아님, 변환하지 않음: assetClass={}", assetClass);
            return null;
        }
        
        return alpacaToBinance(alpacaSymbol);
    }
    
    public static String alpacaToBinance(String alpacaSymbol) {
        if (alpacaSymbol == null || alpacaSymbol.isBlank()) {
            return null;
        }
        
        var matcher = ALPACA_CRYPTO_PATTERN.matcher(alpacaSymbol);
        if (matcher.matches()) {
            String baseAsset = matcher.group(1);
            String quoteAsset = matcher.group(2);
            String binanceQuote = ALPACA_TO_BINANCE_QUOTE.getOrDefault(quoteAsset, quoteAsset);
            
            return baseAsset + binanceQuote;
        }
        
        return null;
    }
    
    /**
     * Assets API (/v2/assets): BTC/USD 형식 (슬래시 포함)
     * Positions API (/v2/positions): BTCUSD 형식 (슬래시 없음)
     * 로 둘이 다름.. 일부 업데이트가 덜 된듯하다. 
     */
    // Alpaca Assets 형식 (BTC/USD) → Alpaca Positions 형식 (BTCUSD) 변환
    public static String alpacaAssetToPositionFormat(String alpacaAssetSymbol) {
        if (alpacaAssetSymbol == null || alpacaAssetSymbol.isBlank()) {
            return alpacaAssetSymbol;
        }
        
        var matcher = ALPACA_CRYPTO_PATTERN.matcher(alpacaAssetSymbol);
        if (matcher.matches()) {
            String baseAsset = matcher.group(1);
            String quoteAsset = matcher.group(2);
            return baseAsset + quoteAsset;
        }
        
        // 암호화폐 형식이 아니면 그대로 반환
        return alpacaAssetSymbol;
    }
    
    // Alpaca Positions 형식 (BTCUSD) → Alpaca Assets 형식 (BTC/USD) 변환
    public static String alpacaPositionToAssetFormat(String alpacaPositionSymbol) {
        if (alpacaPositionSymbol == null || alpacaPositionSymbol.isBlank()) {
            return alpacaPositionSymbol;
        }
        
        // 이미 슬래시가 있으면 그대로 반환
        if (alpacaPositionSymbol.contains("/")) {
            return alpacaPositionSymbol;
        }
        
        // 암호화폐 패턴 체크 (BTCUSD, ETHUSD 등)
        var matcher = BINANCE_CRYPTO_PATTERN.matcher(alpacaPositionSymbol);
        if (matcher.matches()) {
            String baseAsset = matcher.group(1);
            String quoteAsset = matcher.group(2);
            // USDT → USD로 변환
            String alpacaQuote = quoteAsset.equals("USDT") ? "USD" : quoteAsset;
            return baseAsset + "/" + alpacaQuote;
        }
        
        // 암호화폐 형식이 아니면 그대로 반환
        return alpacaPositionSymbol;
    }

}

