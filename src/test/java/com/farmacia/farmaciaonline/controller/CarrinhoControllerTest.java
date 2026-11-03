package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.CarrinhoCookie;
import com.farmacia.farmaciaonline.security.MesclagemCarrinhoNoLogin;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CarrinhoException;
import com.farmacia.farmaciaonline.service.CarrinhoService;
import com.farmacia.farmaciaonline.service.ResumoCarrinho;
import com.farmacia.farmaciaonline.service.ResumoCarrinho.LinhaCarrinho;

@WebMvcTest(CarrinhoController.class)
@Import(SecurityConfig.class)
class CarrinhoControllerTest {
    @MockBean
    private CategoriaRepository categoriaRepository;

    @MockBean
    private CarrinhoService carrinhoService;

    @Autowired
    private MockMvc mvc;

    private static UsuarioAutenticado cliente() {
        Usuario usuario = Usuario.builder()
                .id(2L)
                .nome("Maria da Silva")
                .email("cliente@farma.com")
                .dataNascimento(LocalDate.of(1995, 5, 20))
                .senhaHash("hash")
                .perfil(Perfil.CLIENTE)
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .build();
        return UsuarioAutenticado.de(usuario);
    }

    private static LinhaCarrinho linha(long itemId, String problema) {
        return new LinhaCarrinho(itemId, 10L, 20L, "Novalgina Dipirona 1g", "20 comprimidos",
                "/images/produtos/placeholder.svg", 2, new BigDecimal("30.99"), new BigDecimal("30.99"),
                false, new BigDecimal("61.98"), problema, 10);
    }

    private static ResumoCarrinho resumoCom(LinhaCarrinho... linhas) {
        return new ResumoCarrinho(List.of(linhas), new BigDecimal("61.98"), 2);
    }

    @Test
    @DisplayName("visitante sem carrinho abre /carrinho e vê o carrinho vazio")
    void visitanteVeCarrinhoVazio() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(ResumoCarrinho.vazio());

        String html = mvc.perform(get("/carrinho"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Seu carrinho está vazio");
    }

    @Test
    @DisplayName("item com problema aparece destacado e o botão de finalizar fica desativado")
    void itemComProblemaBloqueiaFinalizar() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(resumoCom(linha(5L, "Sem estoque no momento. Remova-o para continuar.")));

        String html = mvc.perform(get("/carrinho"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Sem estoque no momento");
        assertThat(html).contains("disabled");
        assertThat(html).doesNotContain("href=\"/carrinho/finalizar\"");
    }

    @Test
    @DisplayName("POST sem token CSRF é recusado")
    void postSemCsrfEhRecusado() throws Exception {
        mvc.perform(post("/carrinho/itens").param("apresentacaoId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("visitante que adiciona ganha o cookie do carrinho e o dono é o token")
    void visitanteAdicionaEGanhaCookie() throws Exception {
        mvc.perform(post("/carrinho/itens").with(csrf()).param("apresentacaoId", "7").param("quantidade", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(cookie().exists(CarrinhoCookie.NOME))
                .andExpect(cookie().httpOnly(CarrinhoCookie.NOME, true))
                .andExpect(flash().attribute("sucesso", "Item adicionado ao carrinho."));

        verify(carrinhoService).adicionar(argThat(dono -> dono.isVisitante() && dono.usuarioId() == null),
                eq(7L), eq(2));
    }

    @Test
    @DisplayName("cliente logado usa o id do login como dono e não recebe cookie")
    void clienteUsaOIdDoLogin() throws Exception {
        mvc.perform(post("/carrinho/itens").with(user(cliente())).with(csrf()).param("apresentacaoId", "7"))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(cookie().doesNotExist(CarrinhoCookie.NOME));

        verify(carrinhoService).adicionar(argThat(dono -> !dono.isVisitante() && Long.valueOf(2L).equals(dono.usuarioId())),
                eq(7L), eq(1));
    }

    @Test
    @DisplayName("recusa de negócio vira mensagem de erro na tela, não erro 500")
    void recusaViraMensagem() throws Exception {
        when(carrinhoService.adicionar(any(), eq(9L), anyInt()))
                .thenThrow(new CarrinhoException("Este produto está sem estoque no momento."));

        mvc.perform(post("/carrinho/itens").with(csrf()).param("apresentacaoId", "9"))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("erro", "Este produto está sem estoque no momento."));
    }

    @Test
    @DisplayName("quantidade zero não remove: leva à confirmação")
    void quantidadeZeroPedeConfirmacao() throws Exception {
        mvc.perform(post("/carrinho/itens/5/quantidade").with(csrf()).param("quantidade", "0"))
                .andExpect(redirectedUrl("/carrinho/itens/5/remover"));

        verify(carrinhoService, never()).alterarQuantidade(any(), any(), anyInt());
    }

    @Test
    @DisplayName("quantidade negativa também leva à confirmação")
    void quantidadeNegativaPedeConfirmacao() throws Exception {
        mvc.perform(post("/carrinho/itens/5/quantidade").with(csrf()).param("quantidade", "-3"))
                .andExpect(redirectedUrl("/carrinho/itens/5/remover"));
    }

    @Test
    @DisplayName("a página de confirmação mostra o item e só o POST remove")
    void paginaDeConfirmacaoDeRemocao() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(resumoCom(linha(5L, null)));

        String html = mvc.perform(get("/carrinho/itens/5/remover"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Remover item do carrinho?");
        assertThat(html).contains("Novalgina Dipirona 1g");
        verify(carrinhoService, never()).remover(any(), any());
    }

    @Test
    @DisplayName("esvaziar pede confirmação numa página própria: um h1, o POST com CSRF e o botão de perigo")
    void paginaDeConfirmacaoDeEsvaziar() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(resumoCom(linha(5L, null)));

        String html = mvc.perform(get("/carrinho/esvaziar"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("<h1>Esvaziar o carrinho?</h1>");
        assertThat(html).containsPattern("(?s)<form class=\"acoes\" action=\"/carrinho/esvaziar\" method=\"post\">"
                + ".*?name=\"_csrf\".*?<button type=\"submit\" class=\"btn-perigo\">Sim, esvaziar</button>");
        verify(carrinhoService, never()).esvaziar(any());
    }

    @Test
    @DisplayName("confirmar esvaziar com o carrinho já vazio volta ao carrinho")
    void confirmarEsvaziarCarrinhoVazio() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(ResumoCarrinho.vazio());

        mvc.perform(get("/carrinho/esvaziar")).andExpect(redirectedUrl("/carrinho"));
    }

    @Test
    @DisplayName("o POST de esvaziar continua igual: esvazia e volta ao carrinho com a mensagem")
    void esvaziarContinuaPorPost() throws Exception {
        mvc.perform(post("/carrinho/esvaziar").with(csrf()))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("sucesso", "Carrinho esvaziado."));
        verify(carrinhoService).esvaziar(any());
    }

    @Test
    @DisplayName("carrinho com itens: etapa 1 marcada, tabela rolável acessível e esvaziar como link de perigo para a confirmação")
    void carrinhoComEtapasETabela() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(resumoCom(linha(5L, null)));

        String html = mvc.perform(get("/carrinho"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).containsPattern("(?s)<li aria-current=\"step\">\\s*<span class=\"etapas__numero\" aria-hidden=\"true\">1</span>"
                + "\\s*<span class=\"etapas__rotulo\">Carrinho</span>");
        assertThat(html).contains("<div class=\"tabela-rolavel\" role=\"region\" aria-label=\"Itens do carrinho\" tabindex=\"0\">");
        assertThat(html).contains("<a class=\"link-perigo\" href=\"/carrinho/esvaziar\">Esvaziar carrinho</a>");
        assertThat(html).contains("<a class=\"btn-secundario\" href=\"/produtos\">Continuar comprando</a>");
    }

    @Test
    @DisplayName("carrinho vazio não mostra as etapas e leva ao catálogo")
    void carrinhoVazioSemEtapas() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(ResumoCarrinho.vazio());

        String html = mvc.perform(get("/carrinho"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).doesNotContain("class=\"etapas\"");
        assertThat(html).contains("<a class=\"btn-primario\" href=\"/produtos\">Continuar comprando</a>");
    }

    @Test
    @DisplayName("confirmar remoção de item que não é do carrinho da pessoa volta ao carrinho com erro")
    void confirmacaoDeItemAlheio() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(ResumoCarrinho.vazio());

        mvc.perform(get("/carrinho/itens/999/remover"))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("erro", "Este item não está no seu carrinho."));
    }

    @Test
    @DisplayName("remover item alheio, por POST direto, é recusado pelo serviço e vira mensagem")
    void removerItemAlheio() throws Exception {
        doThrow(new CarrinhoException("Este item não está no seu carrinho."))
                .when(carrinhoService).remover(any(), eq(999L));

        mvc.perform(post("/carrinho/itens/999/remover").with(csrf()))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("erro", "Este item não está no seu carrinho."));
    }

    @Test
    @DisplayName("visitante que finaliza é mandado ao login, com esta URL guardada para voltar")
    void visitanteVaiAoLoginEGuardaOCaminho() throws Exception {
        var resultado = mvc.perform(get("/carrinho/finalizar"))
                .andExpect(redirectedUrl("/login"))
                .andReturn();

        var guardado = resultado.getRequest().getSession().getAttribute("SPRING_SECURITY_SAVED_REQUEST");
        assertThat(guardado).isNotNull();
        assertThat(guardado.toString()).contains("/carrinho/finalizar");
    }

    @Test
    @DisplayName("cliente com carrinho em ordem segue para o checkout")
    void clienteSegueParaOCheckout() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(resumoCom(linha(5L, null)));

        mvc.perform(get("/carrinho/finalizar").with(user(cliente())))
                .andExpect(redirectedUrl("/checkout"));
    }

    @Test
    @DisplayName("cliente com item indisponível não passa: volta ao carrinho")
    void itemIndisponivelImpedeFinalizar() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(resumoCom(linha(5L, "Sem estoque no momento.")));

        mvc.perform(get("/carrinho/finalizar").with(user(cliente())))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attributeExists("erro"));
    }

    @Test
    @DisplayName("carrinho vazio não finaliza")
    void carrinhoVazioNaoFinaliza() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(ResumoCarrinho.vazio());

        mvc.perform(get("/carrinho/finalizar").with(user(cliente())))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("erro", "Seu carrinho está vazio."));
    }

    @Test
    @DisplayName("quem pede JSON recebe o item, os avisos e o total; visitante ganha o cookie")
    void adicionarRapidoResponderJson() throws Exception {
        when(carrinhoService.adicionar(any(), eq(20L), eq(2))).thenReturn(List.of("A quantidade foi ajustada para 2."));
        when(carrinhoService.resumo(any())).thenReturn(resumoCom(linha(5L, null)));
        when(carrinhoService.contarUnidades(any())).thenReturn(2);

        mvc.perform(post("/carrinho/itens").with(csrf()).accept(MediaType.APPLICATION_JSON)
                        .param("apresentacaoId", "20").param("quantidade", "2"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CarrinhoCookie.NOME))
                .andExpect(jsonPath("$.mensagem").value("Item adicionado ao carrinho."))
                .andExpect(jsonPath("$.avisos[0]").value("A quantidade foi ajustada para 2."))
                .andExpect(jsonPath("$.item.nome").value("Novalgina Dipirona 1g"))
                .andExpect(jsonPath("$.item.descricao").value("20 comprimidos"))
                .andExpect(jsonPath("$.item.imagem").value("/images/produtos/placeholder.svg"))
                .andExpect(jsonPath("$.item.quantidade").value(2))
                .andExpect(jsonPath("$.item.precoUnitario").value(30.99))
                .andExpect(jsonPath("$.item.subtotal").value(61.98))
                .andExpect(jsonPath("$.totalUnidades").value(2))
                .andExpect(jsonPath("$.total").value(61.98));

        verify(carrinhoService).adicionar(argThat(dono -> dono.isVisitante()), eq(20L), eq(2));
    }

    @Test
    @DisplayName("JSON: cliente logado usa o id do login e não recebe cookie")
    void adicionarRapidoDoCliente() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(resumoCom(linha(5L, null)));

        mvc.perform(post("/carrinho/itens").with(user(cliente())).with(csrf()).accept(MediaType.APPLICATION_JSON)
                        .param("apresentacaoId", "20"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(CarrinhoCookie.NOME));

        verify(carrinhoService).adicionar(argThat(dono -> Long.valueOf(2L).equals(dono.usuarioId())), eq(20L), eq(1));
    }

    @Test
    @DisplayName("JSON: recusa de negócio vira 422 com a mensagem, sem redirecionar")
    void adicionarRapidoRecusado() throws Exception {
        when(carrinhoService.adicionar(any(), eq(9L), anyInt()))
                .thenThrow(new CarrinhoException("Este produto está sem estoque no momento."));

        mvc.perform(post("/carrinho/itens").with(csrf()).accept(MediaType.APPLICATION_JSON)
                        .param("apresentacaoId", "9"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Este produto está sem estoque no momento."));
    }

    @Test
    @DisplayName("JSON: sem token CSRF também é recusado")
    void adicionarRapidoExigeCsrf() throws Exception {
        mvc.perform(post("/carrinho/itens").accept(MediaType.APPLICATION_JSON).param("apresentacaoId", "1"))
                .andExpect(status().isForbidden());

        verify(carrinhoService, never()).adicionar(any(), any(), anyInt());
    }

    @Test
    @DisplayName("quem pede HTML continua sendo redirecionado ao carrinho (sem JavaScript)")
    void adicionarComHtmlContinuaRedirecionando() throws Exception {
        mvc.perform(post("/carrinho/itens").with(csrf()).accept(MediaType.TEXT_HTML).param("apresentacaoId", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/carrinho"));
    }

    @Test
    @DisplayName("cabeçalho mostra o número de itens, com texto para leitor de tela")
    void cabecalhoMostraContador() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(ResumoCarrinho.vazio());
        when(carrinhoService.contarUnidades(any())).thenReturn(3);

        String html = mvc.perform(get("/carrinho").with(user(cliente())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"carrinho-contagem\">(<span>3</span>)<span class=\"visually-hidden\"> itens no carrinho</span></span>");
    }

    @Test
    @DisplayName("carrinho vazio: o cabeçalho não mostra número")
    void cabecalhoSemNumeroQuandoVazio() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(ResumoCarrinho.vazio());

        String html = mvc.perform(get("/carrinho"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"carrinho-contagem\"></span>");
        assertThat(html).doesNotContain("itens no carrinho");
    }

    @Test
    @DisplayName("aviso de carrinho mantido aparece uma vez e some na segunda requisição; um único h1")
    void avisoDeCarrinhoMantidoApareceUmaVez() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(ResumoCarrinho.vazio());
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute(MesclagemCarrinhoNoLogin.AVISO_MANTIDO_NA_SESSAO, "Você tem 2 unidades no seu carrinho.");

        String primeira = mvc.perform(get("/carrinho").with(user(cliente())).session(sessao))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String segunda = mvc.perform(get("/carrinho").with(user(cliente())).session(sessao))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(primeira).contains("Você tem 2 unidades no seu carrinho.").contains("role=\"status\"");
        assertThat(segunda).doesNotContain("Você tem 2 unidades no seu carrinho.");
        assertThat(primeira.split("<h1", -1)).hasSize(2);
    }

    @Test
    @DisplayName("resposta que é redirect não consome o aviso: ele aparece na página seguinte")
    void redirectNaoConsomeOAviso() throws Exception {
        when(carrinhoService.resumo(any())).thenReturn(resumoCom(linha(5L, null)));
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute(MesclagemCarrinhoNoLogin.AVISO_MANTIDO_NA_SESSAO, "Você tem 2 unidades no seu carrinho.");

        mvc.perform(get("/carrinho/finalizar").with(user(cliente())).session(sessao))
                .andExpect(redirectedUrl("/checkout"));

        assertThat(sessao.getAttribute(MesclagemCarrinhoNoLogin.AVISO_MANTIDO_NA_SESSAO)).isNotNull();
    }

    @Test
    @DisplayName("administrador não monta carrinho: adicionar volta ao carrinho com a mensagem e nada é gravado")
    void administradorNaoAdiciona() throws Exception {
        mvc.perform(post("/carrinho/itens").with(csrf()).with(user("admin").roles("ADMIN"))
                        .param("apresentacaoId", "1"))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("erro", "Contas de administrador não fazem compras."));
        mvc.perform(get("/carrinho/finalizar").with(user("admin").roles("ADMIN")))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("erro", "Contas de administrador não fazem compras."));

        verify(carrinhoService, never()).adicionar(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("administrador na adição rápida (JSON): 403 com a mensagem, sem redirecionar")
    void administradorNaoAdicionaPeloJson() throws Exception {
        mvc.perform(post("/carrinho/itens").with(csrf()).with(user("admin").roles("ADMIN"))
                        .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                        .param("apresentacaoId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.erro")
                        .value("Contas de administrador não fazem compras."));

        verify(carrinhoService, never()).adicionar(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
