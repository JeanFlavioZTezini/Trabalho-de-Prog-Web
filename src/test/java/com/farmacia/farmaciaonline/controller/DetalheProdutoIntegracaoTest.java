package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.ImagemProduto;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.CarrinhoRepository;
import com.farmacia.farmaciaonline.repository.ImagemProdutoRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;
import com.farmacia.farmaciaonline.security.CarrinhoCookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DetalheProdutoIntegracaoTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ApresentacaoRepository apresentacaoRepository;

    @Autowired
    private ImagemProdutoRepository imagemProdutoRepository;

    @Autowired
    private CarrinhoRepository carrinhoRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("migalhas levam do início à categoria mais específica do produto, com a página atual marcada")
    void migalhasComCategoria() throws Exception {
        String html = pagina("/produtos/4");

        assertThat(html).containsPattern("(?s)<nav class=\"migalhas\" aria-label=\"Você está em\">\\s*<ol>"
                + "\\s*<li><a href=\"/\">Início</a></li>"
                + "\\s*<li>\\s*<a href=\"/produtos\\?categoria=1\">Medicamentos</a>\\s*</li>"
                + "\\s*<li>\\s*<a href=\"/produtos\\?categoria=11\">Dor e febre</a>\\s*</li>"
                + "\\s*<li aria-current=\"page\">[^<]+</li>");
        assertThat(html).doesNotContain("Gripe e resfriado</a></li>");
    }

    @Test
    @DisplayName("sem categoria ativa, as migalhas vão de Início para Produtos")
    void migalhasSemCategoriaAtiva() throws Exception {
        entityManager.flush();
        entityManager.createNativeQuery("update categoria set ativa = false where id = 11").executeUpdate();
        entityManager.clear();

        String html = pagina("/produtos/1");

        assertThat(html).containsPattern("(?s)<li><a href=\"/\">Início</a></li>\\s*<li><a href=\"/produtos\">Produtos</a></li>"
                + "\\s*<li aria-current=\"page\">Novalgina Dipirona 1g</li>");
        assertThat(html).doesNotContain(">Dor e febre</a>");
    }

    @Test
    @DisplayName("detalhe público exibe descrição, preço, avisos e compra da apresentação ativa")
    void detalheExibeDadosEIncrementaVisualizacoes() throws Exception {
        long visualizacoesAntes = produtoRepository.findById(1L).orElseThrow().getVisualizacoes();

        String html = pagina("/produtos/1");

        assertThat(html).contains("Novalgina Dipirona 1g");
        assertThat(html).contains("Alívio da dor");
        assertThat(html).contains("Redução da febre");
        assertThat(html).contains("R$ 30,99");
        assertThat(html).contains("20 comprimidos");
        assertThat(html).contains("7891000000021");
        // Pagamento numa linha dentro da caixa de compra (depois do botão), e não mais numa seção própria no fim.
        assertThat(html).containsPattern("(?s)<div class=\"produto-detalhe__compra\">.*?Adicionar ao carrinho.*?"
                + "<p class=\"produto-detalhe__pagamento\">\\s*<strong>Formas de pagamento:</strong>\\s*"
                + "<span>Cartão de crédito</span>, <span>Cartão de débito</span>, <span>Pix</span> e <span>Boleto</span>\\.");
        assertThat(html).doesNotContain("id=\"titulo-pagamento\"");
        assertThat(html).containsPattern("(?s)<input type=\"hidden\" name=\"apresentacaoId\"\\s+value=\"2\">");
        assertThat(html).contains("name=\"quantidade\"", "max=\"10\"", "name=\"_csrf\"");
        assertThat(html).contains("alt=\"Novalgina Dipirona 1g\"");

        entityManager.flush();
        entityManager.clear();
        assertThat(produtoRepository.findById(1L).orElseThrow().getVisualizacoes())
                .isEqualTo(visualizacoesAntes + 1);
    }

    @Test
    @DisplayName("trocar para apresentação sem estoque atualiza preço e desativa a compra")
    void apresentaEstadoSemEstoque() throws Exception {
        String html = pagina("/produtos/1?apresentacaoId=1");

        assertThat(html).contains("10 comprimidos");
        assertThat(html).contains("R$ 20,99");
        assertThat(html).contains("Indisponível para compra nesta apresentação.");
        assertThat(html).contains("name=\"quantidade\"");
        assertThat(html).contains("disabled");
        assertThat(html).contains("alt=\"Novalgina Dipirona 1g\"");
    }

    @Test
    @DisplayName("imagem específica e descrição mudam com a apresentação selecionada")
    void imagemEspecificaPertenceASelecaoAtual() throws Exception {
        Produto produto = produtoRepository.findById(1L).orElseThrow();
        Apresentacao apresentacao = apresentacaoRepository.findById(3L).orElseThrow();
        ImagemProduto imagem = new ImagemProduto();
        imagem.setProduto(produto);
        imagem.setApresentacao(apresentacao);
        imagem.setArquivo("/images/produtos/dipirona-monoidratada-500mg-20-comprimidos.webp");
        imagem.setOrdem(0);
        imagemProdutoRepository.saveAndFlush(imagem);

        String html = pagina("/produtos/1?apresentacaoId=3");

        assertThat(html).contains("30 comprimidos");
        assertThat(html).contains("src=\"/images/produtos/dipirona-monoidratada-500mg-20-comprimidos.webp\"");
        assertThat(html).contains("alt=\"Novalgina Dipirona 1g — 30 comprimidos\"");
    }

    @Test
    @DisplayName("não aceita apresentação de outro produto nem apresentação inativa")
    void recusaApresentacoesQueNaoPertencemAoProdutoOuEstaoInativas() throws Exception {
        mvc.perform(get("/produtos/1").param("apresentacaoId", "4"))
                .andExpect(status().isNotFound());

        Apresentacao apresentacao = apresentacaoRepository.findById(2L).orElseThrow();
        apresentacao.setAtiva(false);
        apresentacaoRepository.saveAndFlush(apresentacao);

        mvc.perform(get("/produtos/1").param("apresentacaoId", "2"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("produto inexistente ou inativo responde 404 sem exibir detalhes")
    void produtoInexistenteOuInativoResponde404() throws Exception {
        mvc.perform(get("/produtos/9999")).andExpect(status().isNotFound());
        mvc.perform(get("/produtos/19")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("link de bula com esquema inseguro não é aberto e recebe orientação")
    void naoAbreLinkDeBulaInseguro() throws Exception {
        Produto produto = produtoRepository.findById(1L).orElseThrow();
        produto.setBulaUrl("javascript:alert(1)");
        produtoRepository.saveAndFlush(produto);

        String html = pagina("/produtos/1");

        assertThat(html).contains("O link da bula cadastrado não é válido e não pode ser aberto.");
        assertThat(html).doesNotContain("href=\"javascript:alert(1)\"");
    }

    @Test
    @DisplayName("comprar envia apresentação correta e o servidor limita a quantidade no carrinho")
    void comprarAdicionaApresentacaoERevalidaQuantidade() throws Exception {
        MvcResult resultado = mvc.perform(post("/carrinho/itens")
                        .with(csrf())
                        .param("apresentacaoId", "2")
                        .param("quantidade", "12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/carrinho"))
                .andReturn();

        String token = resultado.getResponse().getCookie(CarrinhoCookie.NOME).getValue();
        assertThat(token).isNotBlank();
        assertThat(carrinhoRepository.findByToken(java.util.UUID.fromString(token)))
                .isPresent()
                .get()
                .extracting(carrinho -> carrinho.getItens().get(0).getApresentacao().getId())
                .isEqualTo(2L);
        assertThat(carrinhoRepository.findByToken(java.util.UUID.fromString(token)).orElseThrow()
                .getItens().get(0).getQuantidade()).isEqualTo(10);
    }

    private String pagina(String url) throws Exception {
        return mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8)
                .replace('\u00a0', ' ');
    }
}
