package com.farmacia.farmaciaonline.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.model.ImagemProduto;
import com.farmacia.farmaciaonline.model.Produto;

/**
 * Mapeamento do catálogo contra o PostgreSQL real, usando os dados do V2__seed.sql.
 * Cada teste é desfeito no fim (@DataJpaTest é transacional).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CatalogoRepositoryTest {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ApresentacaoRepository apresentacaoRepository;

    @Autowired
    private ImagemProdutoRepository imagemProdutoRepository;

    private Produto produto(String nome) {
        return produtoRepository.findAll().stream()
                .filter(p -> p.getNome().equals(nome))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("findByAtivoTrue não traz produto inativo")
    void findByAtivoTrueNaoTrazInativo() {
        Page<Produto> pagina = produtoRepository.findByAtivoTrue(PageRequest.of(0, 100));

        assertThat(pagina.getContent()).isNotEmpty().allMatch(Produto::isAtivo);
        assertThat(pagina.getContent()).extracting(Produto::getNome).doesNotContain("Loratadina 10mg");
        assertThat(pagina.getTotalElements()).isLessThan(produtoRepository.count());
    }

    @Test
    @DisplayName("findByAtivoTrue respeita o tamanho da página")
    void findByAtivoTrueRespeitaOTamanhoDaPagina() {
        Page<Produto> pagina = produtoRepository.findByAtivoTrue(PageRequest.of(0, 5));

        assertThat(pagina.getContent()).hasSize(5);
        assertThat(pagina.getTotalPages()).isGreaterThan(1);
    }

    @Test
    @DisplayName("categorias raiz ativas vêm na ordem de exibição")
    void categoriasRaizVemEmOrdem() {
        List<Categoria> raizes = categoriaRepository.findByCategoriaPaiIsNullAndAtivaTrueOrderByOrdemAsc();

        assertThat(raizes).extracting(Categoria::getNome)
                .contains("Medicamentos", "Saúde e bem-estar", "Beleza e cuidados pessoais",
                        "Mamãe e bebê", "Casa e outros");
        assertThat(raizes).extracting(Categoria::getOrdem).isSorted();
    }

    @Test
    @DisplayName("subcategorias ativas vêm na ordem de exibição e sem as inativas")
    void subcategoriasAtivasVemEmOrdemESemAsInativas() {
        Categoria medicamentos = categoriaRepository.findByCategoriaPaiIsNullAndAtivaTrueOrderByOrdemAsc().get(0);
        assertThat(medicamentos.getNome()).isEqualTo("Medicamentos");

        Categoria inativa = new Categoria();
        inativa.setNome("Subcategoria inativa");
        inativa.setCategoriaPai(medicamentos);
        inativa.setAtiva(false);
        inativa.setOrdem(99);
        categoriaRepository.saveAndFlush(inativa);

        List<Categoria> filhas = categoriaRepository.findByCategoriaPaiIdAndAtivaTrueOrderByOrdemAsc(medicamentos.getId());

        assertThat(filhas).extracting(Categoria::getNome)
                .startsWith("Dor e febre", "Gripe e resfriado")
                .doesNotContain("Subcategoria inativa");
        assertThat(filhas).extracting(Categoria::getOrdem).isSorted();
    }

    @Test
    @DisplayName("Novalgina: apresentações em ordem, padrão de 20 comprimidos a R$ 30,99")
    void novalginaTemApresentacoesEmOrdemEPadraoDe20() {
        Produto novalgina = produto("Novalgina Dipirona 1g");

        assertThat(novalgina.getApresentacoes()).extracting(Apresentacao::getDescricao)
                .containsExactly("20 comprimidos", "10 comprimidos", "30 comprimidos");
        assertThat(novalgina.getApresentacaoPadrao().getDescricao()).isEqualTo("20 comprimidos");
        assertThat(novalgina.getApresentacaoPadrao().getPreco()).isEqualByComparingTo("30.99");
        assertThat(novalgina.isIndisponivel()).isFalse();
    }

    @Test
    @DisplayName("produto sem estoque em nenhuma apresentação é indisponível")
    void produtoSemEstoqueEhIndisponivel() {
        assertThat(produto("Termômetro Digital").isIndisponivel()).isTrue();
    }

    @Test
    @DisplayName("produto com duas categorias mapeia o N:N")
    void produtoComDuasCategorias() {
        Produto protetor = produto("Protetor Solar Facial FPS 60");

        assertThat(protetor.getCategorias()).extracting(Categoria::getNome)
                .containsExactlyInAnyOrder("Proteção solar", "Pele e rosto");
    }

    @Test
    @DisplayName("apresentação é encontrada por SKU e por EAN")
    void apresentacaoPorSkuEEan() {
        Apresentacao apresentacao = apresentacaoRepository.findBySku("NOV-DIP1G-20").orElseThrow();

        assertThat(apresentacao.getEan()).isEqualTo("7891000000021");
        assertThat(apresentacaoRepository.findByEan("7891000000021").map(Apresentacao::getSku))
                .contains("NOV-DIP1G-20");
        assertThat(apresentacaoRepository.existsBySku("NOV-DIP1G-20")).isTrue();
        assertThat(apresentacaoRepository.existsByEan("7891000000021")).isTrue();
        assertThat(apresentacaoRepository.existsBySku("SKU-QUE-NAO-EXISTE")).isFalse();
        assertThat(apresentacaoRepository.existsByEan("0000000000000")).isFalse();
    }

    @Test
    @DisplayName("a versão (lock otimista) sobe ao atualizar o estoque")
    void versaoSobeAoAtualizarOEstoque() {
        Apresentacao apresentacao = apresentacaoRepository.findBySku("NOV-DIP1G-20").orElseThrow();
        Long antes = apresentacao.getVersao();

        apresentacao.setEstoque(apresentacao.getEstoque() - 1);
        apresentacaoRepository.saveAndFlush(apresentacao);

        assertThat(apresentacao.getVersao()).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("imagens do produto vêm pela ordem")
    void imagensDoProdutoVemPelaOrdem() {
        Produto novalgina = produto("Novalgina Dipirona 1g");
        imagemProdutoRepository.saveAndFlush(imagem(novalgina, "/images/produtos/segunda.webp", 2));
        imagemProdutoRepository.saveAndFlush(imagem(novalgina, "/images/produtos/primeira.webp", 1));

        List<ImagemProduto> imagens = imagemProdutoRepository.findByProdutoIdOrderByOrdemAsc(novalgina.getId());

        assertThat(imagens).hasSize(3);
        assertThat(imagens).extracting(ImagemProduto::getOrdem).isSorted();
    }

    private ImagemProduto imagem(Produto produto, String arquivo, int ordem) {
        ImagemProduto imagem = new ImagemProduto();
        imagem.setProduto(produto);
        imagem.setArquivo(arquivo);
        imagem.setOrdem(ordem);
        return imagem;
    }
}
