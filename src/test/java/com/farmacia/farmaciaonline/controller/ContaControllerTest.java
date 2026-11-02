package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.StringUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.ContaForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.RevogacaoDeSessoes;
import com.farmacia.farmaciaonline.security.SessaoAutenticada;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.ContaInvalidaException;
import com.farmacia.farmaciaonline.service.ContaService;
import com.farmacia.farmaciaonline.service.DadosDaConta;

@WebMvcTest(ContaController.class)
@Import(SecurityConfig.class)
class ContaControllerTest {

    private static final long CLIENTE = 2L;

    @MockBean
    private ContaService service;

    @MockBean
    private SessaoAutenticada sessao;

    @MockBean
    private RevogacaoDeSessoes revogacao;

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    private static Usuario usuario() {
        return Usuario.builder()
                .id(CLIENTE)
                .nome("Maria da Silva")
                .cpf("52998224725")
                .email("cliente@farma.com")
                .celular("67999998888")
                .dataNascimento(LocalDate.of(1995, 5, 20))
                .genero(Genero.FEMININO)
                .senhaHash("hash")
                .perfil(Perfil.CLIENTE)
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .emailConfirmadoEm(Instant.now())
                .build();
    }

    private static RequestPostProcessor cliente() {
        return user(UsuarioAutenticado.de(usuario()));
    }

    private static MockHttpServletRequestBuilder dadosValidos(MockHttpServletRequestBuilder b) {
        return b.param("nome", "Joana Souza").param("dataNascimento", "1990-03-15").param("genero", "FEMININO");
    }

    private String html(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("visitante é mandado ao login em todas as rotas da conta")
    void visitanteVaiAoLogin() throws Exception {
        for (String rota : new String[] {"/conta", "/conta/editar", "/conta/senha"}) {
            mvc.perform(get(rota)).andExpect(redirectedUrlPattern("**/login*"));
        }
    }

    @Test
    @DisplayName("POST sem token CSRF é recusado")
    void postSemCsrf() throws Exception {
        mvc.perform(dadosValidos(post("/conta/editar")).with(cliente())).andExpect(status().isForbidden());
        mvc.perform(post("/conta/senha").with(cliente())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a conta mostra os dados do próprio usuário e oferece a troca de e-mail e de celular")
    void verConta() throws Exception {
        when(service.dados(CLIENTE)).thenReturn(DadosDaConta.de(usuario()));

        String pagina = html(get("/conta").with(cliente()));

        assertThat(pagina).contains("Maria da Silva", "529.982.247-25", "cliente@farma.com", "(67) 99999-8888", "20/05/1995");
        assertThat(pagina).contains("(confirmado)").contains("(não confirmado)");
        assertThat(pagina).contains("href=\"/conta/contato/email\"", "href=\"/conta/contato/celular\"");
    }

    @Test
    @DisplayName("Minha conta: um h1, seções em cartão na ordem certa e excluir conta como link de perigo")
    void contaEmCartoes() throws Exception {
        when(service.dados(CLIENTE)).thenReturn(DadosDaConta.de(usuario()));

        String pagina = html(get("/conta").with(cliente()));

        assertThat(StringUtils.countOccurrencesOf(pagina, "<h1")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(pagina, "<section class=\"conta__secao secao-cartao\"")).isEqualTo(6);
        assertThat(pagina).containsPattern("(?s)<h1>Minha conta</h1>.*?<h2 id=\"titulo-dados\">Dados pessoais</h2>"
                + ".*?<h2 id=\"titulo-contatos\">Contatos</h2>.*?<h2 id=\"titulo-seguranca\">Segurança</h2>"
                + ".*?<h2 id=\"titulo-enderecos\">Endereços e cartões</h2>.*?href=\"/enderecos\".*?href=\"/conta/cartoes\""
                + ".*?<h2 id=\"titulo-pedidos\">Pedidos</h2>.*?href=\"/pedidos\""
                + ".*?<h2 id=\"titulo-excluir\">Excluir conta</h2>");
        assertThat(pagina).doesNotContainPattern("<h[3-6]");
        assertThat(pagina).contains("<a class=\"link-perigo\" href=\"/conta/excluir\">Excluir minha conta</a>");
    }

    @Test
    @DisplayName("a tela de edição não tem campo de CPF, perfil ou status e tem o link Cancelar")
    void editarNaoOfereceCamposProtegidos() throws Exception {
        ContaForm form = new ContaForm();
        form.setNome("Maria da Silva");
        form.setDataNascimento(LocalDate.of(1995, 5, 20));
        form.setGenero(Genero.FEMININO);
        when(service.formDe(CLIENTE)).thenReturn(form);
        when(service.dados(CLIENTE)).thenReturn(DadosDaConta.de(usuario()));

        String pagina = html(get("/conta/editar").with(cliente()));

        assertThat(pagina).contains("name=\"nome\"", "name=\"dataNascimento\"", "name=\"genero\"");
        assertThat(pagina).doesNotContain("name=\"cpf\"", "name=\"perfil\"", "name=\"status\"", "name=\"email\"");
        // Salvar primeiro no DOM (o Enter aciona ele) e Cancelar como botão secundário no mesmo grupo.
        assertThat(pagina).containsPattern("(?s)<div class=\"acoes\">\\s*<button type=\"submit\" class=\"btn-primario\">Salvar alterações</button>"
                + "\\s*<a class=\"btn-secundario\" href=\"/conta\">Cancelar</a>");
    }

    @Test
    @DisplayName("salvar dados válidos grava, atualiza a sessão sem trocar o id e mostra mensagem de sucesso")
    void salvarDados() throws Exception {
        when(service.atualizarDados(eq(CLIENTE), any(ContaForm.class))).thenReturn(usuario());

        mvc.perform(dadosValidos(post("/conta/editar")).with(cliente()).with(csrf()))
                .andExpect(redirectedUrl("/conta"))
                .andExpect(flash().attribute("sucesso", "Dados atualizados com sucesso."));

        verify(sessao).atualizar(any(Usuario.class), any(), any(), eq(false));
    }

    @Test
    @DisplayName("CPF, perfil, status e id enviados à força no POST são ignorados")
    void camposProtegidosSaoIgnorados() throws Exception {
        when(service.atualizarDados(eq(CLIENTE), any(ContaForm.class))).thenReturn(usuario());

        mvc.perform(dadosValidos(post("/conta/editar")).with(cliente()).with(csrf())
                        .param("cpf", "11144477735").param("perfil", "ADMIN").param("status", "PENDENTE")
                        .param("id", "1").param("email", "invasor@x.com"))
                .andExpect(redirectedUrl("/conta"));

        verify(service).atualizarDados(eq(CLIENTE), any(ContaForm.class));
    }

    @Test
    @DisplayName("dados inválidos voltam ao formulário com erro e não chegam ao serviço")
    void dadosInvalidos() throws Exception {
        when(service.dados(CLIENTE)).thenReturn(DadosDaConta.de(usuario()));

        String pagina = html(post("/conta/editar").with(cliente()).with(csrf())
                .param("nome", " ").param("dataNascimento", "1990-03-15").param("genero", "FEMININO"));

        assertThat(pagina).contains("Informe o nome completo.").contains("Corrija os campos");
        verify(service, never()).atualizarDados(any(), any());
        verify(sessao, never()).atualizar(any(), any(), any(), eq(false));
    }

    @Test
    @DisplayName("regra do serviço (ex.: menor de idade) aparece no campo certo")
    void erroDoServicoApareceNoCampo() throws Exception {
        when(service.dados(CLIENTE)).thenReturn(DadosDaConta.de(usuario()));
        doThrow(new ContaInvalidaException(Map.of("dataNascimento", "É preciso ter 18 anos ou mais para ter uma conta.")))
                .when(service).atualizarDados(eq(CLIENTE), any(ContaForm.class));

        String pagina = html(dadosValidos(post("/conta/editar")).with(cliente()).with(csrf()));

        assertThat(pagina).contains("É preciso ter 18 anos ou mais");
        verify(sessao, never()).atualizar(any(), any(), any(), eq(false));
    }

    @Test
    @DisplayName("trocar a senha com sucesso renova o id da sessão")
    void trocarSenha() throws Exception {
        when(service.alterarSenha(eq(CLIENTE), any())).thenReturn(usuario());

        mvc.perform(post("/conta/senha").with(cliente()).with(csrf())
                        .param("senhaAtual", "Farma@123").param("novaSenha", "NovaSenha1").param("confirmarNovaSenha", "NovaSenha1"))
                .andExpect(redirectedUrl("/conta"))
                .andExpect(flash().attribute("sucesso", "Senha alterada com sucesso."));

        verify(sessao).atualizar(any(Usuario.class), any(), any(), eq(true));
        verify(revogacao).encerrarOutrasSessoesDe(eq(CLIENTE), any());
    }

    @Test
    @DisplayName("senha atual incorreta volta ao formulário com a mensagem e sem repetir a senha digitada")
    void senhaAtualIncorreta() throws Exception {
        doThrow(new ContaInvalidaException(Map.of("senhaAtual", "Senha atual incorreta.")))
                .when(service).alterarSenha(eq(CLIENTE), any());

        String pagina = html(post("/conta/senha").with(cliente()).with(csrf())
                .param("senhaAtual", "Errada123").param("novaSenha", "NovaSenha1").param("confirmarNovaSenha", "NovaSenha1"));

        assertThat(pagina).contains("Senha atual incorreta.");
        assertThat(pagina).doesNotContain("Errada123").doesNotContain("NovaSenha1");
        verify(sessao, never()).atualizar(any(), any(), any(), eq(true));
        verify(revogacao, never()).encerrarOutrasSessoesDe(any(), any());
    }

    @Test
    @DisplayName("nova senha fora da política é recusada antes de chegar ao serviço")
    void senhaForaDaPolitica() throws Exception {
        String pagina = html(post("/conta/senha").with(cliente()).with(csrf())
                .param("senhaAtual", "Farma@123").param("novaSenha", "curta").param("confirmarNovaSenha", "curta"));

        assertThat(pagina).contains("A senha deve ter 8 caracteres ou mais");
        verify(service, never()).alterarSenha(any(), any());
    }
}
