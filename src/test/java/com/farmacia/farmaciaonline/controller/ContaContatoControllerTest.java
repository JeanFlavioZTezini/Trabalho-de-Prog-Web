package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.RevogacaoDeSessoes;
import com.farmacia.farmaciaonline.security.SessaoAutenticada;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoEnvio;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoValidacao;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.SituacaoEnvio;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.SituacaoValidacao;
import com.farmacia.farmaciaonline.service.ContaInvalidaException;
import com.farmacia.farmaciaonline.service.ContaService;
import com.farmacia.farmaciaonline.service.ContatoService;
import com.farmacia.farmaciaonline.service.ContatoService.ResultadoConfirmacao;
import com.farmacia.farmaciaonline.service.ContatoService.Tipo;
import com.farmacia.farmaciaonline.service.DadosDaConta;

/** Telas de troca de e-mail e celular (UC07 / F-21): rotas, HTML, campos aceitos e o que acontece com a sessão. */
@WebMvcTest(ContaContatoController.class)
@Import(SecurityConfig.class)
class ContaContatoControllerTest {

    private static final long CLIENTE = 2L;

    @MockBean
    private ContatoService service;

    @MockBean
    private ContaService conta;

    @MockBean
    private CodigoVerificacaoService codigos;

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

    @BeforeEach
    void dados() {
        when(conta.dados(CLIENTE)).thenReturn(DadosDaConta.de(usuario()));
        when(codigos.getValidade()).thenReturn(Duration.ofMinutes(10));
        when(codigos.getTentativasMaximas()).thenReturn(5);
    }

    private String html(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static int ocorrencias(String html, String regex) {
        Matcher m = Pattern.compile(regex).matcher(html);
        int total = 0;
        while (m.find()) {
            total++;
        }
        return total;
    }

    @Test
    @DisplayName("visitante vai ao login em todas as rotas de troca de contato")
    void visitanteVaiAoLogin() throws Exception {
        mvc.perform(get("/conta/contato/email")).andExpect(redirectedUrlPattern("**/login*"));
        mvc.perform(get("/conta/contato/celular")).andExpect(redirectedUrlPattern("**/login*"));
    }

    @Test
    @DisplayName("POST sem token CSRF é recusado")
    void postSemCsrf() throws Exception {
        mvc.perform(post("/conta/contato/email/enviar").with(cliente())).andExpect(status().isForbidden());
        mvc.perform(post("/conta/contato/email/confirmar").with(cliente())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("tipo que não é email nem celular responde 404")
    void tipoInvalido() throws Exception {
        mvc.perform(get("/conta/contato/cpf").with(cliente())).andExpect(status().isNotFound());
        mvc.perform(post("/conta/contato/senha/enviar").with(cliente()).with(csrf())
                .param("novoContato", "x@y.com").param("senhaAtual", "Farma@123")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("tela do e-mail: um único h1, campos rotulados, autocomplete certo e link Cancelar")
    void telaDoEmail() throws Exception {
        String html = html(get("/conta/contato/email").with(cliente()));

        assertThat(ocorrencias(html, "<h1[ >]")).isEqualTo(1);
        assertThat(html).contains("Alterar e-mail").contains("cliente@farma.com");
        assertThat(html).contains("<label for=\"novoContato\"").contains("<label for=\"senhaAtual\"")
                .contains("<label for=\"codigo\"");
        assertThat(html).contains("type=\"email\"").contains("autocomplete=\"email\"")
                .contains("autocomplete=\"current-password\"").contains("autocomplete=\"one-time-code\"");
        assertThat(html).contains("action=\"/conta/contato/email/enviar\"")
                .contains("action=\"/conta/contato/email/confirmar\"").contains("href=\"/conta\"");
    }

    @Test
    @DisplayName("tela do celular: campo tel com máscara de celular")
    void telaDoCelular() throws Exception {
        String html = html(get("/conta/contato/celular").with(cliente()));

        assertThat(ocorrencias(html, "<h1[ >]")).isEqualTo(1);
        assertThat(html).contains("Alterar celular").contains("type=\"tel\"").contains("autocomplete=\"tel\"")
                .contains("data-mascara=\"celular\"").contains("/js/mascaras.js");
    }

    @Test
    @DisplayName("pedir a troca usa o id da sessão e ignora id, perfil e status forjados")
    void solicitarUsaSoOsCamposPermitidos() throws Exception {
        when(service.solicitar(anyLong(), any(), any(), any())).thenReturn(new ResultadoEnvio(SituacaoEnvio.ENVIADO, 0));

        mvc.perform(post("/conta/contato/email/enviar").with(cliente()).with(csrf())
                        .param("novoContato", "novo@teste.com").param("senhaAtual", "Farma@123")
                        .param("id", "99").param("perfil", "ADMIN").param("status", "PENDENTE")
                        .param("usuarioId", "99"))
                .andExpect(redirectedUrl("/conta/contato/email"))
                .andExpect(flash().attributeExists("sucesso"));

        ArgumentCaptor<Long> id = ArgumentCaptor.forClass(Long.class);
        verify(service).solicitar(id.capture(), eq(Tipo.EMAIL), eq("novo@teste.com"), eq("Farma@123"));
        assertThat(id.getValue()).isEqualTo(CLIENTE);
    }

    @Test
    @DisplayName("senha errada: o erro aparece no elemento senhaAtual-erro, ligado ao campo")
    void erroNoCampo() throws Exception {
        when(service.solicitar(anyLong(), any(), any(), any()))
                .thenThrow(new ContaInvalidaException(Map.of("senhaAtual", "Senha atual incorreta.")));

        String html = html(post("/conta/contato/email/enviar").with(cliente()).with(csrf())
                .param("novoContato", "novo@teste.com").param("senhaAtual", "errada"));

        assertThat(html).containsPattern("(?s)<p class=\"campo__erro\" role=\"alert\" id=\"senhaAtual-erro\">\\s*Senha atual incorreta\\.\\s*</p>");
        assertThat(html).contains("aria-describedby=\"senhaAtual-ajuda senhaAtual-erro\"").contains("aria-invalid=\"true\"");
        assertThat(html).doesNotContain("id=\"novoContato-erro\"");
    }

    @Test
    @DisplayName("campos em branco são recusados antes de chegar ao serviço")
    void camposEmBranco() throws Exception {
        String html = html(post("/conta/contato/celular/enviar").with(cliente()).with(csrf())
                .param("novoContato", "").param("senhaAtual", ""));

        assertThat(html).contains("id=\"novoContato-erro\"").contains("id=\"senhaAtual-erro\"");
        verify(service, never()).solicitar(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("pedir código cedo demais mostra quantos segundos aguardar")
    void aguarde() throws Exception {
        when(service.solicitar(anyLong(), any(), any(), any())).thenReturn(new ResultadoEnvio(SituacaoEnvio.AGUARDE, 42));

        mvc.perform(post("/conta/contato/celular/enviar").with(cliente()).with(csrf())
                        .param("novoContato", "67988887766").param("senhaAtual", "Farma@123"))
                .andExpect(redirectedUrl("/conta/contato/celular"))
                .andExpect(flash().attribute("erro", "Aguarde 42 segundos para pedir outro código."));
    }

    @Test
    @DisplayName("confirmar o e-mail renova a sessão, derruba as outras e volta para a conta")
    void confirmarEmail() throws Exception {
        Usuario atualizado = usuario();
        atualizado.setEmail("novo@teste.com");
        when(service.confirmar(CLIENTE, Tipo.EMAIL, "123456")).thenReturn(new ResultadoConfirmacao(
                new ResultadoValidacao(SituacaoValidacao.VALIDO, 5, CanalCodigo.EMAIL, "novo@teste.com"), false,
                atualizado));

        mvc.perform(post("/conta/contato/email/confirmar").with(cliente()).with(csrf()).param("codigo", "123456"))
                .andExpect(redirectedUrl("/conta"))
                .andExpect(flash().attributeExists("sucesso"));

        verify(revogacao).encerrarOutrasSessoesDe(eq(CLIENTE), any());
        verify(sessao).atualizar(eq(atualizado), any(), any(), eq(true));
    }

    @Test
    @DisplayName("confirmar o celular atualiza a sessão sem derrubar as outras")
    void confirmarCelular() throws Exception {
        Usuario atualizado = usuario();
        when(service.confirmar(CLIENTE, Tipo.CELULAR, "123456")).thenReturn(new ResultadoConfirmacao(
                new ResultadoValidacao(SituacaoValidacao.VALIDO, 5, CanalCodigo.SMS, "67988887766"), false, atualizado));

        mvc.perform(post("/conta/contato/celular/confirmar").with(cliente()).with(csrf()).param("codigo", "123456"))
                .andExpect(redirectedUrl("/conta"));

        verify(revogacao, never()).encerrarOutrasSessoesDe(anyLong(), any());
        verify(sessao).atualizar(eq(atualizado), any(), any(), eq(false));
    }

    @Test
    @DisplayName("código incorreto volta à tela com as tentativas que restam e não mexe na sessão")
    void codigoIncorreto() throws Exception {
        when(service.confirmar(CLIENTE, Tipo.EMAIL, "000000")).thenReturn(new ResultadoConfirmacao(
                new ResultadoValidacao(SituacaoValidacao.INCORRETO, 4, CanalCodigo.EMAIL, "novo@teste.com"), false,
                null));

        mvc.perform(post("/conta/contato/email/confirmar").with(cliente()).with(csrf()).param("codigo", "000000"))
                .andExpect(redirectedUrl("/conta/contato/email"))
                .andExpect(flash().attribute("erro", "Código incorreto. Você ainda tem 4 tentativas."));

        verify(sessao, never()).atualizar(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(revogacao, never()).encerrarOutrasSessoesDe(anyLong(), any());
    }

    @Test
    @DisplayName("e-mail tomado por outra conta na hora de confirmar: mensagem genérica e nada muda")
    void emailEmUso() throws Exception {
        when(service.confirmar(CLIENTE, Tipo.EMAIL, "123456")).thenReturn(new ResultadoConfirmacao(
                new ResultadoValidacao(SituacaoValidacao.VALIDO, 5, CanalCodigo.EMAIL, "novo@teste.com"), true, null));

        mvc.perform(post("/conta/contato/email/confirmar").with(cliente()).with(csrf()).param("codigo", "123456"))
                .andExpect(redirectedUrl("/conta/contato/email"))
                .andExpect(flash().attribute("erro", "Não foi possível usar este e-mail. Informe outro."));

        verify(sessao, never()).atualizar(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
