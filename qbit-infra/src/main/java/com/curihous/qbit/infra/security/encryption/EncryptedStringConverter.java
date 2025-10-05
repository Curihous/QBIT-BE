package com.curihous.qbit.infra.security.encryption;

import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.common.exception.ErrorCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Converter
@Component
@RequiredArgsConstructor
@Slf4j
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final EncryptionService encryptionService;

    // 엔티티 속성을 데이터베이스 컬럼으로 변환 (암호화)
    @Override
    public String convertToDatabaseColumn(String attribute) {
        try {
            if (attribute == null || attribute.isEmpty()) {
                return attribute;
            }
            return encryptionService.encrypt(attribute);
        } catch (Exception e) {
            log.error("데이터베이스 암호화 실패", e);
            throw new QbitException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 데이터베이스 컬럼을 엔티티 속성으로 변환 (복호화)
    @Override
    public String convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isEmpty()) {
                return dbData;
            }
            return encryptionService.decrypt(dbData);
        } catch (Exception e) {
            log.error("데이터베이스 복호화 실패", e);
            throw new QbitException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
