package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.AdminPedidosService.FiltroPedidos;
import com.farmacia.farmaciaonline.service.PedidosClienteService.FiltroMeusPedidos;

/**
 * F-23: período e status em "Meus pedidos", cliente e período nos pedidos do admin, período e CSV nos clientes.
 * Usa a base do checkout (pedidos de verdade, sem rollback); as datas são ajustadas por SQL.
 */
class FiltrosEExportacaoIntegracaoTest extends CheckoutTesteBase {

    private static final long ADMIN = 1L;
    private static final LocalDate DIA = LocalDate.of(2025, 3, 10);

    @Autowired
    private PedidosClienteService pedidosCliente;

    @Autowired
    private AdminPedidosService adminPedidos;

    @Autowired
    private ClienteAdminService clientes;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    private Long pedidoEm(Usuario cliente, Apresentacao apresentacao, String instante) {
        noCarrinho(cliente, apresentacao, 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        jdbc.update("update pedido set criado_em = ?::timestamptz where id = ?", instante, id);
        return id;
    }

    private void cadastradoEm(Usuario usuario, String instante) {
        jdbc.update("update usuario set criado_em = ?::timestamptz where id = ?", instante, usuario.getId());
    }

    private static RequestPostProcessor como(Usuario usuario) {
        return user(UsuarioAutenticado.de(usuario));
    }

    private static RequestPostProcessor admin() {
        return user(new UsuarioAutenticado(ADMIN, "Admin", Perfil.ADMIN, "admin@farma.com", "x"));
    }

    private int exportacoes() {
        return jdbc.queryForObject("select count(*) from log_auditoria where acao = 'CLIENTE_EXPORTAR'", Integer.class);
    }

    @Test
    @DisplayName("meus pedidos: período inclui o dia inteiro nos dois extremos (horário de São Paulo)")
    void meusPedidosPorPeriodo() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 50);
        Long antes = pedidoEm(cliente, a, "2025-03-09 23:59:00-03");
        Long inicioDoDia = pedidoEm(cliente, a, "2025-03-10 00:00:00-03");
        Long fimDoDia = pedidoEm(cliente, a, "2025-03-10 23:59:00-03");
        Long depois = pedidoEm(cliente, a, "2025-03-11 00:00:00-03");

        var lista = pedidosCliente.listar(cliente.getId(),
                new FiltroMeusPedidos(null, new PeriodoFiltro(DIA, DIA)), 0);

        assertThat(lista.getPedidos()).extracting(p -> p.getId()).containsExactly(fimDoDia, inicioDoDia)
                .doesNotContain(antes, depois);
        assertThat(pedidosCliente.listar(cliente.getId(), new FiltroMeusPedidos(null, new PeriodoFiltro(DIA, null)), 0)
                .getPedidos()).extracting(p -> p.getId()).containsExactly(depois, fimDoDia, inicioDoDia);
    }

    @Test
    @DisplayName("meus pedidos: status combina com período e nunca traz pedido de outro cliente")
    void meusPedidosPorStatus() {
        Usuario cliente = novoCliente();
        Usuario outro = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 50);
        Long aguardando = pedidoEm(cliente, a, "2025-03-10 10:00:00-03");
        Long cancelado = pedidoEm(cliente, a, "2025-03-10 11:00:00-03");
        jdbc.update("update pedido set status = 'CANCELADO' where id = ?", cancelado);
        Long dele = pedidoEm(outro, a, "2025-03-10 12:00:00-03");
        jdbc.update("update pedido set status = 'CANCELADO' where id = ?", dele);

        var lista = pedidosCliente.listar(cliente.getId(),
                new FiltroMeusPedidos(StatusPedido.CANCELADO, new PeriodoFiltro(DIA, DIA)), 0);

        assertThat(lista.getPedidos()).extracting(p -> p.getId()).containsExactly(cancelado)
                .doesNotContain(aguardando, dele);
    }

    @Test
    @DisplayName("meus pedidos: a tela tem os filtros com rótulo e a paginação mantém status e período")
    void telaMeusPedidos() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 50);
        for (int i = 0; i < 11; i++) {
            pedidoEm(cliente, a, "2025-03-10 1" + (i % 10) + ":00:00-03");
        }

        String html = mvc.perform(get("/pedidos").with(como(cliente))
                        .param("status", "AGUARDANDO_PAGAMENTO").param("de", "2025-03-10").param("ate", "2025-03-10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<label for=\"status\"", "<label for=\"de\"", "<label for=\"ate\"");
        assertThat(html).containsPattern("href=\"/pedidos\\?status=AGUARDANDO_PAGAMENTO&amp;de=2025-03-10&amp;ate=2025-03-10&amp;pagina=1\"");
        assertThat(html.split("<h1").length - 1).isEqualTo(1);
    }

    @Test
    @DisplayName("meus pedidos: data inicial depois da final mostra erro ligado ao campo e não consulta")
    void meusPedidosPeriodoInvalido() throws Exception {
        Usuario cliente = novoCliente();
        pedidoEm(cliente, novaApresentacao("10.00", 5), "2025-03-10 10:00:00-03");

        String html = mvc.perform(get("/pedidos").with(como(cliente)).param("de", "2025-03-11").param("ate", "2025-03-10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"erro-filtro\"", "aria-describedby=\"erro-filtro\"")
                .contains("Nenhum pedido encontrado com esses filtros").doesNotContain("Pedido nº");
    }

    @Test
    @DisplayName("pedidos do admin: filtra por cliente (nome ou CPF), período e número, combinados")
    void pedidosAdminPorClienteEPeriodo() {
        Usuario cliente = novoCliente();
        Usuario outro = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 50);
        Long noDia = pedidoEm(cliente, a, "2025-03-10 10:00:00-03");
        Long foraDoDia = pedidoEm(cliente, a, "2025-04-10 10:00:00-03");
        Long doOutro = pedidoEm(outro, a, "2025-03-10 10:00:00-03");
        var pagina = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "id"));

        assertThat(adminPedidos.listar(new FiltroPedidos(null, null, cliente.getNome(), new PeriodoFiltro(DIA, DIA)), pagina)
                .getContent()).extracting(AdminPedidosService.Resumo::id).containsExactly(noDia);
        assertThat(adminPedidos.listar(new FiltroPedidos(null, null, cliente.getCpf(), PeriodoFiltro.VAZIO), pagina)
                .getContent()).extracting(AdminPedidosService.Resumo::id).containsExactly(foraDoDia, noDia)
                .doesNotContain(doOutro);
        assertThat(adminPedidos.listar(new FiltroPedidos(doOutro, StatusPedido.AGUARDANDO_PAGAMENTO, null,
                new PeriodoFiltro(DIA, DIA)), pagina).getContent()).extracting(AdminPedidosService.Resumo::id)
                .containsExactly(doOutro);
    }

    @Test
    @DisplayName("pedidos do admin: status inválido vira 'todos' e a paginação leva cliente e período")
    void telaPedidosAdmin() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 50);
        for (int i = 0; i < 21; i++) {
            pedidoEm(cliente, a, "2025-03-10 10:00:00-03");
        }

        String html = mvc.perform(get("/admin/pedidos").with(admin()).param("status", "XYZ")
                        .param("cliente", cliente.getEmail()).param("de", "2025-03-10").param("ate", "2025-03-10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<label for=\"cliente\"", "<label for=\"de\"", "<label for=\"ate\"");
        assertThat(html).contains("status=&amp;cliente=" + cliente.getEmail()
                + "&amp;de=2025-03-10&amp;ate=2025-03-10&amp;pagina=1");
    }

    @Test
    @DisplayName("clientes do admin: período sobre a data de cadastro")
    void clientesPorPeriodo() {
        Usuario noDia = novoCliente();
        Usuario depois = novoCliente();
        cadastradoEm(noDia, "2025-03-10 22:00:00-03");
        cadastradoEm(depois, "2025-03-11 08:00:00-03");

        var pagina = clientes.listar(ADMIN, "127.0.0.1", "Cliente", null, new PeriodoFiltro(DIA, DIA),
                PageRequest.of(0, 100, Sort.by("nome")));

        assertThat(pagina.getContent()).extracting(ClienteResumo::getId).contains(noDia.getId())
                .doesNotContain(depois.getId());
        assertThat(jdbc.queryForObject("select detalhes from log_auditoria where acao = 'CLIENTE_LISTAR' "
                + "order by id desc limit 1", String.class)).isEqualTo("status=TODOS, busca=sim, periodo=sim, pagina=0");
    }

    @Test
    @DisplayName("CSV: mascara CPF, e-mail e celular, não leva senha, aplica filtros e grava a exportação")
    void csvMascaradoEAuditado() {
        Usuario cliente = novoCliente();
        cliente.setCelular("67999998888");
        usuarioRepository.saveAndFlush(cliente);
        cadastradoEm(cliente, "2025-03-10 10:00:00-03");
        int antes = exportacoes();

        String csv = clientes.exportarCsv(ADMIN, "127.0.0.1", cliente.getNome(), null, new PeriodoFiltro(DIA, DIA));

        assertThat(csv).startsWith("﻿\"Id\";\"Nome\";\"CPF\";\"E-mail\";\"Celular\";\"Status\";\"Cadastro\"\r\n");
        assertThat(csv).contains("\"" + cliente.getNome() + "\"", "\"10/03/2025 10:00\"", "\"Ativa\"");
        assertThat(csv).doesNotContain(cliente.getCpf(), cliente.getEmail(), "67999998888", "hash");
        assertThat(csv).contains(ContatoMascarado.cpf(cliente.getCpf()), ContatoMascarado.email(cliente.getEmail()));
        assertThat(csv.lines().count()).isEqualTo(2);
        assertThat(exportacoes()).isEqualTo(antes + 1);
        assertThat(jdbc.queryForObject("select detalhes from log_auditoria where acao = 'CLIENTE_EXPORTAR' "
                + "order by id desc limit 1", String.class)).contains("periodo=sim", "linhas=1")
                .doesNotContain(cliente.getNome());
    }

    @Test
    @DisplayName("CSV: conteúdo que começa com = + - @ não vira fórmula e aspas são escapadas")
    void csvSemFormula() {
        Usuario cliente = novoCliente();
        cliente.setNome("=HYPERLINK(\"http://x\") " + cliente.getId());
        usuarioRepository.saveAndFlush(cliente);

        String csv = clientes.exportarCsv(ADMIN, "127.0.0.1", "HYPERLINK", null, PeriodoFiltro.VAZIO);

        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\") " + cliente.getId() + "\"");
        assertThat(Csv.celula("+1")).isEqualTo("\"'+1\"");
        assertThat(Csv.celula("-1")).isEqualTo("\"'-1\"");
        assertThat(Csv.celula("@x")).isEqualTo("\"'@x\"");
        assertThat(Csv.celula("\tx")).isEqualTo("\"'\tx\"");
        assertThat(Csv.celula(null)).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("CSV pela rota: só admin baixa, como anexo text/csv; cliente recebe 403 e visitante vai ao login")
    void csvPelaRota() throws Exception {
        mvc.perform(get("/admin/clientes/exportar.csv").with(admin()).param("status", "ATIVA"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"clientes-")));
        mvc.perform(get("/admin/clientes/exportar.csv").with(como(novoCliente()))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/clientes/exportar.csv")).andExpect(redirectedUrlPattern("**/login*"));
        mvc.perform(get("/admin/clientes/exportar.csv").with(admin()).param("de", "2025-03-11").param("ate", "2025-03-10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("tela de clientes: campos de período com rótulo, link de exportação com os filtros e paginação")
    void telaClientes() throws Exception {
        String html = mvc.perform(get("/admin/clientes").with(admin()).param("status", "ATIVA")
                        .param("de", "2020-01-01"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<label for=\"de\"", "<label for=\"ate\"");
        assertThat(html).contains("/admin/clientes/exportar.csv?q=&amp;status=ATIVA&amp;de=2020-01-01");
    }
}
