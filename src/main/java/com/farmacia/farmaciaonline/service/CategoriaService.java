package com.farmacia.farmaciaonline.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.model.CategoriaForm;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoriaService {

    /** Limite de níveis adotado: categoria raiz + subcategoria (é o que o menu do site exibe). */
    public static final int MAX_NIVEIS = 2;

    private final CategoriaRepository repository;
    private final ProdutoRepository produtoRepository;

    public record CategoriaLinha(Long id, String nome, String descricao, int ordem, boolean ativa, int nivel) {
    }

    /** Dados da tela de exclusão: o que impede excluir e para onde reatribuir os produtos. */
    public record ResumoExclusao(Long id, String nome, boolean ativa, long subcategorias, long produtos,
            List<CategoriaLinha> destinos) {
    }

    /** Hierarquia achatada em ordem de árvore (pai antes dos filhos). */
    @Transactional(readOnly = true)
    public List<CategoriaLinha> listarArvore() {
        Map<Long, List<Categoria>> filhas = new HashMap<>();
        List<Categoria> raizes = new ArrayList<>();
        for (Categoria c : repository.findAllByOrderByOrdemAscNomeAsc()) {
            if (c.getCategoriaPai() == null) {
                raizes.add(c);
            } else {
                filhas.computeIfAbsent(c.getCategoriaPai().getId(), k -> new ArrayList<>()).add(c);
            }
        }
        List<CategoriaLinha> saida = new ArrayList<>();
        for (Categoria raiz : raizes) {
            adicionar(raiz, 0, filhas, saida);
        }
        return saida;
    }

    private void adicionar(Categoria c, int nivel, Map<Long, List<Categoria>> filhas, List<CategoriaLinha> saida) {
        saida.add(new CategoriaLinha(c.getId(), c.getNome(), c.getDescricao(), c.getOrdem(), c.isAtiva(), nivel));
        for (Categoria filha : filhas.getOrDefault(c.getId(), List.of())) {
            adicionar(filha, nivel + 1, filhas, saida);
        }
    }

    /**
     * Categorias que podem ser pai de {@code id}. Como o limite é de 2 níveis, só as raízes servem
     * (menos a própria); quem já tem subcategorias só pode continuar sendo raiz.
     */
    @Transactional(readOnly = true)
    public List<CategoriaLinha> opcoesDePai(Long id) {
        if (id != null && repository.existsByCategoriaPaiId(id)) {
            return List.of();
        }
        return listarArvore().stream()
                .filter(linha -> linha.nivel() == 0 && !linha.id().equals(id))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaForm formDe(Long id) {
        Categoria c = buscar(id);
        CategoriaForm form = new CategoriaForm();
        form.setNome(c.getNome());
        form.setDescricao(c.getDescricao());
        form.setPaiId(c.getCategoriaPai() == null ? null : c.getCategoriaPai().getId());
        form.setOrdem(c.getOrdem());
        form.setAtiva(c.isAtiva());
        return form;
    }

    public void salvar(Long id, CategoriaForm form) {
        Categoria categoria = id == null ? new Categoria() : buscar(id);
        String nome = form.getNome().trim();

        Categoria pai = null;
        if (form.getPaiId() != null) {
            pai = repository.findById(form.getPaiId())
                    .orElseThrow(() -> new CategoriaException("Categoria pai inexistente."));
            if (id != null) {
                if (pai.getId().equals(id)) {
                    throw new CategoriaException("Uma categoria não pode ser pai dela mesma.");
                }
                for (Categoria a = pai.getCategoriaPai(); a != null; a = a.getCategoriaPai()) {
                    if (a.getId().equals(id)) {
                        throw new CategoriaException("Categoria pai inválida: formaria um ciclo na hierarquia.");
                    }
                }
                if (repository.existsByCategoriaPaiId(id)) {
                    throw new CategoriaException("Não é possível mover: a categoria possui subcategorias e o limite é de "
                            + MAX_NIVEIS + " níveis.");
                }
            }
            if (pai.getCategoriaPai() != null) {
                throw new CategoriaException("Limite de " + MAX_NIVEIS
                        + " níveis: escolha uma categoria raiz como pai.");
            }
            if (form.isAtiva() && !pai.isAtiva()) {
                throw new CategoriaException("A categoria pai está inativa: ative-a antes ou salve esta como inativa.");
            }
        }

        List<Categoria> irmas = pai == null ? repository.findByCategoriaPaiIsNull()
                : repository.findByCategoriaPaiId(pai.getId());
        boolean repetido = irmas.stream()
                .anyMatch(c -> !c.getId().equals(id) && c.getNome().equalsIgnoreCase(nome));
        if (repetido) {
            throw new CategoriaException("Já existe uma categoria com esse nome na mesma categoria pai.");
        }

        boolean inativando = categoria.getId() != null && categoria.isAtiva() && !form.isAtiva();
        categoria.setNome(nome);
        categoria.setDescricao(form.getDescricao() == null || form.getDescricao().isBlank()
                ? null : form.getDescricao().trim());
        categoria.setCategoriaPai(pai);
        categoria.setOrdem(form.getOrdem());
        categoria.setAtiva(form.isAtiva());
        try {
            repository.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException e) {
            throw new CategoriaException("Já existe uma categoria com esse nome na mesma categoria pai.");
        }
        if (inativando) {
            inativarDescendentes(categoria);
        }
    }

    /** Inativa a categoria e todas as suas subcategorias, para o menu não ficar inconsistente. */
    public void inativar(Long id) {
        Categoria categoria = buscar(id);
        categoria.setAtiva(false);
        inativarDescendentes(categoria);
        repository.flush();
    }

    private void inativarDescendentes(Categoria categoria) {
        for (Categoria filha : repository.findByCategoriaPaiId(categoria.getId())) {
            filha.setAtiva(false);
            inativarDescendentes(filha);
        }
    }

    @Transactional(readOnly = true)
    public ResumoExclusao resumoExclusao(Long id) {
        Categoria categoria = buscar(id);
        List<CategoriaLinha> destinos = listarArvore().stream()
                .filter(linha -> linha.ativa() && !linha.id().equals(id))
                .toList();
        return new ResumoExclusao(id, categoria.getNome(), categoria.isAtiva(),
                repository.findByCategoriaPaiId(id).size(), repository.contarProdutos(id), destinos);
    }

    public void excluir(Long id) {
        Categoria categoria = buscar(id);
        if (repository.existsByCategoriaPaiId(id)) {
            throw new CategoriaException("Não é possível excluir: a categoria possui subcategorias. Exclua-as antes ou inative a categoria.");
        }
        if (repository.contarProdutos(id) > 0) {
            throw new CategoriaException("Não é possível excluir: a categoria possui produtos. Reatribua-os a outra categoria ou inative esta.");
        }
        repository.delete(categoria);
    }

    /** Move os produtos da categoria para {@code destinoId} (ativa) e exclui a categoria. */
    public void reatribuirEExcluir(Long id, Long destinoId) {
        Categoria origem = buscar(id);
        if (repository.existsByCategoriaPaiId(id)) {
            throw new CategoriaException("Não é possível excluir: a categoria possui subcategorias. "
                    + "Exclua-as antes ou inative a categoria.");
        }
        if (destinoId == null || destinoId.equals(id)) {
            throw new CategoriaException("Escolha outra categoria como destino dos produtos.");
        }
        Categoria destino = repository.findById(destinoId)
                .orElseThrow(() -> new CategoriaException("Categoria de destino inexistente."));
        if (!destino.isAtiva()) {
            throw new CategoriaException("A categoria de destino está inativa.");
        }
        for (Produto produto : produtoRepository.findByCategoriasId(id)) {
            produto.getCategorias().removeIf(c -> c.getId().equals(id));
            produto.getCategorias().add(destino);
        }
        produtoRepository.flush();
        repository.delete(origem);
        repository.flush();
    }

    private Categoria buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
