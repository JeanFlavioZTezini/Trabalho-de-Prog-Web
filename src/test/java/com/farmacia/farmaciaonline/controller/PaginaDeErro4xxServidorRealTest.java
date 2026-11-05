package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;

/**
 * Sobe o Tomcat de verdade: quem escolhe o template pelo status é o resolvedor de erros do Spring Boot, que o MockMvc não
 * monta. Antes do error/4xx.html, estes endereços mostravam a "Whitelabel Error Page", em inglês e fora do layout.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PaginaDeErro4xxServidorRealTest {

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    @ParameterizedTest(name = "{0} → {1} no layout do site")
    @CsvSource({
            "/produtos?ordenacao=QUALQUER, 400",
            "/produtos?precoMin=abc, 400",
            "/produtos/abc, 400",
            "/carrinho/itens, 405"
    })
    void erro4xxSaiNoLayoutEmPortugues(String caminho, int status) throws Exception {
        HttpResponse<String> resposta = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Accept", "text/html").GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(resposta.statusCode()).isEqualTo(status);
        assertThat(resposta.body()).contains("<h1>Não foi possível abrir a página</h1>")
                .contains("<main id=\"conteudo\"")
                .doesNotContain("Whitelabel").doesNotContain("Exception");
    }
}
