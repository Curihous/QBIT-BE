package com.curihous.qbit.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 시간대 변환 유틸리티
 * Binance API는 UTC 시간을 사용하므로, 한국 시간(UTC+9)과 변환이 필요
 */
public class TimeZoneConverter {
    
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    
    // KST 타임스탬프를 UTC 타임스탬프로 변환
    public static long kstToUtc(long kstTimestampMillis) {
        ZonedDateTime kstDateTime = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(kstTimestampMillis), 
            KST_ZONE
        );
        return kstDateTime.withZoneSameInstant(UTC_ZONE).toInstant().toEpochMilli();
    }
    
    // UTC 타임스탬프를 KST 타임스탬프로 변환
    public static long utcToKst(long utcTimestampMillis) {
        ZonedDateTime utcDateTime = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(utcTimestampMillis), 
            UTC_ZONE
        );
        return utcDateTime.withZoneSameInstant(KST_ZONE).toInstant().toEpochMilli();
    }
    
    // KST 타임스탬프를 UTC 타임스탬프로 변환 (null 허용)
    public static Long kstToUtc(Long kstTimestampMillis) {
        if (kstTimestampMillis == null) {
            return null;
        }
        return kstToUtc(kstTimestampMillis.longValue());
    }
    
    // UTC 타임스탬프를 KST 타임스탬프로 변환 (null 허용)
    public static Long utcToKst(Long utcTimestampMillis) {
        if (utcTimestampMillis == null) {
            return null;
        }
        return utcToKst(utcTimestampMillis.longValue());
    }
}

