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
            log.debug("암호화폐가 아님, 변환하지 않음: assetClass={}", assetClass);
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

}

