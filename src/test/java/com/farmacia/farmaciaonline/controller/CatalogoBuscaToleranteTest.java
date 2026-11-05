package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.model.enums.TipoProduto;
import com.farmacia.farmaciaonline.repository.FiltroCatalogo;
import com.farmacia.farmaciaonline.repository.OrdenacaoCatalogo;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;
import com.farmacia.farmaciaonline.service.CatalogoService;

/** F-22: busca sem acento, filtros novos, ordenações novas e "você quis dizer" (usa o seed). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CatalogoBuscaToleranteTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CatalogoService catalogoService;

    private List<String> nomes(FiltroCatalogo filtro, OrdenacaoCatalogo ordenacao) {
        return produtoRepository.buscarCatalogo(filtro, ordenacao, PageRequest.of(0, 100))
                .getContent().stream().map(Produto::getNome).toList();
    }

    private FiltroCatalogo termo(String termo) {
        return new FiltroCatalogo(termo, null, null, null, null, false, null, null, null, null, null);
    }

    private FiltroCatalogo filtro(String marca, String principio, TipoProduto tipo, String forma, Boolean receita) {
        return new FiltroCatalogo(null, null, null, null, null, false, marca, principio, tipo, forma, receita);
    }

    private String pagina(String... parametros) throws Exception {
        MockHttpServletRequestBuilder requisicao = get("/produtos");
        for (int i = 0; i < parametros.length; i += 2) {
            requisicao.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(requisicao).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8).replace(' ', ' ');
    }

    @Test
    @DisplayName("busca ignora acento e caixa nos dois sentidos")
    void buscaSemAcento() {
        assertThat(nomes(termo("Paracetamól"), OrdenacaoCatalogo.NOME)).contains("Paracetamol 750mg");
        assertThat(nomes(termo("DIPIRONA"), OrdenacaoCatalogo.NOME))
                .isEqualTo(nomes(termo("dipirona"), OrdenacaoCatalogo.NOME)).isNotEmpty();
        assertThat(nomes(termo("capsula"), OrdenacaoCatalogo.NOME)).isEmpty(); // forma não entra na busca de texto
        assertThat(nomes(termo("Dipirôna"), OrdenacaoCatalogo.NOME)).contains("Neo Dipirona 500mg");
    }

    @Test
    @DisplayName("filtros por marca, forma, tipo, princípio ativo e receita funcionam isolados")
    void filtrosIsolados() {
        assertThat(nomes(filtro("mintlab", null, null, null, null), OrdenacaoCatalogo.NOME))
                .containsExactly("Amoxicilina 500mg");
        assertThat(nomes(filtro(null, null, null, "CAPSULA", null), OrdenacaoCatalogo.NOME))
                .contains("Omeprazol 20mg", "Amoxicilina 500mg");
        assertThat(nomes(filtro(null, "Dipirona Monoidratada", null, null, null), OrdenacaoCatalogo.NOME))
                .containsExactly("Dipirona Monoidratada 500mg", "Novalgina Dipirona 1g");
        assertThat(nomes(filtro(null, null, TipoProduto.SIMILAR, null, null), OrdenacaoCatalogo.NOME))
                .contains("Neo Dipirona 500mg").doesNotContain("Novalgina Dipirona 1g");
        assertThat(nomes(filtro(null, null, null, null, true), OrdenacaoCatalogo.NOME))
                .contains("Amoxicilina 500mg", "Clonazepam 2mg").doesNotContain("Omeprazol 20mg");
    }

    @Test
    @DisplayName("filtros novos compõem entre si e com o termo")
    void filtrosCompostos() {
        assertThat(nomes(filtro("Mintlab", null, null, "cápsula", true), OrdenacaoCatalogo.NOME))
                .containsExactly("Amoxicilina 500mg");
        assertThat(nomes(filtro("Mintlab", null, null, "cápsula", false), OrdenacaoCatalogo.NOME)).isEmpty();
        assertThat(nomes(new FiltroCatalogo("omeprazol", null, null, null, null, false, "Avivia", null, null,
                null, true), OrdenacaoCatalogo.NOME)).isEmpty();
    }

    @Test
    @DisplayName("MAIS_VENDIDOS ordena por qtd_vendida decrescente")
    void maisVendidos() {
        assertThat(nomes(termo("dipirona"), OrdenacaoCatalogo.MAIS_VENDIDOS)).containsExactly(
                "Novalgina Dipirona 1g", "Dipirona Monoidratada 500mg", "Neo Dipirona 500mg");
    }

    @Test
    @DisplayName("RELEVANCIA: nome começa com o termo antes de nome que apenas contém")
    void relevancia() {
        assertThat(nomes(termo("dipirona"), OrdenacaoCatalogo.RELEVANCIA)).containsExactly(
                "Dipirona Monoidratada 500mg", "Neo Dipirona 500mg", "Novalgina Dipirona 1g");
        // sem termo cai para NOME
        assertThat(nomes(termo(null), OrdenacaoCatalogo.RELEVANCIA))
                .isEqualTo(nomes(termo(null), OrdenacaoCatalogo.NOME));
    }

    @Test
    @DisplayName("sugere a palavra do catálogo para erro de digitação")
    void sugestao() {
        assertThat(catalogoService.sugerir("paracetamoll")).contains("paracetamol");
        assertThat(catalogoService.sugerir("dipirrona 500mg")).hasValueSatisfying(s -> assertThat(s).startsWith("dipirona"));
    }

    @Test
    @DisplayName("não sugere para termo certo, sem parecido ou palavra curta")
    void semSugestao() {
        assertThat(catalogoService.sugerir("dipirona")).isEqualTo(Optional.empty());
        assertThat(catalogoService.sugerir("xqzwkvj")).isEqualTo(Optional.empty());
        assertThat(catalogoService.sugerir("ab")).isEqualTo(Optional.empty());
        assertThat(catalogoService.sugerir("  ")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("sugestão ignora produto inativo")
    void sugestaoSoAtivos() {
        // "Loratadina 10mg" está inativo no seed
        assertThat(catalogoService.sugerir("loratadinaa")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("tela mostra 'Você quis dizer' com link para a busca corrigida")
    void telaComSugestao() throws Exception {
        String html = pagina("q", "paracetamoll");

        assertThat(html).contains("Nenhum produto encontrado");
        assertThat(html).contains("Você quis dizer:");
        assertThat(html).contains("href=\"/produtos?q=paracetamol\"");
    }

    @Test
    @DisplayName("tela sem resultado e sem parecido não mostra sugestão")
    void telaSemSugestao() throws Exception {
        String html = pagina("q", "xqzwkvj");

        assertThat(html).contains("Nenhum produto encontrado").doesNotContain("Você quis dizer");
    }

    @Test
    @DisplayName("tela traz os campos novos com label, um único h1 e opções do catálogo ativo")
    void telaComCamposNovos() throws Exception {
        String html = pagina();

        for (String campo : List.of("marca", "principioAtivo", "tipo", "forma", "receita")) {
            assertThat(html).contains("<label for=\"" + campo + "\"").contains("id=\"" + campo + "\"");
        }
        assertThat(html.split("<h1").length - 1).isEqualTo(1);
        assertThat(html).contains("<option value=\"Mintlab\"").contains("value=\"MAIS_VENDIDOS\"");
    }

    @Test
    @DisplayName("tela filtra por marca + receita e mantém a seleção nos campos")
    void telaFiltra() throws Exception {
        String html = pagina("marca", "Mintlab", "receita", "sim");

        assertThat(html).contains("Amoxicilina 500mg").doesNotContain("Omeprazol 20mg");
        assertThat(pagina("marca", "Mintlab", "receita", "nao")).doesNotContain("Amoxicilina 500mg");
        assertThat(html).containsPattern("<option value=\"sim\"\\s+selected");
    }

    @Test
    @DisplayName("tipo inválido é ignorado (200, sem erro)")
    void tipoInvalidoIgnorado() throws Exception {
        assertThat(pagina("tipo", "XYZ")).contains("Novalgina Dipirona 1g");
    }

    @Test
    @DisplayName("paginação preserva os filtros novos e a ordenação")
    void paginacaoPreservaFiltros() throws Exception {
        String html = pagina("ordenacao", "MAIS_VENDIDOS", "receita", "nao");

        assertThat(html).contains("Próxima");
        assertThat(html).containsPattern("href=\"[^\"]*receita=nao[^\"]*pagina=1[^\"]*\"");
        assertThat(html).containsPattern("href=\"[^\"]*ordenacao=MAIS_VENDIDOS[^\"]*pagina=1[^\"]*\"");
    }
}
