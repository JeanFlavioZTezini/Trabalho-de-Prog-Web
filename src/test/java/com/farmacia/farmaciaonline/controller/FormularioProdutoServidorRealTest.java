package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;

/**
 * Sobe o Tomcat de verdade: o limite de partes de um multipart (server.tomcat.max-part-count, 50 por padrão no
 * Spring Boot 3.5) é do contêiner, e o MockMvc não passa por ele. Cada campo do formulário conta como uma parte, e o
 * formulário de produto passa de 50 só com as 4 linhas de apresentação. O nome vai em branco: a validação barra e nada
 * é gravado no banco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FormularioProdutoServidorRealTest {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
    private static final String FRONTEIRA = "----limiteDePartes";

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> get(String caminho) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(caminho)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String token(String caminho) throws Exception {
        Matcher m = CSRF.matcher(get(caminho).body());
        assertThat(m.find()).as("token CSRF em " + caminho).isTrue();
        return m.group(1);
    }

    private URI uri(String caminho) {
        return URI.create("http://localhost:" + porta + caminho);
    }

    private void entrarComoAdmin() throws Exception {
        String corpo = "_csrf=" + URLEncoder.encode(token("/login"), StandardCharsets.UTF_8)
                + "&identificador=admin%40farma.com&senha=Farma%40123";
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(uri("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(login.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/admin"));
    }

    private static void parte(StringBuilder corpo, String nome, String valor) {
        corpo.append("--").append(FRONTEIRA).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(nome).append("\"\r\n\r\n")
                .append(valor).append("\r\n");
    }

    @Test
    @DisplayName("servidor real: salvar produto com 60 partes chega ao controller, sem o aviso de envio grande demais")
    void formularioComMaisDe50PartesChegaAoController() throws Exception {
        entrarComoAdmin();
        StringBuilder corpo = new StringBuilder();
        parte(corpo, "_csrf", token("/admin/produtos/15/editar"));
        parte(corpo, "nome", "");
        for (int i = 0; i < 4; i++) {
            for (String campo : new String[] {"id", "versao", "descricao", "sku", "ean", "preco", "estoque",
                    "limitePorPedido", "ativa", "_ativa"}) {
                parte(corpo, "apresentacoes[" + i + "]." + campo, "");
            }
        }
        for (int i = 0; i < 18; i++) {
            parte(corpo, "campoExtra" + i, "x");
        }
        corpo.append("--").append(FRONTEIRA).append("--\r\n");

        HttpResponse<String> resposta = http.send(HttpRequest.newBuilder(uri("/admin/produtos/15"))
                .header("Content-Type", "multipart/form-data; boundary=" + FRONTEIRA)
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString(), StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(resposta.statusCode()).as("formulário reaberto com o erro de validação, sem redirecionar")
                .isEqualTo(200);
        assertThat(resposta.body()).contains("Informe o nome.").doesNotContain("Envio grande demais");
    }
}
