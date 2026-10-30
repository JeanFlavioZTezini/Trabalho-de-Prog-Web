package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.controller.NavegacaoAdvice;
import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.model.CategoriaForm;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * UC13 contra o seed (V2__seed.sql), num PostgreSQL descartável (precisa do Docker ligado).
 * Seed usado: 1 = Medicamentos (raiz com 6 filhas); 11 = Dor e febre (produtos 1 a 4);
 * 12 = Gripe e resfriado (produto 4). O que o teste grava é desfeito no fim (@Transactional).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CategoriaServiceIntegracaoTest {

    private static final long MEDICAMENTOS = 1L;
    private static final long DOR_E_FEBRE = 11L;
    private static final long GRIPE = 12L;

    @Autowired
    private CategoriaService service;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @PersistenceContext
    private EntityManager em;

    private static CategoriaForm form(String nome, Long paiId, boolean ativa) {
        CategoriaForm form = new CategoriaForm();
        form.setNome(nome);
        form.setPaiId(paiId);
        form.setAtiva(ativa);
        return form;
    }

    private Long criar(String nome, Long paiId) {
        service.salvar(null, form(nome, paiId, true));
        return categoriaRepository.findAll().stream()
                .filter(c -> c.getNome().equals(nome))
                .findFirst().orElseThrow().getId();
    }

    private Categoria gravarDireto(String nome, Categoria pai) {
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        categoria.setCategoriaPai(pai);
        return categoriaRepository.saveAndFlush(categoria);
    }

    private List<Long> idsDoMenu() {
        return new NavegacaoAdvice(categoriaRepository).menuCategorias().stream()
                .flatMap(item -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(item.categoria().getId()),
                        item.filhas().stream().map(Categoria::getId)))
                .toList();
    }

    // ---------- ciclo, autoparentesco e limite de níveis ----------

    @Test
    @DisplayName("bloqueia categoria sendo pai de si mesma")
    void bloqueiaAutoparentesco() {
        assertThatThrownBy(() -> service.salvar(MEDICAMENTOS, form("Medicamentos", MEDICAMENTOS, true)))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("dela mesma");
    }

    @Test
    @DisplayName("bloqueia ciclo direto: pai virar filha da própria filha")
    void bloqueiaCicloDireto() {
        assertThatThrownBy(() -> service.salvar(MEDICAMENTOS, form("Medicamentos", DOR_E_FEBRE, true)))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("ciclo");
    }

    @Test
    @DisplayName("bloqueia ciclo indireto: A > B > C e A passa a ser filha de C")
    void bloqueiaCicloIndireto() {
        Categoria a = gravarDireto("Ciclo A", null);
        Categoria b = gravarDireto("Ciclo B", a);
        Categoria c = gravarDireto("Ciclo C", b); // 3 níveis só existem aqui, gravados direto no banco

        assertThatThrownBy(() -> service.salvar(a.getId(), form("Ciclo A", c.getId(), true)))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("ciclo");
    }

    @Test
    @DisplayName("limite de 2 níveis: não cria subcategoria de subcategoria")
    void bloqueiaTerceiroNivel() {
        assertThatThrownBy(() -> service.salvar(null, form("Nível 3", DOR_E_FEBRE, true)))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("2 níveis");
    }

    @Test
    @DisplayName("limite de 2 níveis: raiz com filhas não vira filha de outra raiz")
    void bloqueiaMoverRaizComFilhas() {
        assertThatThrownBy(() -> service.salvar(MEDICAMENTOS, form("Medicamentos", 2L, true)))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("2 níveis");
    }

    @Test
    @DisplayName("o select de pai só oferece raízes, sem a própria")
    void opcoesDePaiSaoSoRaizes() {
        assertThat(service.opcoesDePai(null)).allMatch(o -> o.nivel() == 0);
        assertThat(service.opcoesDePai(DOR_E_FEBRE)).allMatch(o -> o.nivel() == 0);
        assertThat(service.opcoesDePai(2L)).extracting(o -> o.id()).doesNotContain(2L);
        assertThat(service.opcoesDePai(MEDICAMENTOS)).isEmpty();
    }

    @Test
    @DisplayName("bloqueia nome repetido no mesmo pai (sem diferenciar maiúsculas), mas aceita em outro pai")
    void bloqueiaNomeRepetidoNoMesmoPai() {
        assertThatThrownBy(() -> service.salvar(null, form("DOR E FEBRE", MEDICAMENTOS, true)))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("mesma categoria pai");

        service.salvar(null, form("Dor e febre", 2L, true));
        assertThat(categoriaRepository.findByCategoriaPaiId(2L)).extracting(Categoria::getNome)
                .contains("Dor e febre");
    }

    // ---------- exclusão e reatribuição ----------

    @Test
    @DisplayName("não exclui categoria com produto vinculado")
    void naoExcluiCategoriaComProduto() {
        assertThatThrownBy(() -> service.excluir(DOR_E_FEBRE))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("produtos");

        assertThat(categoriaRepository.existsById(DOR_E_FEBRE)).isTrue();
    }

    @Test
    @DisplayName("não exclui categoria com subcategorias")
    void naoExcluiCategoriaComSubcategorias() {
        assertThatThrownBy(() -> service.excluir(MEDICAMENTOS))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("subcategorias");

        assertThat(categoriaRepository.existsById(MEDICAMENTOS)).isTrue();
    }

    @Test
    @DisplayName("exclui categoria sem dependências")
    void excluiCategoriaSemDependencias() {
        Long id = criar("Temporária", null);

        service.excluir(id);

        assertThat(categoriaRepository.existsById(id)).isFalse();
    }

    @Test
    @DisplayName("reatribui os produtos para outra categoria ativa e exclui a original")
    void reatribuiEExclui() {
        service.reatribuirEExcluir(DOR_E_FEBRE, GRIPE);
        em.flush();
        em.clear();

        assertThat(categoriaRepository.existsById(DOR_E_FEBRE)).isFalse();
        // o produto 4 já estava nas duas categorias e não pode duplicar nem ficar sem categoria
        assertThat(produtoRepository.findByCategoriasId(GRIPE)).extracting(Produto::getId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("reatribuição recusa destino inativo, a própria categoria ou ausência de destino")
    void reatribuicaoValidaODestino() {
        service.inativar(GRIPE);

        assertThatThrownBy(() -> service.reatribuirEExcluir(DOR_E_FEBRE, GRIPE))
                .isInstanceOf(CategoriaException.class).hasMessageContaining("inativa");
        assertThatThrownBy(() -> service.reatribuirEExcluir(DOR_E_FEBRE, DOR_E_FEBRE))
                .isInstanceOf(CategoriaException.class);
        assertThatThrownBy(() -> service.reatribuirEExcluir(DOR_E_FEBRE, null))
                .isInstanceOf(CategoriaException.class);

        assertThat(categoriaRepository.existsById(DOR_E_FEBRE)).isTrue();
        assertThat(produtoRepository.findByCategoriasId(DOR_E_FEBRE)).hasSize(4);
    }

    @Test
    @DisplayName("reatribuição não exclui categoria que ainda tem subcategorias")
    void reatribuicaoNaoExcluiPaiComFilhas() {
        assertThatThrownBy(() -> service.reatribuirEExcluir(MEDICAMENTOS, 2L))
                .isInstanceOf(CategoriaException.class).hasMessageContaining("subcategorias");
    }

    // ---------- inativação e menu ----------

    @Test
    @DisplayName("inativar o pai inativa as filhas e todas somem do menu")
    void inativarPaiInativaFilhasEMenu() {
        assertThat(idsDoMenu()).contains(MEDICAMENTOS, DOR_E_FEBRE);

        service.inativar(MEDICAMENTOS);

        assertThat(categoriaRepository.findByCategoriaPaiId(MEDICAMENTOS)).isNotEmpty().noneMatch(Categoria::isAtiva);
        assertThat(idsDoMenu()).doesNotContain(MEDICAMENTOS, DOR_E_FEBRE);
    }

    @Test
    @DisplayName("inativar só a filha tira ela do menu e mantém o pai")
    void inativarFilhaTiraSoEla() {
        service.inativar(DOR_E_FEBRE);

        assertThat(idsDoMenu()).contains(MEDICAMENTOS).doesNotContain(DOR_E_FEBRE);
    }

    @Test
    @DisplayName("desmarcar \"ativa\" na edição do pai também inativa as filhas")
    void inativarPelaEdicaoCascateia() {
        CategoriaForm form = service.formDe(MEDICAMENTOS);
        form.setAtiva(false);

        service.salvar(MEDICAMENTOS, form);

        assertThat(categoriaRepository.findByCategoriaPaiId(MEDICAMENTOS)).noneMatch(Categoria::isAtiva);
    }

    @Test
    @DisplayName("não ativa filha enquanto o pai estiver inativo")
    void naoAtivaFilhaComPaiInativo() {
        service.inativar(MEDICAMENTOS);
        CategoriaForm form = service.formDe(DOR_E_FEBRE);
        form.setAtiva(true);

        assertThatThrownBy(() -> service.salvar(DOR_E_FEBRE, form))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("pai está inativa");
    }
}
