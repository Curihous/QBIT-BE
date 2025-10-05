package com.curihous.qbit.infra.security.oauth.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

// OAuth2 로그인 사용자 정보
public record OAuth2UserDetails(
    Map<String, Object> attributes,  // 카카오 사용자 정보
    String nameAttributeKey,         // 사용자 식별 키
    String email,                    // 이메일
    boolean isNewUser,               // 신규 가입 여부
    Long userId                      // DB 유저ID
) implements OAuth2User {

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        Object nameAttribute = attributes.get(nameAttributeKey);
        return nameAttribute != null ? nameAttribute.toString() : null;
    }
}
