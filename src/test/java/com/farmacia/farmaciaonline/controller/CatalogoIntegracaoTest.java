package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CatalogoIntegracaoTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApresentacaoRepository apresentacaoRepository;

    private String pagina(String... parametros) throws Exception {
        MockHttpServletRequestBuilder requisicao = get("/produtos");
        for (int i = 0; i < parametros.length; i += 2) {
            requisicao.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(requisicao)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8)
                .replace('\u00a0', ' ');
    }

    @Test
    @DisplayName("combina categoria e termo sem listar produtos de outras categorias")
    void combinaCategoriaETermo() throws Exception {
        String html = pagina("categoria", "11", "q", "dipirona");

        assertThat(html).contains("Novalgina Dipirona 1g");
        assertThat(html).contains("Dipirona Monoidratada 500mg");
        assertThat(html).doesNotContain("Shampoo Anticaspa Clássico");
    }

    @ParameterizedTest
    @CsvSource({
            "Sanofi,Novalgina Dipirona 1g",
            "dipirona monoidratada,Dipirona Monoidratada 500mg",
            "febre,Novalgina Dipirona 1g"
    })
    @DisplayName("busca por marca, princípio ativo e indicação")
    void buscaPorDadosDoProduto(String termo, String produtoEsperado) throws Exception {
        assertThat(pagina("q", termo)).contains(produtoEsperado);
    }

    @Test
    @DisplayName("busca pelo EAN de uma apresentação")
    void buscaPorEan() throws Exception {
        String html = pagina("q", "7891000000021");

        assertThat(html).contains("Novalgina Dipirona 1g");
        assertThat(html).contains("20 comprimidos");
        assertThat(html).doesNotContain("Dipirona Monoidratada 500mg");
    }

    @Test
    @DisplayName("busca apenas por categoria funciona sem termo textual")
    void buscaSomentePorCategoria() throws Exception {
        String html = pagina("categoria", "11");

        assertThat(html).contains("Novalgina Dipirona 1g");
        assertThat(html).contains("Resultados");
    }

    @Test
    @DisplayName("categoria raiz inclui produtos das subcategorias")
    void buscaPorCategoriaRaiz() throws Exception {
        String html = pagina("categoria", "1");

        assertThat(html).contains("Novalgina Dipirona 1g");
        assertThat(html).contains("Amoxicilina 500mg");
    }

    @Test
    @DisplayName("produto sem estoque continua visível com indicação de indisponibilidade")
    void produtoSemEstoquePermaneceNaLista() throws Exception {
        String html = pagina("q", "Termômetro Digital");

        assertThat(html).contains("Termômetro Digital");
        assertThat(html).contains("Indisponível");
    }

    @Test
    @DisplayName("filtro de disponibilidade esconde produtos sem estoque")
    void filtroDisponibilidadeEscondeProdutoSemEstoque() throws Exception {
        String html = pagina("q", "Termômetro Digital", "disponivel", "true");

        assertThat(html).doesNotContain("<p class=\"nome\">Termômetro Digital</p>");
        assertThat(html).contains("Nenhum produto encontrado");
    }

    @Test
    @DisplayName("produto inativo não aparece nos resultados")
    void produtoInativoNaoAparece() throws Exception {
        String html = pagina("q", "Loratadina");

        assertThat(html).doesNotContain("Loratadina 10mg");
        assertThat(html).contains("Nenhum produto encontrado");
    }

    @Test
    @DisplayName("preço e apresentação vêm da primeira apresentação ativa")
    void ignoraApresentacaoInativaAoExibirPreco() throws Exception {
        Apresentacao apresentacaoPadrao = apresentacaoRepository.findBySku("NOV-DIP1G-20").orElseThrow();
        apresentacaoPadrao.setAtiva(false);
        apresentacaoRepository.saveAndFlush(apresentacaoPadrao);

        String html = pagina("q", "Novalgina");

        assertThat(html).contains("10 comprimidos");
        assertThat(html).contains("R$ 20,99");
        assertThat(html).doesNotContain("R$ 30,99");
    }

    @Test
    @DisplayName("faixa de preço compara o preço da apresentação ativa exibida")
    void filtraPeloPrecoDaApresentacaoDeReferencia() throws Exception {
        String html = pagina("q", "Novalgina", "precoMin", "31");

        assertThat(html).contains("Nenhum produto encontrado");
        assertThat(html).doesNotContain("<p class=\"nome\">Novalgina Dipirona 1g</p>");
    }

    @Test
    @DisplayName("preserva preço, disponibilidade e ordenação nos links de paginação")
    void preservaFiltrosAoPaginar() throws Exception {
        String html = pagina("precoMin", "5", "disponivel", "true",
                "ordenacao", "PRECO_DESC");

        assertThat(html).contains("precoMin=5");
        assertThat(html).contains("disponivel=true");
        assertThat(html).contains("ordenacao=PRECO_DESC");
        assertThat(html).contains("pagina=1");
    }

    @Test
    @DisplayName("busca sem filtros exibe uma página coerente do catálogo ativo")
    void termoVazioSemFiltrosListaCatalogoAtivo() throws Exception {
        String html = pagina("q", "");

        assertThat(html).contains("Novalgina Dipirona 1g");
        assertThat(html).contains("Resultados");
        assertThat(html).doesNotContain("Loratadina 10mg");
    }
}
