package com.curihous.qbit.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LoginType {
    KAKAO("kakao", "카카오"),
    GOOGLE("google", "구글");

    private final String provider;
    private final String description;

    public static LoginType from(String provider) {
        for (LoginType loginType : values()) {
            if (loginType.provider.equals(provider)) {
                return loginType;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 로그인 타입입니다: " + provider);
    }
}
