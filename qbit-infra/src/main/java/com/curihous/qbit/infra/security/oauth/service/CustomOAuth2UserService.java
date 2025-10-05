package com.curihous.qbit.infra.security.oauth.service;

import com.curihous.qbit.common.exception.ErrorCode;
import com.curihous.qbit.common.exception.QbitException;
import com.curihous.qbit.infra.security.oauth.dto.OAuth2Attributes;
import com.curihous.qbit.infra.security.oauth.dto.OAuth2UserDetails;
import com.curihous.qbit.domain.user.entity.User;
import com.curihous.qbit.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // OAuth2 제공업체에서 사용자 정보 가져오기
        Map<String, Object> oAuth2UserAttributes = super.loadUser(userRequest).getAttributes();

        // registrationId 가져오기 (kakao)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // userNameAttributeName 가져오기
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();

        // 사용자 정보 DTO 생성
        OAuth2Attributes oAuth2Attributes = OAuth2Attributes.of(registrationId, oAuth2UserAttributes);

        // 신규 사용자 여부 확인 (이번 인증 흐름에서 생성되었는지 확인)
        boolean userExistedBefore = userRepository.existsByEmail(oAuth2Attributes.email());
        
        // 회원가입 및 로그인 (User를 저장하며 가져옴)
        User user = getOrSave(oAuth2Attributes);
        
        // 방금 생성된 사용자인지 판단 (이전에 없었으면 신규)
        boolean isNewUser = !userExistedBefore;
        
        // OAuth2UserDetails 반환
        return new OAuth2UserDetails(
                oAuth2UserAttributes, 
                userNameAttributeName, 
                user.getEmail(), 
                isNewUser, 
                user.getId()
        );
    }

    @Transactional
    public User getOrSave(OAuth2Attributes oAuth2Attributes) {
        String email = oAuth2Attributes.email();
        User existingUser = userRepository.findByEmail(email).orElse(null);

        if (existingUser != null) {
            // 기존 사용자 - 로그인 타입 확인
            if (!existingUser.getLoginType().equals(oAuth2Attributes.loginType())) {
                throw new QbitException(ErrorCode.EMAIL_ALREADY_REGISTERED);
            }
            
            // 기존 사용자가 비활성화 상태라면 자동으로 활성화
            if (!existingUser.isActive()) {
                existingUser.activate();
                log.info("비활성화된 사용자가 OAuth2 로그인을 통해 자동 활성화됨: email={}", email);
            }
            
            return existingUser;
        } else {
            // 카카오에서 받은 닉네임으로 새 사용자 생성
            User newUser = oAuth2Attributes.toEntity();
            return userRepository.save(newUser);
        }
    }
}
