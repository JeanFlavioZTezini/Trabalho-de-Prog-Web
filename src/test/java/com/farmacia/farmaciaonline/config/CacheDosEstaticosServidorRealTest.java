package com.farmacia.farmaciaonline.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.service.ArmazenamentoImagem;

/**
 * Sobe o Tomcat de verdade para ver os cabeçalhos finais (o Spring Security escreve os dele no fim da resposta).
 * Antes, CSS, JS e fotos saíam com "no-store" e eram baixados de novo a cada página.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CacheDosEstaticosServidorRealTest {

    @LocalServerPort
    private int porta;

    @Value("${app.uploads.dir:uploads}")
    private String uploads;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<Void> pedir(String caminho, String... cabecalhos) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET();
        if (cabecalhos.length > 0) {
            pedido.headers(cabecalhos);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.discarding());
    }

    @Test
    @DisplayName("CSS, JS e imagens do site: guardados no navegador e só conferidos (304 quando não mudaram)")
    void estaticosDoSiteSaoConferidosEmVezDeBaixadosDeNovo() throws Exception {
        for (String caminho : new String[] {"/css/style.css", "/js/menu.js", "/images/logo.png"}) {
            HttpResponse<Void> primeira = pedir(caminho);
            assertThat(primeira.statusCode()).isEqualTo(200);
            assertThat(primeira.headers().firstValue("Cache-Control")).as(caminho)
                    .hasValueSatisfying(c -> assertThat(c).contains("no-cache").doesNotContain("no-store"));
            String modificado = primeira.headers().firstValue("Last-Modified").orElseThrow();

            assertThat(pedir(caminho, "If-Modified-Since", modificado).statusCode()).as(caminho).isEqualTo(304);
        }
    }

    @Test
    @DisplayName("foto enviada pelo admin (nome UUID, nunca sobrescrita): cache de um ano")
    void fotoEnviadaTemCacheLongo() throws Exception {
        Path pasta = ArmazenamentoImagem.pastaDe(uploads);
        Files.createDirectories(pasta);
        Path foto = pasta.resolve(UUID.randomUUID() + ".png");
        Files.write(foto, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        try {
            HttpResponse<Void> resposta = pedir(ArmazenamentoImagem.URL_BASE + foto.getFileName());
            assertThat(resposta.statusCode()).isEqualTo(200);
            assertThat(resposta.headers().firstValue("Cache-Control"))
                    .hasValueSatisfying(c -> assertThat(c).contains("max-age=31536000").doesNotContain("no-store"));
        } finally {
            Files.deleteIfExists(foto);
        }
    }

    @Test
    @DisplayName("as páginas continuam sem cache (podem ter dados da conta)")
    void paginasContinuamSemCache() throws Exception {
        assertThat(pedir("/").headers().firstValue("Cache-Control"))
                .hasValueSatisfying(c -> assertThat(c).contains("no-store"));
    }
}
