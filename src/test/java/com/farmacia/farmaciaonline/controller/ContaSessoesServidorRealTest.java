package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/**
 * Sobe o Tomcat de verdade: no MockMvc não há contêiner de servlet, então o evento de troca de id da sessão (que mexe no
 * registro de sessões) nunca dispara. Aqui cada "navegador" é um HttpClient com o próprio jar de cookies.
 * Não é @Transactional (o servidor roda em outra thread): cria a própria conta e apaga no fim.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ContaSessoesServidorRealTest {

    private static final String SENHA = "Farma@123";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @LocalServerPort
    private int porta;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder encoder;

    private String email;
    private Long usuarioId;

    @BeforeEach
    void criarConta() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        email = "sessoes-" + sufixo + "@teste.com";
        long cpf = 10_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 80_000_000_000L);
        Usuario usuario = usuarios.save(Usuario.builder()
                .nome("Pessoa Sessoes").cpf(String.valueOf(cpf)).email(email)
                .dataNascimento(LocalDate.of(1990, 1, 1)).genero(Genero.FEMININO)
                .senhaHash(encoder.encode(SENHA)).perfil(Perfil.CLIENTE).status(StatusUsuario.ATIVA)
                .termosVersao("1.0").termosAceitosEm(Instant.now()).emailConfirmadoEm(Instant.now()).build());
        usuarioId = usuario.getId();
    }

    @AfterEach
    void apagarConta() {
        usuarios.deleteById(usuarioId);
    }

    private class Navegador {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER).build();

        HttpResponse<String> get(String caminho) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        HttpResponse<String> post(String caminho, String... paresNomeValor) throws Exception {
            String token = token(caminho.equals("/login") ? "/login" : "/conta/senha");
            StringBuilder corpo = new StringBuilder("_csrf=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
            for (int i = 0; i < paresNomeValor.length; i += 2) {
                corpo.append('&').append(paresNomeValor[i]).append('=')
                        .append(URLEncoder.encode(paresNomeValor[i + 1], StandardCharsets.UTF_8));
            }
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo.toString())).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        private String token(String paginaComFormulario) throws Exception {
            Matcher m = CSRF.matcher(get(paginaComFormulario).body());
            assertThat(m.find()).as("token CSRF em " + paginaComFormulario).isTrue();
            return m.group(1);
        }

        void entrar() throws Exception {
            assertThat(post("/login", "identificador", email, "senha", SENHA).headers().firstValue("Location"))
                    .hasValueSatisfying(l -> assertThat(l).doesNotContain("erro"));
        }
    }

    @Test
    @DisplayName("servidor real: trocar a senha em um navegador derruba o outro e mantém o atual")
    void trocarSenhaDerrubaOutroNavegador() throws Exception {
        Navegador atual = new Navegador();
        Navegador outro = new Navegador();
        atual.entrar();
        outro.entrar();
        assertThat(outro.get("/conta").statusCode()).isEqualTo(200);

        HttpResponse<String> troca = atual.post("/conta/senha",
                "senhaAtual", SENHA, "novaSenha", "NovaSenha1", "confirmarNovaSenha", "NovaSenha1");
        assertThat(troca.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/conta"));

        HttpResponse<String> doOutro = outro.get("/conta");
        assertThat(doOutro.headers().firstValue("Location")).as("outro navegador").hasValueSatisfying(l ->
                assertThat(l).endsWith("/login?expirada"));
        assertThat(atual.get("/conta").statusCode()).as("navegador atual").isEqualTo(200);
    }
}
