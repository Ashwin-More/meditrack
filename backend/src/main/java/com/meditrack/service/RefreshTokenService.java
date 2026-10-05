package com.meditrack.service;

import com.meditrack.entity.RefreshToken;
import com.meditrack.entity.User;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(User user);
    RefreshToken verifyExpiration(RefreshToken token);
    RefreshToken findByToken(String token);
    void revokeAllForUser(Integer userId);
}
