package com.example.warehouse.util;

import java.util.Date;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.warehouse.security.SecurityConstants;

public class TokenUtil {

    public static String generateToken() {
        String token = JWT.create()
                .withSubject("test")
                .withClaim("userId", "testUser123")
                .withArrayClaim("roles", new String[] { "PARTNER" })
                .withExpiresAt(new Date(System.currentTimeMillis() + SecurityConstants.TOKEN_EXPIRATION))
                .sign(Algorithm.HMAC512(SecurityConstants.SECRET_KEY));
        return token;
    }
}
