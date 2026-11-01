package com.farmacia.farmaciaonline.security;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class CarrinhoCookie {
    public static final String NOME = "carrinho_token";

    private static final Duration VALIDADE = Duration.ofDays(30);

    private CarrinhoCookie() {
    }

    public static Optional<UUID> ler(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (NOME.equals(cookie.getName())) {
                try {
                    return Optional.of(UUID.fromString(cookie.getValue()));
                } catch (IllegalArgumentException invalido) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    public static void gravar(HttpServletRequest request, HttpServletResponse response, UUID token) {
        enviar(request, response, token.toString(), VALIDADE);
    }

    public static void apagar(HttpServletRequest request, HttpServletResponse response) {
        enviar(request, response, "", Duration.ZERO);
    }

    private static void enviar(HttpServletRequest request, HttpServletResponse response, String valor, Duration validade) {
        ResponseCookie cookie = ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(validade)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
