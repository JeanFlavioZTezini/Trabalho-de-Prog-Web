package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.StringUtils;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.CadastroForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CadastroInvalidoException;
import com.farmacia.farmaciaonline.service.CadastroService;

/**
 * Camada web do cadastro, com o serviço simulado: sem banco e sem Docker. As regras do serviço têm teste próprio
 * (CadastroServiceIntegracaoTest) e o fluxo completo está no CadastroIntegracaoTest.
 */
@WebMvcTest({CadastroController.class, ConfirmacaoContaController.class})
@Import(SecurityConfig.class)
class CadastroControllerTest {

    @MockBean
    private CadastroService service;

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    /**
     * POST /cadastro com dados válidos. Os pares nome/valor de {@code trocas} SUBSTITUEM os padrões: o
     * {@code param()} do MockMvc acrescenta um segundo valor, e o Spring juntaria os dois com vírgula.
     */
    private static MockHttpServletRequestBuilder cadastro(String... trocas) {
        Map<String, String> dados = new LinkedHashMap<>();
        dados.put("nome", "Maria da Silva");
        dados.put("cpf", "529.982.247-25");
        dados.put("dataNascimento", "1995-05-20");
        dados.put("genero", "FEMININO");
        dados.put("celular", "(67) 99999-8888");
        dados.put("email", "maria@exemplo.com");
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

    private static Usuario contaDeId(long id) {
        return Usuario.builder().id(id).nome("Maria da Silva").email("maria@exemplo.com")
                .dataNascimento(LocalDate.of(1995, 5, 20)).senhaHash("hash").perfil(Perfil.CLIENTE)
                .status(StatusUsuario.PENDENTE).termosVersao("1.0").termosAceitosEm(Instant.now()).build();
    }

    private static RequestPostProcessor clienteLogado() {
        Usuario usuario = contaDeId(2L);
        usuario.setStatus(StatusUsuario.ATIVA);
        return user(UsuarioAutenticado.de(usuario));
    }

    private String html(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("o formulário abre para visitante, com um h1, rótulos, CSRF e as máscaras")
    void formularioAbreParaVisitante() throws Exception {
        String html = html(get("/cadastro"));

        assertThat(html).contains("lang=\"pt-BR\"").contains("<h1>Criar conta</h1>");
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("action=\"/cadastro\"").contains("name=\"_csrf\"");
        for (String campo : new String[] {"nome", "cpf", "dataNascimento", "genero", "celular", "email", "senha",
                "confirmarSenha", "aceiteTermos"}) {
            assertThat(html).as("rótulo do campo %s", campo).contains("for=\"" + campo + "\"");
        }
        assertThat(html).contains("autocomplete=\"new-password\"").contains("data-mascara=\"cpf\"")
                .contains("data-mascara=\"celular\"").contains("src=\"/js/mascaras.js\"");
        assertThat(html).contains(">Masculino<").contains(">Feminino<");
        assertThat(html).doesNotContain("name=\"retorno\"");
    }

    @ParameterizedTest(name = "?retorno={0} fica guardado no formulário")
    @ValueSource(strings = {"carrinho", "checkout"})
    void retornoValidoVaiNoCampoEscondido(String retorno) throws Exception {
        assertThat(html(get("/cadastro").param("retorno", retorno)))
                .contains("name=\"retorno\" value=\"" + retorno + "\"");
    }

    @ParameterizedTest(name = "?retorno={0} é ignorado")
    @ValueSource(strings = {"http://site-falso.com", "//site-falso.com", "CHECKOUT", "/checkout", "admin", ""})
    void retornoQueNaoEUmaDasDuasPalavrasEIgnorado(String retorno) throws Exception {
        assertThat(html(get("/cadastro").param("retorno", retorno))).doesNotContain("name=\"retorno\"");
    }

    @Test
    @DisplayName("quem já está logado não vê o cadastro nem consegue enviá-lo")
    void clienteLogadoVaiParaAHome() throws Exception {
        mvc.perform(get("/cadastro").with(clienteLogado())).andExpect(redirectedUrl("/"));
        mvc.perform(cadastro().with(clienteLogado())).andExpect(redirectedUrl("/"));

        verify(service, never()).cadastrar(any());
    }

    @Test
    @DisplayName("dados inválidos voltam ao formulário com a mensagem em cada campo, sem eco da senha")
    void dadosInvalidosVoltamComMensagens() throws Exception {
        String html = html(cadastro("cpf", "529.982.247-24", "email", "", "celular", "123", "aceiteTermos", "false"));

        assertThat(html).contains("id=\"cpf-erro\"").contains("CPF inválido").contains("id=\"email-erro\"")
                .contains("Informe o e-mail.").contains("id=\"celular-erro\"").contains("Celular inválido")
                .contains("id=\"aceiteTermos-erro\"").contains("É preciso aceitar os termos");
        assertThat(html).contains("aria-invalid=\"true\"").contains("aria-describedby=\"cpf-ajuda cpf-erro\"");
        assertThat(html).contains("value=\"Maria da Silva\"").doesNotContain("Senha123");
        verify(service, never()).cadastrar(any());
    }

    @Test
    @DisplayName("data inexistente e gênero fora da lista dão mensagem em português")
    void dataEGeneroInvalidosTemMensagemAmigavel() throws Exception {
        String html = html(cadastro("dataNascimento", "2000-02-31", "genero", "OUTRO"));

        assertThat(html).contains("Informe uma data válida.").contains("Escolha uma das opções de gênero.");
        assertThat(html).doesNotContain("Failed to convert");
    }

    @Test
    @DisplayName("as regras do serviço (senhas, 18 anos) aparecem no campo certo e nada é gravado")
    void regrasDoServicoAparecemNoCampoCerto() throws Exception {
        when(service.verificar(any())).thenReturn(Map.of("confirmarSenha", "As senhas não conferem.",
                "dataNascimento", "É preciso ter 18 anos ou mais para criar uma conta."));

        String html = html(cadastro());

        // a ajuda "É preciso ter 18 anos ou mais." sempre aparece; o erro é a frase completa dentro do aviso do campo
        assertThat(html).contains("id=\"confirmarSenha-erro\"").contains("As senhas não conferem.")
                .contains("id=\"dataNascimento-erro\"").contains("ou mais para criar uma conta.");
        assertThat(html).doesNotContain("Já existe uma conta com estes dados");
        verify(service, never()).cadastrar(any());
    }

    @Test
    @DisplayName("e-mail ou CPF já usados orientam a entrar ou recuperar a senha")
    void duplicidadeOrientaLoginERecuperacao() throws Exception {
        when(service.verificar(any())).thenReturn(Map.of("email", "Já existe uma conta com este e-mail."));

        String html = html(cadastro());

        assertThat(html).contains("Já existe uma conta com este e-mail.").contains("Já existe uma conta com estes dados");
        assertThat(html).contains("href=\"/login\"").contains("href=\"/recuperar-senha\"");
        verify(service, never()).cadastrar(any());
    }

    @Test
    @DisplayName("duplicidade que só aparece na hora de gravar (cadastros ao mesmo tempo) também orienta")
    void corridaDeCadastroOrienta() throws Exception {
        when(service.cadastrar(any())).thenThrow(new CadastroInvalidoException(Map.of("cpf", "Já existe uma conta com este CPF.")));

        String html = html(cadastro());

        assertThat(html).contains("Já existe uma conta com este CPF.").contains("href=\"/recuperar-senha\"");
    }

    @Test
    @DisplayName("cadastro válido guarda a conta na sessão (não na URL) e vai para a confirmação")
    void cadastroValidoRedirecionaParaAConfirmacao() throws Exception {
        when(service.cadastrar(any())).thenReturn(contaDeId(7L));

        mvc.perform(cadastro())
                .andExpect(redirectedUrl("/confirmar-conta"))
                .andExpect(request().sessionAttribute(CadastroController.SESSAO_CONTA, 7L))
                .andExpect(request().sessionAttributeDoesNotExist(CadastroController.SESSAO_RETORNO));

        ArgumentCaptor<CadastroForm> enviado = ArgumentCaptor.forClass(CadastroForm.class);
        verify(service).cadastrar(enviado.capture());
        assertThat(enviado.getValue().getGenero()).isEqualTo(Genero.FEMININO);
        assertThat(enviado.getValue().getDataNascimento()).isEqualTo(LocalDate.of(1995, 5, 20));
    }

    @ParameterizedTest(name = "retorno={0} é guardado na sessão")
    @ValueSource(strings = {"carrinho", "checkout"})
    void retornoValidoFicaNaSessao(String retorno) throws Exception {
        when(service.cadastrar(any())).thenReturn(contaDeId(7L));

        mvc.perform(cadastro("retorno", retorno))
                .andExpect(redirectedUrl("/confirmar-conta"))
                .andExpect(request().sessionAttribute(CadastroController.SESSAO_RETORNO, retorno));
    }

    @Test
    @DisplayName("retorno que não é uma das duas palavras não vai para a sessão")
    void retornoInvalidoNaoVaiParaASessao() throws Exception {
        when(service.cadastrar(any())).thenReturn(contaDeId(7L));

        mvc.perform(cadastro("retorno", "http://site-falso.com"))
                .andExpect(redirectedUrl("/confirmar-conta"))
                .andExpect(request().sessionAttributeDoesNotExist(CadastroController.SESSAO_RETORNO));
    }

    @Test
    @DisplayName("POST sem token CSRF é recusado")
    void postSemCsrfEhRecusado() throws Exception {
        mvc.perform(post("/cadastro").param("nome", "Maria")).andExpect(status().isForbidden());

        verify(service, never()).cadastrar(any());
    }

    @Test
    @DisplayName("destinoDoRetorno só conhece carrinho e checkout")
    void destinoDoRetornoSoConheceDuasPalavras() {
        assertThat(CadastroController.destinoDoRetorno("carrinho")).isEqualTo("/carrinho");
        assertThat(CadastroController.destinoDoRetorno("checkout")).isEqualTo("/checkout");
        assertThat(CadastroController.destinoDoRetorno("https://x.com")).isNull();
        assertThat(CadastroController.destinoDoRetorno(null)).isNull();
    }

    @Test
    @DisplayName("a página provisória de confirmação só abre para quem acabou de se cadastrar")
    void confirmacaoExigeContaNaSessao() throws Exception {
        mvc.perform(get("/confirmar-conta")).andExpect(redirectedUrl("/cadastro"));

        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute(CadastroController.SESSAO_CONTA, 7L);
        String html = html(get("/confirmar-conta").session(sessao));

        assertThat(html).contains("<h1>Cadastro recebido</h1>").contains("pendente");
    }
}
