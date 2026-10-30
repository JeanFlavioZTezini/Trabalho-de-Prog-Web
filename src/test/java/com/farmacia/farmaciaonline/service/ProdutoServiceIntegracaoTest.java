package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.model.ProdutoForm;
import com.farmacia.farmaciaonline.model.ProdutoForm.ApresentacaoForm;
import com.farmacia.farmaciaonline.model.enums.Tarja;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** UC12 contra o seed (V2): admin id 1, categoria 11 (Dor e febre), Novalgina = produto 1. Precisa do Docker. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ProdutoServiceIntegracaoTest {

    private static final long ADMIN = 1L;
    private static final long DOR_E_FEBRE = 11L;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private ProdutoService service;
    @Autowired
    private ProdutoRepository produtoRepository;
    @Autowired
    private ApresentacaoRepository apresentacaoRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @PersistenceContext
    private EntityManager em;

    private static ProdutoForm form(String nome, String sku, String ean) {
        ProdutoForm f = new ProdutoForm();
        f.setNome(nome);
        f.setMarca("Marca");
        f.getCategoriaIds().add(DOR_E_FEBRE);
        ApresentacaoForm a = new ApresentacaoForm();
        a.setDescricao("10 comprimidos");
        a.setSku(sku);
        a.setEan(ean);
        a.setPreco(new BigDecimal("9.90"));
        a.setEstoque(5);
        f.getApresentacoes().add(a);
        return f;
    }

    private Long idPorNome(String nome) {
        return produtoRepository.findAll().stream().filter(p -> p.getNome().equals(nome)).findFirst().orElseThrow().getId();
    }

    @Test
    void cadastroAparecePeloCatalogoEGravaAdmin() {
        service.salvar(null, form("Produto Teste UC12", "UC12-A", "7890000000011"), ADMIN);
        em.flush();
        em.clear();

        Produto p = produtoRepository.findById(idPorNome("Produto Teste UC12")).orElseThrow();
        assertThat(p.getAtualizadoPor().getId()).isEqualTo(ADMIN);
        assertThat(p.getApresentacoes()).hasSize(1);
        assertThat(produtoRepository.findByAtivoTrue(PageRequest.of(0, 1000)).getContent())
                .extracting(Produto::getNome).contains("Produto Teste UC12");
        assertThat(service.buscar("uc12-a", "ativos", PageRequest.of(0, 10)).getContent()).hasSize(1);
    }

    @Test
    void violacoesGeramMensagem() {
        ProdutoForm f = form("Ruim", "NOV-DIP1G-20", "abc");          // SKU do seed, EAN inválido
        f.getCategoriaIds().clear();                                    // sem categoria
        f.setTarja(Tarja.VERMELHA);                                     // sem exigir receita
        f.getApresentacoes().get(0).setPreco(BigDecimal.ZERO);
        f.getApresentacoes().get(0).setEstoque(-1);
        assertThatThrownBy(() -> service.salvar(null, f, ADMIN))
                .isInstanceOfSatisfying(ProdutoException.class, e -> assertThat(e.getErros()).hasSize(6));
    }

    @Test
    void estoqueAlteradoPorCompraNaoESobrescrito() {
        Long id = 1L;
        ProdutoForm f = service.formDe(id);
        jdbc.update("UPDATE apresentacao SET estoque = estoque - 1, versao = versao + 1 WHERE id = 2"); // "compra"
        em.clear();
        assertThatThrownBy(() -> service.salvar(id, f, ADMIN))
                .isInstanceOf(ProdutoException.class).hasMessageContaining("alterada por outra operação");
    }

    @Test
    void edicaoSemConflitoMantemApresentacoesEImagemVinculada() {
        ProdutoForm f = service.formDe(1L);
        f.setNome("Novalgina Dipirona 1g (editado)");
        f.setImagemApresentacaoId(2L);
        f.getNovasImagens().add(new MockMultipartFile("novasImagens", "foto.png", "image/png", PNG));
        service.salvar(1L, f, ADMIN);
        em.flush();
        em.clear();

        assertThat(apresentacaoRepository.count()).isGreaterThanOrEqualTo(3);
        List<String> arquivos = service.imagensDe(1L).stream().map(i -> i.getArquivo()).toList();
        assertThat(arquivos).anyMatch(a -> a.startsWith(ArmazenamentoImagem.URL_BASE));
        service.imagensDe(1L).stream().map(i -> i.getArquivo()).filter(a -> a.startsWith(ArmazenamentoImagem.URL_BASE))
                .forEach(a -> assertThat(a).doesNotContain(".."));

        ProdutoForm invalido = service.formDe(1L);
        invalido.setImagemApresentacaoId(4L); // apresentação de outro produto
        invalido.getNovasImagens().add(new MockMultipartFile("novasImagens", "foto.png", "image/png", PNG));
        assertThatThrownBy(() -> service.salvar(1L, invalido, ADMIN)).isInstanceOf(ProdutoException.class);
    }

    @Test
    void produtoSemPedidoEApagadoDeVez() {
        service.salvar(null, form("Descartavel", "UC12-B", "7890000000028"), ADMIN);
        Long id = idPorNome("Descartavel");
        assertThat(service.excluir(id, ADMIN)).isTrue();
        assertThat(produtoRepository.findById(id)).isEmpty();
    }

    @Test
    void produtoComPedidoSoEInativadoEHistoricoFicaIntacto() {
        service.salvar(null, form("Vendido", "UC12-C", "7890000000035"), ADMIN);
        Long id = idPorNome("Vendido");
        Long apId = apresentacaoRepository.findBySku("UC12-C").orElseThrow().getId();
        jdbc.update("""
                INSERT INTO pedido (id, usuario_id, tipo_entrega, loja_retirada_id, subtotal, frete, total)
                VALUES (900000, 2, 'RETIRADA', (SELECT MIN(id) FROM loja), 9.90, 0, 9.90)""");
        jdbc.update("""
                INSERT INTO item_pedido (pedido_id, apresentacao_id, nome_produto, descricao_apresentacao, sku, preco_unitario, quantidade)
                VALUES (900000, ?, 'Vendido', '10 comprimidos', 'UC12-C', 9.90, 1)""", apId);

        assertThat(service.excluir(id, ADMIN)).isFalse();
        em.flush();
        em.clear();

        assertThat(produtoRepository.findById(id).orElseThrow().isAtivo()).isFalse();
        assertThat(produtoRepository.findByAtivoTrue(PageRequest.of(0, 1000)).getContent())
                .extracting(Produto::getNome).doesNotContain("Vendido");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM item_pedido WHERE pedido_id = 900000", Integer.class))
                .isEqualTo(1);
    }
}
