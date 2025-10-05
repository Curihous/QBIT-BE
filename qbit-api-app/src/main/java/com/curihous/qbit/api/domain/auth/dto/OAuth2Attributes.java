package com.curihous.qbit.api.domain.auth.dto;

import com.curihous.qbit.api.domain.auth.exception.MissingOAuth2AttributeException;
import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.user.entity.LoginType;
import com.curihous.qbit.domain.user.entity.User;

import java.util.Map;

// OAuth2 제공업체에서 받은 사용자 정보
public record OAuth2Attributes(
    String email,        // 이메일
    String nickname,     // 닉네임
    String provider,     // 제공업체명
    LoginType loginType, // 로그인 타입
    boolean isNewUser    // 신규 가입 여부
) {
    // 제공업체별로 OAuth2 정보 파싱
    // TODO: 추후 구글 추가
    public static OAuth2Attributes of(String registrationId, Map<String, Object> attributes) {
        if ("kakao".equals(registrationId)) {
            return ofKakao(attributes);
        }
        throw new QbitException(ErrorCode.ILLEGAL_REGISTRATION_ID);
    }

    // 카카오 사용자 정보 파싱
    private static OAuth2Attributes ofKakao(Map<String, Object> attributes) {
        // kakao_account 필드 검증
        Object kakaoAccountObj = attributes.get("kakao_account");
        if (kakaoAccountObj == null) {
            throw new MissingOAuth2AttributeException(ErrorCode.OAUTH2_ATTRIBUTE_MISSING);
        }
        
        Map<String, Object> kakaoAccount = (Map<String, Object>) kakaoAccountObj;
        
        // profile 필드 검증
        Object profileObj = kakaoAccount.get("profile");
        if (profileObj == null) {
            throw new MissingOAuth2AttributeException(ErrorCode.OAUTH2_ATTRIBUTE_MISSING);
        }
        
        Map<String, Object> profile = (Map<String, Object>) profileObj;
        
        // email 필드 검증
        String email = (String) kakaoAccount.get("email");
        if (email == null || email.trim().isEmpty()) {
            throw new MissingOAuth2AttributeException(ErrorCode.OAUTH2_ATTRIBUTE_MISSING);
        }
        
        // nickname 필드 검증
        // TODO: 추후 로직 변경 가능성 있음
        String nickname = (String) profile.get("nickname");
        if (nickname == null || nickname.trim().isEmpty()) {
            // 이메일에서 @ 앞부분을 닉네임으로 사용
            nickname = email.split("@")[0];
        }
        
        return new OAuth2Attributes(
                email,
                nickname,
                "kakao",
                LoginType.KAKAO,
                false
        );
    }

    // User 엔티티로 변환
    public User toEntity() {
        return User.builder()
                .email(this.email)
                .nickname(this.nickname)
                .provider(this.provider)
                .loginType(this.loginType)
                .build();
    }

    // 신규 가입 여부 변경
    public OAuth2Attributes withNewUser(boolean isNewUser) {
        return new OAuth2Attributes(this.email, this.nickname, this.provider, this.loginType, isNewUser);
    }
}
