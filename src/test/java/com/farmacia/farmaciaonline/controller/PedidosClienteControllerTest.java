package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.EnderecoEntrega;
import com.farmacia.farmaciaonline.model.LocalRetirada;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.PedidosClienteService;
import com.farmacia.farmaciaonline.service.PedidosClienteService.FiltroMeusPedidos;
import com.farmacia.farmaciaonline.service.PedidosClienteService.DetalhePedido;
import com.farmacia.farmaciaonline.service.PedidosClienteService.LinhaItem;
import com.farmacia.farmaciaonline.service.PedidosClienteService.LinhaPagamento;
import com.farmacia.farmaciaonline.service.PedidosClienteService.PaginaDePedidos;
import com.farmacia.farmaciaonline.service.PedidosClienteService.PassoDoHistorico;
import com.farmacia.farmaciaonline.service.PedidosClienteService.ResumoPedido;

@WebMvcTest(PedidosClienteController.class)
@Import(SecurityConfig.class)
class PedidosClienteControllerTest {

    private static final long CLIENTE = 2L;
    private static final Instant QUANDO = Instant.parse("2026-10-29T13:30:00Z"); // 10:30 em São Paulo

    @MockBean
    private PedidosClienteService service;

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor como(long id, Perfil perfil) {
        Usuario usuario = Usuario.builder()
                .id(id)
                .nome("Maria da Silva")
                .email("cliente@farma.com")
                .dataNascimento(LocalDate.of(1995, 5, 20))
                .senhaHash("hash")
                .perfil(perfil)
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .build();
        return user(UsuarioAutenticado.de(usuario));
    }

    private static RequestPostProcessor cliente() {
        return como(CLIENTE, Perfil.CLIENTE);
    }

    private String html(MockHttpServletRequestBuilder requisicao) throws Exception {
        // A moeda em pt-BR usa espaço não separável ("R$ 10,00"): troca para as comparações ficarem legíveis.
        return mvc.perform(requisicao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).replace('\u00a0', ' ');
    }

    private static void assertLayoutComUmH1(String html) {
        assertThat(html).contains("lang=\"pt-BR\"");
        assertThat(StringUtils.countOccurrencesOf(html, "<header")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<footer")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
    }

    private static ResumoPedido resumo(long id, String total, long unidades, StatusPedido status) {
        return new ResumoPedido(id, QUANDO, new BigDecimal(total), unidades, status, TipoEntrega.RETIRADA);
    }

    private static DetalhePedido detalheDeRetirada(StatusPedido status, boolean receita, List<PassoDoHistorico> passos) {
        LocalRetirada loja = new LocalRetirada();
        loja.setNome("Farma++ Centro");
        loja.setLogradouro("Rua 14 de Julho");
        loja.setNumero("100");
        loja.setBairro("Centro");
        loja.setCidade("Campo Grande");
        loja.setUf("MS");
        loja.setHorarioFuncionamento("Seg a Sáb, 8h às 20h");
        LinhaItem item = new LinhaItem("Novalgina Dipirona 1g", "20 comprimidos", new BigDecimal("30.99"), 2,
                new BigDecimal("61.98"), receita);
        LinhaPagamento pagamento = new LinhaPagamento("Cartão de crédito", "Aprovado", new BigDecimal("61.98"), 3,
                "VISA final 1111", null, "29/10/2026 às 10:31");
        return new DetalhePedido(7L, QUANDO, status, TipoEntrega.RETIRADA, List.of(item), new BigDecimal("61.98"),
                BigDecimal.ZERO, new BigDecimal("61.98"), null, null, loja, List.of(pagamento), passos, receita);
    }

    // ---------- Acesso ----------

    @Test
    @DisplayName("visitante é mandado para o login na lista e no detalhe")
    void visitanteVaiParaOLogin() throws Exception {
        mvc.perform(get("/pedidos")).andExpect(redirectedUrlPattern("**/login*"));
        mvc.perform(get("/pedidos/1")).andExpect(redirectedUrlPattern("**/login*"));
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    @DisplayName("sessão sem o principal da aplicação recebe 403")
    void sessaoSemOPrincipalRecebe403() throws Exception {
        mvc.perform(get("/pedidos")).andExpect(status().isForbidden());
        mvc.perform(get("/pedidos/1")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("o cliente não altera nada: não existe rota de escrita em /pedidos")
    void naoHaRotaDeEscrita() throws Exception {
        mvc.perform(post("/pedidos/1").with(cliente()).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/pedidos/1").with(cliente()).with(csrf()).param("status", "ENTREGUE"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/pedidos/1").with(cliente()).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/pedidos/1").with(cliente()).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/pedidos").with(cliente()).with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("o id usado na consulta é o da sessão, nunca um vindo da URL ou do formulário")
    void usaOIdDaSessao() throws Exception {
        when(service.listar(CLIENTE, FiltroMeusPedidos.VAZIO, 0)).thenReturn(new PaginaDePedidos(List.of(), 0, 0, 0));

        // Quem tenta passar outro dono por parâmetro é ignorado: o serviço só é chamado com o id da sessão.
        html(get("/pedidos").param("usuarioId", "99").param("usuario", "99").with(cliente()));

        org.mockito.Mockito.verify(service).listar(CLIENTE, FiltroMeusPedidos.VAZIO, 0);
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).listar(org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.any(), anyInt());
    }

    // ---------- Lista ----------

    @Test
    @DisplayName("lista mostra número, data, itens, total e situação, com link para o detalhe")
    void listaMostraOsPedidos() throws Exception {
        when(service.listar(CLIENTE, FiltroMeusPedidos.VAZIO, 0)).thenReturn(new PaginaDePedidos(
                List.of(resumo(7, "61.98", 2, StatusPedido.PRONTO_PARA_RETIRADA),
                        resumo(3, "10.00", 1, StatusPedido.CANCELADO)), 0, 1, 2));

        String html = html(get("/pedidos").with(cliente()));

        assertLayoutComUmH1(html);
        assertThat(html).contains("Meus pedidos").contains("Pedido nº 7").contains("href=\"/pedidos/7\"")
                .contains("29/10/2026 às 10:30").contains("2 unidades").contains("1 unidade")
                .contains("61,98").contains("Pronto para retirada").contains("Cancelado")
                .contains("class=\"tabela-rolavel\"").contains("scope=\"col\"");
        assertThat(html.indexOf("Pedido nº 7")).isLessThan(html.indexOf("Pedido nº 3"));
        assertThat(html).doesNotContain("Paginação de pedidos");
    }

    @Test
    @DisplayName("sem pedidos: mensagem e link para o catálogo")
    void listaVazia() throws Exception {
        when(service.listar(CLIENTE, FiltroMeusPedidos.VAZIO, 0)).thenReturn(new PaginaDePedidos(List.of(), 0, 0, 0));

        String html = html(get("/pedidos").with(cliente()));

        assertThat(html).contains("ainda não fez nenhum pedido").contains("href=\"/produtos\"");
        assertThat(html).doesNotContain("<table");
    }

    @Test
    @DisplayName("mais de uma página: navegação com anterior e próxima")
    void paginacao() throws Exception {
        when(service.listar(CLIENTE, FiltroMeusPedidos.VAZIO, 1)).thenReturn(new PaginaDePedidos(
                List.of(resumo(2, "10.00", 1, StatusPedido.ENTREGUE)), 1, 3, 21));

        String html = html(get("/pedidos").param("pagina", "1").with(cliente()));

        assertThat(html).contains("Paginação de pedidos").contains("Página 2 de 3")
                .contains("pagina=0").contains("pagina=2");
    }

    @Test
    @DisplayName("falha ao carregar a lista: aviso, opção de tentar de novo e status 503")
    void falhaNaLista() throws Exception {
        when(service.listar(CLIENTE, FiltroMeusPedidos.VAZIO, 0)).thenThrow(new DataAccessResourceFailureException("banco fora"));

        String html = mvc.perform(get("/pedidos").with(cliente())).andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Não foi possível carregar seus pedidos").contains("Tentar novamente")
                .contains("href=\"/pedidos\"");
        assertThat(html).doesNotContain("banco fora").doesNotContain("DataAccess");
    }

    @Test
    @DisplayName("página que não é número dá 400, não 500")
    void paginaInvalida() throws Exception {
        mvc.perform(get("/pedidos").param("pagina", "abc").with(cliente())).andExpect(status().isBadRequest());
    }

    // ---------- Detalhe ----------

    @Test
    @DisplayName("detalhe de retirada: itens, totais, local, pagamento mascarado e linha do tempo")
    void detalheDeRetirada() throws Exception {
        when(service.detalhar(CLIENTE, 7L)).thenReturn(detalheDeRetirada(StatusPedido.PRONTO_PARA_RETIRADA, false, List.of(
                new PassoDoHistorico(StatusPedido.AGUARDANDO_PAGAMENTO, "29/10/2026 às 10:30", false),
                new PassoDoHistorico(StatusPedido.PAGAMENTO_APROVADO, "29/10/2026 às 10:31", false),
                new PassoDoHistorico(StatusPedido.EM_SEPARACAO, "29/10/2026 às 11:00", false),
                new PassoDoHistorico(StatusPedido.PRONTO_PARA_RETIRADA, "29/10/2026 às 12:00", true))));

        String html = html(get("/pedidos/7").with(cliente()));

        assertLayoutComUmH1(html);
        assertThat(html).contains("Pedido nº <span>7</span>").contains("Novalgina Dipirona 1g 20 comprimidos")
                .contains("30,99").contains("61,98").contains("Frete").contains("0,00")
                .contains("Retirada em loja").contains("Farma++ Centro").contains("Rua 14 de Julho, 100")
                .contains("Seg a Sáb, 8h às 20h")
                .contains("Cartão de crédito").contains("VISA final 1111").contains("em 3x").contains("Aprovado")
                .contains("pronto para retirada em <strong>Farma++ Centro</strong>");
        // linha do tempo em ordem, com a situação atual marcada
        assertThat(html.indexOf("Aguardando pagamento")).isLessThan(html.indexOf("Pagamento aprovado"));
        assertThat(html.indexOf("Pagamento aprovado")).isLessThan(html.indexOf("Em separação"));
        assertThat(html.lastIndexOf("Pronto para retirada")).isGreaterThan(html.indexOf("Em separação"));
        assertThat(html).contains("(situação atual)").contains("linha-do-tempo__atual");
        assertThat(html).doesNotContain("Pagar este pedido");
    }

    @Test
    @DisplayName("pedido com item controlado lembra de levar a receita")
    void avisoDeReceita() throws Exception {
        when(service.detalhar(CLIENTE, 7L)).thenReturn(detalheDeRetirada(StatusPedido.PAGAMENTO_APROVADO, true,
                List.of(new PassoDoHistorico(StatusPedido.PAGAMENTO_APROVADO, "29/10/2026 às 10:31", true))));

        String html = html(get("/pedidos/7").with(cliente()));

        assertThat(html).contains("Exige receita").contains("apresente a receita na loja");
    }

    @Test
    @DisplayName("pedido de entrega mostra a cópia do endereço e o prazo")
    void detalheDeEntrega() throws Exception {
        EnderecoEntrega endereco = new EnderecoEntrega();
        endereco.setDestinatario("Maria da Silva");
        endereco.setCep("79002000");
        endereco.setLogradouro("Avenida Afonso Pena");
        endereco.setNumero("500");
        endereco.setComplemento("Apto 12");
        endereco.setBairro("Centro");
        endereco.setCidade("Campo Grande");
        endereco.setUf("MS");
        LinhaItem item = new LinhaItem("Produto", "10 unidades", new BigDecimal("10.00"), 1, new BigDecimal("10.00"), false);
        DetalhePedido detalhe = new DetalhePedido(8L, QUANDO, StatusPedido.ENVIADO, TipoEntrega.ENTREGA, List.of(item),
                new BigDecimal("10.00"), new BigDecimal("9.90"), new BigDecimal("19.90"), 3, endereco, null, List.of(),
                List.of(new PassoDoHistorico(StatusPedido.ENVIADO, "30/10/2026 às 09:00", true)), false);
        when(service.detalhar(CLIENTE, 8L)).thenReturn(detalhe);

        String html = html(get("/pedidos/8").with(cliente()));

        assertThat(html).contains("Avenida Afonso Pena, 500").contains("Apto 12").contains("Centro - Campo Grande/MS")
                .contains("CEP 79002000").contains("até <span>3</span> dias úteis").contains("9,90")
                .contains("Nenhuma tentativa de pagamento");
        assertThat(html).doesNotContain("Retirada em loja");
    }

    @Test
    @DisplayName("pedido aguardando pagamento leva à tela de pagamento; só mostra o que é seguro")
    void aguardandoPagamento() throws Exception {
        LinhaPagamento pix = new LinhaPagamento("Pix", "Aguardando pagamento", new BigDecimal("10.00"), 1, null, null,
                "29/10/2026 às 10:30");
        LinhaPagamento recusado = new LinhaPagamento("Cartão de crédito", "Recusado", new BigDecimal("10.00"), 1,
                "VISA final 1210", "Cartão recusado pelo emissor.", "29/10/2026 às 10:29");
        DetalhePedido detalhe = new DetalhePedido(9L, QUANDO, StatusPedido.AGUARDANDO_PAGAMENTO, TipoEntrega.RETIRADA,
                List.of(new LinhaItem("Produto", "10 unidades", new BigDecimal("10.00"), 1, new BigDecimal("10.00"), false)),
                new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"), null, null, new LocalRetirada(),
                List.of(pix, recusado),
                List.of(new PassoDoHistorico(StatusPedido.AGUARDANDO_PAGAMENTO, "29/10/2026 às 10:30", true)), false);
        when(service.detalhar(CLIENTE, 9L)).thenReturn(detalhe);

        String html = html(get("/pedidos/9").with(cliente()));

        assertThat(html).contains("aguarda o pagamento").contains("href=\"/checkout/pedidos/9\"")
                .contains("Cartão recusado pelo emissor.").contains("VISA final 1210");
        assertThat(html).doesNotContain("DEMO-").doesNotContain("transacao");
    }

    @Test
    @DisplayName("pedido cancelado avisa; pedido de outra pessoa ou inexistente dá 404")
    void canceladoENaoEncontrado() throws Exception {
        when(service.detalhar(CLIENTE, 7L)).thenReturn(detalheDeRetirada(StatusPedido.CANCELADO, false,
                List.of(new PassoDoHistorico(StatusPedido.CANCELADO, "29/10/2026 às 10:40", true))));
        when(service.detalhar(CLIENTE, 99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(html(get("/pedidos/7").with(cliente()))).contains("Este pedido foi cancelado");
        mvc.perform(get("/pedidos/99").with(cliente())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("falha ao carregar o detalhe: aviso com link para tentar de novo e status 503")
    void falhaNoDetalhe() throws Exception {
        when(service.detalhar(anyLong(), anyLong())).thenThrow(new DataAccessResourceFailureException("banco fora"));

        String html = mvc.perform(get("/pedidos/7").with(cliente())).andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Não foi possível carregar este pedido").contains("href=\"/pedidos/7\"");
        assertThat(html).doesNotContain("banco fora");
    }

    @Test
    @DisplayName("id que não é número dá 400")
    void idInvalido() throws Exception {
        mvc.perform(get("/pedidos/abc").with(cliente())).andExpect(status().isBadRequest());
    }

    // ---------- Cabeçalho ----------

    @Test
    @DisplayName("o cabeçalho do cliente tem o link Meus pedidos; o do administrador não")
    void linkNoCabecalho() throws Exception {
        when(service.listar(CLIENTE, FiltroMeusPedidos.VAZIO, 0)).thenReturn(new PaginaDePedidos(List.of(), 0, 0, 0));
        when(service.listar(1L, FiltroMeusPedidos.VAZIO, 0)).thenReturn(new PaginaDePedidos(List.of(), 0, 0, 0));

        assertThat(html(get("/pedidos").with(cliente()))).contains(">Meus pedidos</a>").doesNotContain("Painel admin");
        assertThat(html(get("/pedidos").with(como(1L, Perfil.ADMIN)))).doesNotContain("href=\"/pedidos\">Meus pedidos");
    }
}
