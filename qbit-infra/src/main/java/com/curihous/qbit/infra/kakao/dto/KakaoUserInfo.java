package com.curihous.qbit.infra.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카카오 사용자 정보 응답 DTO
 */
@Getter
@NoArgsConstructor
public class KakaoUserInfo {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;
    
    @Getter
    @NoArgsConstructor
    public static class KakaoAccount {
        @JsonProperty("email")
        private String email;
        
        @JsonProperty("profile")
        private Profile profile;
    }
    
    @Getter
    @NoArgsConstructor
    public static class Profile {
        @JsonProperty("nickname")
        private String nickname;
    }
    
    public String getEmail() {
        return kakaoAccount != null ? kakaoAccount.getEmail() : null;
    }
    
    public String getNickname() {
        return kakaoAccount != null && kakaoAccount.getProfile() != null 
            ? kakaoAccount.getProfile().getNickname() 
            : null;
    }
}

