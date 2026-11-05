package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.ProdutoForm;
import com.farmacia.farmaciaonline.security.CarrinhoCookie;
import com.farmacia.farmaciaonline.service.ProdutoService;

/**
 * O admin desativa ou exclui um produto que está no carrinho de clientes. Fora da transação do teste, para valerem o
 * commit e o aviso gravado depois dele; cada teste cria o próprio produto e apaga o que criou.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProdutoInativoNoCarrinhoIntegracaoTest {

    private static final String NOME = "Produto Teste Carrinho";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProdutoService produtos;

    private Long produtoId;
    private Long apresentacaoId;
    private String descricao;
    private Long pedidoId;

    /** Produto novo com uma apresentação, a partir de um produto ativo do seed (outro nome, SKU e EAN). */
    @BeforeEach
    void criarProduto() {
        Long modelo = jdbc.queryForObject("select min(p.id) from produto p join apresentacao a on a.produto_id = p.id "
                + "where p.ativo and a.ativa and not p.exige_receita", Long.class);
        long n = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
        ProdutoForm form = produtos.formDe(modelo);
        form.setNome(NOME + " " + n);
        ProdutoForm.ApresentacaoForm apresentacao = form.getApresentacoes().get(0);
        apresentacao.setId(null);
        apresentacao.setVersao(null);
        apresentacao.setSku("TESTE-" + n);
        apresentacao.setEan("9" + n + "00");
        apresentacao.setPreco(new BigDecimal("12.50"));
        apresentacao.setEstoque(20);
        apresentacao.setAtiva(true);
        form.getApresentacoes().subList(1, form.getApresentacoes().size()).clear();
        form.setAtivo(true);
        produtos.salvar(null, form, null);

        apresentacaoId = jdbc.queryForObject("select id from apresentacao where sku = ?", Long.class, "TESTE-" + n);
        produtoId = jdbc.queryForObject("select produto_id from apresentacao where id = ?", Long.class, apresentacaoId);
        descricao = apresentacao.getDescricao();
    }

    @AfterEach
    void apagarProduto() {
        if (pedidoId != null) {
            jdbc.update("delete from pedido where id = ?", pedidoId);
        }
        // Tira dos carrinhos antes, para a limpeza não deixar aviso de item removido para o próximo teste.
        jdbc.update("delete from item_carrinho where apresentacao_id = ?", apresentacaoId);
        if (jdbc.queryForObject("select count(*) from produto where id = ?", Integer.class, produtoId) > 0) {
            produtos.excluir(produtoId, null);
        }
    }

    private String nomeCompleto() {
        return jdbc.queryForObject("select nome from produto where id = ?", String.class, produtoId) + " " + descricao;
    }

    private MockHttpSession entrar(String email) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("identificador", email).param("senha", "Farma@123"))
                .andReturn().getRequest().getSession(false);
    }

    private void adicionar(MockHttpSession cliente) throws Exception {
        mvc.perform(post("/carrinho/itens").with(csrf()).session(cliente).param("apresentacaoId", apresentacaoId.toString()))
                .andExpect(redirectedUrl("/carrinho"));
    }

    private Cookie adicionarComoVisitante() throws Exception {
        MockHttpServletResponse resposta = mvc.perform(post("/carrinho/itens").with(csrf())
                        .param("apresentacaoId", apresentacaoId.toString()))
                .andExpect(redirectedUrl("/carrinho")).andReturn().getResponse();
        return resposta.getCookie(CarrinhoCookie.NOME);
    }

    /** Os campos do formulário de edição, como o navegador os manda (cada chave uma vez). */
    private Map<String, String> formulario(boolean ativo) {
        ProdutoForm form = produtos.formDe(produtoId);
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("versao", String.valueOf(form.getVersao()));
        campos.put("nome", form.getNome());
        campos.put("marca", form.getMarca());
        campos.put("principioAtivo", texto(form.getPrincipioAtivo()));
        campos.put("tipo", form.getTipo() == null ? "" : form.getTipo().name());
        campos.put("formaFarmaceutica", texto(form.getFormaFarmaceutica()));
        campos.put("tarja", form.getTarja().name());
        campos.put("registroMs", texto(form.getRegistroMs()));
        campos.put("bulaUrl", texto(form.getBulaUrl()));
        campos.put("indicacao", texto(form.getIndicacao()));
        campos.put("acao", texto(form.getAcao()));
        campos.put("viaAdministracao", texto(form.getViaAdministracao()));
        campos.put("publicoAlvo", texto(form.getPublicoAlvo()));
        campos.put("_exigeReceita", "on");
        if (ativo) {
            campos.put("ativo", "true");
        }
        campos.put("_ativo", "on");
        for (int i = 0; i < form.getCategoriaIds().size(); i++) {
            campos.put("categoriaIds[" + i + "]", String.valueOf(form.getCategoriaIds().get(i)));
        }
        ProdutoForm.ApresentacaoForm a = form.getApresentacoes().get(0);
        campos.put("apresentacoes[0].id", String.valueOf(a.getId()));
        campos.put("apresentacoes[0].versao", String.valueOf(a.getVersao()));
        campos.put("apresentacoes[0].descricao", a.getDescricao());
        campos.put("apresentacoes[0].sku", a.getSku());
        campos.put("apresentacoes[0].ean", a.getEan());
        campos.put("apresentacoes[0].preco", a.getPreco().toPlainString());
        campos.put("apresentacoes[0].estoque", String.valueOf(a.getEstoque()));
        campos.put("apresentacoes[0].limitePorPedido", String.valueOf(a.getLimitePorPedido()));
        campos.put("apresentacoes[0].ativa", "true");
        campos.put("apresentacoes[0]._ativa", "on");
        return campos;
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private MockHttpServletResponse executar(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andReturn().getResponse();
    }

    private String html(MockHttpServletRequestBuilder requisicao) throws Exception {
        MockHttpServletResponse resposta = executar(requisicao);
        assertThat(resposta.getStatus()).isEqualTo(200);
        return resposta.getContentAsString(StandardCharsets.UTF_8);
    }

    private void excluir(MockHttpSession admin) throws Exception {
        mvc.perform(post("/admin/produtos/" + produtoId + "/excluir").with(csrf()).session(admin))
                .andExpect(redirectedUrl("/admin/produtos"))
                .andExpect(flash().attributeExists("sucesso"));
    }

    @Test
    @DisplayName("desativar pelo formulário um produto no carrinho: o admin volta à lista e o cliente vê o item indisponível")
    void desativarComItemNoCarrinho() throws Exception {
        MockHttpSession cliente = entrar("cliente@farma.com");
        adicionar(cliente);
        Cookie visitante = adicionarComoVisitante();
        Long itemId = jdbc.queryForObject("select i.id from item_carrinho i join carrinho c on c.id = i.carrinho_id "
                + "where c.usuario_id is not null and i.apresentacao_id = ?", Long.class, apresentacaoId);

        MockHttpServletRequestBuilder envio = post("/admin/produtos/" + produtoId).with(csrf())
                .session(entrar("admin@farma.com"));
        formulario(false).forEach(envio::param);
        mvc.perform(envio).andExpect(redirectedUrl("/admin/produtos"))
                .andExpect(flash().attribute("sucesso", "Produto salvo."));
        assertThat(jdbc.queryForObject("select ativo from produto where id = ?", Boolean.class, produtoId)).isFalse();

        assertThat(html(get("/carrinho").session(cliente)))
                .contains("Estes itens não estão mais à venda e ficaram fora do total:")
                .contains(nomeCompleto()).contains("Fora do total");
        assertThat(html(get("/carrinho").cookie(visitante))).contains("não estão mais à venda");
        for (String url : new String[] {"/", "/produtos", "/carrinho/itens/" + itemId + "/remover", "/checkout"}) {
            html(get(url).session(cliente));
        }
        assertThat(executar(get("/produtos/" + produtoId).session(cliente)).getStatus()).isEqualTo(404);
        assertThat(executar(get("/carrinho/finalizar").session(cliente)).getRedirectedUrl()).isEqualTo("/carrinho");
    }

    @Test
    @DisplayName("excluir um produto sem pedido que está em carrinhos: apaga, e cada dono é avisado uma vez")
    void excluirSemPedidoAvisaOsCarrinhos() throws Exception {
        MockHttpSession cliente = entrar("cliente@farma.com");
        adicionar(cliente);
        Cookie visitante = adicionarComoVisitante();
        String nome = nomeCompleto();

        excluir(entrar("admin@farma.com"));
        assertThat(jdbc.queryForObject("select count(*) from produto where id = ?", Integer.class, produtoId)).isZero();

        assertThat(html(get("/carrinho").session(cliente)))
                .contains("Estes itens saíram do catálogo e foram tirados do seu carrinho:").contains(nome);
        assertThat(html(get("/carrinho").session(cliente))).doesNotContain("saíram do catálogo");
        assertThat(html(get("/carrinho").cookie(visitante))).contains("saíram do catálogo").contains(nome);
        html(get("/").session(cliente));
        html(get("/produtos").session(cliente));
    }

    @Test
    @DisplayName("excluir um produto com pedido que está no carrinho: só inativa, e o item fica como indisponível")
    void excluirComPedidoSoInativa() throws Exception {
        MockHttpSession cliente = entrar("cliente@farma.com");
        adicionar(cliente);
        Long clienteId = jdbc.queryForObject("select id from usuario where email = 'cliente@farma.com'", Long.class);
        pedidoId = jdbc.queryForObject("insert into pedido (usuario_id, status, tipo_entrega, loja_retirada_id, subtotal, "
                + "frete, total) values (?, 'PAGAMENTO_APROVADO', 'RETIRADA', (select min(id) from loja), 12.50, 0, 12.50) "
                + "returning id", Long.class, clienteId);
        jdbc.update("insert into item_pedido (pedido_id, apresentacao_id, nome_produto, descricao_apresentacao, sku, "
                + "preco_unitario, quantidade) select ?, a.id, p.nome, a.descricao, a.sku, a.preco, 1 "
                + "from apresentacao a join produto p on p.id = a.produto_id where a.id = ?", pedidoId, apresentacaoId);

        excluir(entrar("admin@farma.com"));
        assertThat(jdbc.queryForObject("select ativo from produto where id = ?", Boolean.class, produtoId)).isFalse();

        assertThat(html(get("/carrinho").session(cliente))).contains("não estão mais à venda").contains(nomeCompleto())
                .doesNotContain("saíram do catálogo");
    }
}
