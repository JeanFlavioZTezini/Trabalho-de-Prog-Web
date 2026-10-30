package com.farmacia.farmaciaonline.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;

/**
 * Abre o /login como um visitante de aba anônima (sem cookie nenhum), num servidor de verdade (Tomcat)
 * e com o menu de categorias do seed. O MockMvc não pega este caso: ele não "envia" a resposta pela
 * rede, então nunca falha por "Cannot create a session after the response has been committed".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LoginAnonimoTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("visitante sem sessão abre /login com o token CSRF e recebe o cookie de sessão")
    void visitanteSemSessaoAbreOLogin() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setAccept(List.of(MediaType.TEXT_HTML));

        ResponseEntity<String> resposta = http.exchange("/login", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);

        assertThat(resposta.getStatusCode().value())
                .as("status do /login; corpo da resposta: %s", resposta.getBody())
                .isEqualTo(200);
        assertThat(resposta.getBody()).contains("<h1>Entrar</h1>").contains("name=\"_csrf\"");
        assertThat(resposta.getHeaders().get(HttpHeaders.SET_COOKIE)).anyMatch(cookie -> cookie.startsWith("JSESSIONID="));
    }
}
