package com.farmacia.farmaciaonline.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

class CarrinhoCookieTest {
    @Test
    void lerDevolveOTokenDoCookie() {
        UUID token = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CarrinhoCookie.NOME, token.toString()));

        assertThat(CarrinhoCookie.ler(request)).contains(token);
    }

    @Test
    void semCookieOuComValorInvalidoNaoHaToken() {
        MockHttpServletRequest semCookie = new MockHttpServletRequest();
        MockHttpServletRequest invalido = new MockHttpServletRequest();
        invalido.setCookies(new Cookie(CarrinhoCookie.NOME, "isto-nao-e-um-uuid"));

        assertThat(CarrinhoCookie.ler(semCookie)).isEmpty();
        assertThat(CarrinhoCookie.ler(invalido)).isEmpty();
    }

    @Test
    void gravarUsaHttpOnlySameSiteLaxEValidadeDeTrintaDias() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CarrinhoCookie.gravar(new MockHttpServletRequest(), response, UUID.randomUUID());

        String cabecalho = response.getHeader("Set-Cookie");
        assertThat(cabecalho).startsWith(CarrinhoCookie.NOME + "=");
        assertThat(cabecalho).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/");
        assertThat(cabecalho).contains("Max-Age=" + 30 * 24 * 60 * 60);
    }

    @Test
    void apagarExpiraOCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CarrinhoCookie.apagar(new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }
}
