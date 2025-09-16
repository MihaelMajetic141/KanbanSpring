package com.kanban.security;

import com.kanban.model.RefreshToken;
import com.kanban.model.UserInfo;
import com.kanban.repository.UserInfoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    @Autowired
    RefreshTokenRepository refreshTokenRepository;
    @Autowired
    UserInfoRepository userInfoRepository;

    public RefreshToken createRefreshToken(String email) {
        Optional<UserInfo> userInfo = userInfoRepository.findByEmail(email);
        if (userInfo.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        Optional<RefreshToken> existingToken = findByUser(userInfo.get());
        RefreshToken refreshToken;
        if (existingToken.isPresent()) {
            refreshToken = existingToken.get();
            refreshToken.setToken(UUID.randomUUID().toString());
            refreshToken.setExpiryDate(Instant.now().plusMillis(1000L * 60 * 60 * 24 * 7));
        } else {
            refreshToken = RefreshToken.builder()
                    .userId(userInfo.get().getId())
                    .token(UUID.randomUUID().toString())
                    .expiryDate(Instant.now().plusMillis(1000L * 60 * 60 * 24 * 7))
                    .build();
        }
        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshToken> findByToken(String token){
        return refreshTokenRepository.findByToken(token);
    }
    public Optional<RefreshToken> findByUser(UserInfo user) {
        return refreshTokenRepository.findByUserId(user.getId());
    }

    @Transactional
    public Optional<RefreshToken> deleteRefreshToken(String token) {
        if (refreshTokenRepository.findByToken(token).isPresent())
            return refreshTokenRepository.deleteRefreshTokenByToken(token);
        return Optional.empty();
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (refreshTokenRepository.findByToken(token.getToken()).isPresent()) {
            if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
                refreshTokenRepository.delete(token);
                throw new RuntimeException(token.getToken() + " Refresh token has expired. Please make a new login..!");
            }
            return token;
        } else throw new RuntimeException("Refresh token not found");
    }

    public void deleteAll() {
        refreshTokenRepository.deleteAll();
    }

}