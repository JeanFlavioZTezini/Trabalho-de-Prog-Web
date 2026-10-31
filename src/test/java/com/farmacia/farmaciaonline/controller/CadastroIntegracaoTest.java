package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/**
 * O cadastro de ponta a ponta (controller, serviço, banco e login), com o seed do V2.
 * Cada teste é desfeito no fim (@Transactional).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CadastroIntegracaoTest {

    private static final String EMAIL = "carla.nova@exemplo.com";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    /**
     * POST /cadastro com dados válidos. Os pares nome/valor de {@code trocas} SUBSTITUEM os padrões (ou acrescentam
     * campos novos): o {@code param()} do MockMvc acrescenta um segundo valor, e o Spring juntaria os dois com vírgula.
     */
    private static MockHttpServletRequestBuilder cadastro(String... trocas) {
        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("nome", "Carla Nova");
        dados.put("cpf", "390.533.447-05");
        dados.put("dataNascimento", "1992-08-10");
        dados.put("genero", "FEMININO");
        dados.put("celular", "(67) 99999-7777");
        dados.put("email", EMAIL);
        dados.put("senha", "Senha123");
        dados.put("confirmarSenha", "Senha123");
        dados.put("aceiteTermos", "true");
        for (int i = 0; i < trocas.length; i += 2) {
            dados.put(trocas[i], trocas[i + 1]);
        }
        MockHttpServletRequestBuilder requisicao = post("/cadastro").with(csrf());
        dados.forEach(requisicao::param);
        return requisicao;
    }

    private String pagina(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("cadastro completo cria conta PENDENTE, guarda o retorno e a conta ainda não consegue entrar")
    void cadastroCriaContaPendenteQueNaoEntra() throws Exception {
        mvc.perform(cadastro("retorno", "checkout"))
                .andExpect(redirectedUrl("/confirmar-conta"))
                .andExpect(request().sessionAttribute(CadastroController.SESSAO_RETORNO, "checkout"));

        Usuario criada = usuarios.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(criada.getStatus()).isEqualTo(StatusUsuario.PENDENTE);
        assertThat(criada.getPerfil()).isEqualTo(Perfil.CLIENTE);

        mvc.perform(formLogin("/login").user("identificador", EMAIL).password("senha", "Senha123"))
                .andExpect(redirectedUrl("/login?erro=pendente"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("a sessão guarda o id da conta criada, e só ele abre a página de confirmação")
    void sessaoGuardaAContaCriada() throws Exception {
        var resultado = mvc.perform(cadastro()).andExpect(redirectedUrl("/confirmar-conta")).andReturn();
        Long id = usuarios.findByEmailIgnoreCase(EMAIL).orElseThrow().getId();

        assertThat(resultado.getRequest().getSession().getAttribute(CadastroController.SESSAO_CONTA)).isEqualTo(id);
    }

    @Test
    @DisplayName("tentativa de elevar perfil, status ou termos pelo navegador é ignorada")
    void tentativaDeElevarPerfilEIgnorada() throws Exception {
        mvc.perform(cadastro("perfil", "ADMIN", "status", "ATIVA", "termosVersao", "9.9",
                        "termosAceitosEm", "2000-01-01T00:00:00Z", "senhaHash", "hash-forjado", "id", "1",
                        "tentativasLoginFalhas", "-1", "excluidoEm", "2000-01-01T00:00:00Z"))
                .andExpect(redirectedUrl("/confirmar-conta"));

        Usuario criada = usuarios.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(criada.getId()).isNotEqualTo(1L);
        assertThat(criada.getPerfil()).isEqualTo(Perfil.CLIENTE);
        assertThat(criada.getStatus()).isEqualTo(StatusUsuario.PENDENTE);
        assertThat(criada.getTermosVersao()).isEqualTo("1.0");
        assertThat(criada.getTermosAceitosEm().getEpochSecond()).isGreaterThan(1_700_000_000L);
        assertThat(criada.getSenhaHash()).startsWith("$2");
        assertThat(criada.getTentativasLoginFalhas()).isZero();
        assertThat(criada.getExcluidoEm()).isNull();
        assertThat(usuarios.findByEmailIgnoreCase("admin@farma.com").orElseThrow().getPerfil()).isEqualTo(Perfil.ADMIN);
    }

    @Test
    @DisplayName("e-mail de uma conta do seed (em maiúsculas) volta ao formulário com o aviso de login e recuperação")
    void emailRepetidoMostraAvisoELinks() throws Exception {
        String html = pagina(cadastro("email", "CLIENTE@FARMA.COM"));

        assertThat(html).contains("id=\"email-erro\"").contains("Já existe uma conta com este e-mail.").contains("href=\"/login\"")
                .contains("href=\"/recuperar-senha\"");
        assertThat(usuarios.existsByCpf("39053344705")).isFalse();
    }

    @Test
    @DisplayName("CPF de uma conta do seed volta ao formulário com o aviso")
    void cpfRepetidoMostraAviso() throws Exception {
        String html = pagina(cadastro("cpf", "529.982.247-25"));

        assertThat(html).contains("id=\"cpf-erro\"").contains("Já existe uma conta com este CPF.").contains("href=\"/recuperar-senha\"");
    }

    @Test
    @DisplayName("menor de idade, CPF inválido e senhas diferentes: formulário volta, nada é gravado")
    void dadosRecusadosNaoGravamNada() throws Exception {
        LocalDate dezessete = LocalDate.now().minusYears(17);

        String html = pagina(cadastro("dataNascimento", dezessete.toString(), "cpf", "390.533.447-04",
                "confirmarSenha", "Outra123"));

        assertThat(html).contains("id=\"dataNascimento-erro\"").contains("ou mais para criar uma conta.")
                .contains("id=\"cpf-erro\"").contains("CPF inválido")
                .contains("id=\"confirmarSenha-erro\"").contains("As senhas não conferem.");
        assertThat(usuarios.findByEmailIgnoreCase(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("quem abre a confirmação sem ter acabado de se cadastrar volta ao cadastro")
    void confirmacaoSemSessaoVoltaAoCadastro() throws Exception {
        mvc.perform(get("/confirmar-conta"))
                .andExpect(redirectedUrl("/cadastro"));
    }
}
