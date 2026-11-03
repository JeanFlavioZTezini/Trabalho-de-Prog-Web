package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.ClienteAdminService;
import com.farmacia.farmaciaonline.service.ClienteDetalhe;
import com.farmacia.farmaciaonline.service.ClienteDetalhe.PedidoResumo;
import com.farmacia.farmaciaonline.service.ClienteResumo;

/** HTML das telas de consulta de clientes (UC11): estrutura, acessibilidade básica e somente leitura. */
@WebMvcTest(AdminClienteController.class)
@Import(SecurityConfig.class)
class AdminClienteTelaTest {

    @MockBean
    private ClienteAdminService service;

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor admin() {
        return user(new UsuarioAutenticado(1L, "Admin", Perfil.ADMIN, "admin@farma.com", "x"));
    }

    private String html(String url) throws Exception {
        return mvc.perform(get(url).with(admin())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static ClienteResumo resumo(long id, String nome) {
        return new ClienteResumo(id, nome, "***.982.247-**", "maria@teste.com", StatusUsuario.ATIVA, "Ativa",
                LocalDateTime.of(2026, 3, 4, 10, 0));
    }

    private static ClienteDetalhe detalhe(List<PedidoResumo> pedidos) {
        return new ClienteDetalhe(7L, "Maria da Silva", "529.982.247-25", "maria@teste.com", true, "(11) 98765-4321",
                false, LocalDate.of(1990, 1, 2), Genero.FEMININO, StatusUsuario.ATIVA, "Ativa",
                LocalDateTime.of(2026, 3, 4, 10, 0), pedidos);
    }

    /** Só o conteúdo da página: o cabeçalho do layout tem busca e botão de sair, que não contam. */
    private static String conteudo(String html) {
        return html.substring(html.indexOf("<main"), html.indexOf("</main>"));
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
    @DisplayName("lista: um único h1, CPF mascarado, tabela em região rolável e links de detalhe")
    void listaComClientes() throws Exception {
        when(service.listar(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(resumo(7, "Maria da Silva")), PageRequest.of(0, 20), 1));

        String html = html("/admin/clientes");

        assertThat(ocorrencias(html, "<h1[ >]")).isEqualTo(1);
        assertThat(html).contains("***.982.247-**").doesNotContain("52998224725").doesNotContain("529.982.247-25");
        assertThat(html).contains("class=\"tabela-rolavel\"").contains("role=\"region\"").contains("tabindex=\"0\"")
                .contains("aria-label=\"Lista de clientes\"");
        assertThat(html).contains("class=\"tabela-admin\"")
                .containsPattern("(?s)<a class=\"acao-icone\" href=\"/admin/clientes/7\" title=\"Ver [^\"]+\">\\s*<svg[^>]*aria-hidden=\"true\"");
        assertThat(html).contains("<label for=\"q\"").contains("<label for=\"status\"");
        // Lupa decorativa dentro do campo de busca; o rótulo continua no <label for="q">.
        assertThat(html).containsPattern("(?s)<span class=\"campo-busca\"><svg class=\"icone\"[^>]*aria-hidden=\"true\".*?</svg>"
                + "<input type=\"search\" id=\"q\" name=\"q\"");
        assertThat(html).contains("<td><span class=\"selo selo--sucesso\">Ativa</span></td>");
        assertThat(html).doesNotContain("Nenhum cliente encontrado.");
    }

    @Test
    @DisplayName("lista vazia mostra 'Nenhum cliente encontrado.' e nenhuma tabela")
    void listaVazia() throws Exception {
        when(service.listar(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        String html = html("/admin/clientes");

        assertThat(html).contains("<p>Nenhum cliente encontrado.</p>");
        assertThat(html).doesNotContain("<table");
    }

    @Test
    @DisplayName("lista: paginação com Anterior, página X de Y e Próxima, mantendo busca e status")
    void paginacaoMantemFiltros() throws Exception {
        when(service.listar(any(), any(), any(), any(), any(), any())).thenReturn(
                new PageImpl<>(List.of(resumo(7, "Maria")), PageRequest.of(1, 20), 61));

        String html = mvc.perform(get("/admin/clientes").param("q", "maria").param("status", "ATIVA").with(admin()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Página 2 de 4");
        assertThat(html).contains(">Anterior</a>").contains(">Próxima</a>");
        assertThat(html).contains("q=maria").contains("status=ATIVA").contains("pagina=0").contains("pagina=2");
    }

    @Test
    @DisplayName("detalhe: um único h1, CPF completo, contatos e nenhum formulário, botão ou campo editável")
    void detalheSomenteLeitura() throws Exception {
        when(service.detalhar(any(), any(), any())).thenReturn(detalhe(List.of()));

        String html = conteudo(html("/admin/clientes/7"));

        assertThat(ocorrencias(html, "<h1[ >]")).isEqualTo(1);
        assertThat(html).contains("529.982.247-25").contains("(11) 98765-4321").contains("02/01/1990");
        assertThat(html).contains("maria@teste.com (confirmado)").contains("Feminino");
        assertThat(html).contains("class=\"conta__dados\"");
        assertThat(html).contains("<p>Nenhum pedido ainda.</p>");
        assertThat(html).doesNotContain("<form").doesNotContain("<button").doesNotContain("<input")
                .doesNotContain("<textarea").doesNotContain("<select");
        assertThat(html.toLowerCase()).doesNotContain("hash").doesNotContain("senha").doesNotContain("editar")
                .doesNotContain("excluir");
        assertThat(html).contains("<a class=\"topo-admin__voltar\" href=\"/admin/clientes\">")
                .contains("Voltar<span class=\"visually-hidden\"> para Clientes</span>");
    }

    @Test
    @DisplayName("detalhe: pedidos recentes em tabela com região, total e status em português")
    void detalheComPedidos() throws Exception {
        when(service.detalhar(any(), any(), any())).thenReturn(detalhe(List.of(
                new PedidoResumo(31L, LocalDateTime.of(2026, 5, 6, 14, 30), new BigDecimal("59.90"),
                        "Pagamento aprovado"))));

        String html = html("/admin/clientes/7");

        assertThat(html).contains("aria-label=\"Pedidos recentes do cliente\"").contains("role=\"region\"");
        assertThat(html).contains("<td><a href=\"/admin/pedidos/31\">Pedido nº 31</a></td>").contains("06/05/2026 14:30").contains("Pagamento aprovado");
        assertThat(html).contains("59,90");
        assertThat(html).doesNotContain("Nenhum pedido ainda.");
    }

    @Test
    @DisplayName("cabeçalho do administrador logado tem o link Painel admin")
    void cabecalhoTemLinkDoPainel() throws Exception {
        when(service.listar(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(html("/admin/clientes")).contains("<a href=\"/admin\">Painel admin</a>");
    }

    @Test
    @DisplayName("a lista pagina por nome e desempata pelo id (nomes iguais não repetem nem pulam entre páginas)")
    void paginacaoDesempataPeloId() throws Exception {
        when(service.listar(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        html("/admin/clientes");

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> paginacao =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        org.mockito.Mockito.verify(service).listar(any(), any(), any(), any(), any(), paginacao.capture());
        assertThat(paginacao.getValue().getSort())
                .isEqualTo(org.springframework.data.domain.Sort.by("nome", "id"));
    }
}
