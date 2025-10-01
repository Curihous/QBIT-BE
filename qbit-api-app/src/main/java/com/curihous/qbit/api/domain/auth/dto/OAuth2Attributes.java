package com.curihous.qbit.api.domain.auth.dto;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.domain.user.entity.LoginType;
import com.curihous.qbit.domain.user.entity.User;

import java.util.Map;

// OAuth2 제공업체에서 받은 사용자 정보
public record OAuth2Attributes(
    String email,        // 이메일
    String userName,     // 사용자명
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
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
        
        return new OAuth2Attributes(
                (String) kakaoAccount.get("email"),
                (String) profile.get("nickname"),
                "kakao",
                LoginType.KAKAO,
                false
        );
    }

    // User 엔티티로 변환
    public User toEntity(String nickname) {
        return User.builder()
                .email(this.email)
                .nickname(nickname)
                .userName(this.userName)
                .provider(this.provider)
                .loginType(this.loginType)
                .build();
    }

    // 신규 가입 여부 변경
    public OAuth2Attributes withNewUser(boolean isNewUser) {
        return new OAuth2Attributes(this.email, this.userName, this.provider, this.loginType, isNewUser);
    }
}
