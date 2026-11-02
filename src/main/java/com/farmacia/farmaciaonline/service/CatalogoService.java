package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.repository.FiltroCatalogo;
import com.farmacia.farmaciaonline.repository.OrdenacaoCatalogo;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogoService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;

    @Value("${app.catalogo.sugestao-similaridade:0.4}")
    private double sugestaoSimilaridade = 0.4;

    public Page<Produto> buscar(String termo, Long categoriaId, BigDecimal precoMinimo, BigDecimal precoMaximo,
            boolean somenteDisponiveis, OrdenacaoCatalogo ordenacao, Pageable pageable) {
        return buscar(new FiltroCatalogo(termo, categoriaId, null, precoMinimo, precoMaximo, somenteDisponiveis,
                null, null, null, null, null), ordenacao, pageable);
    }

    public Page<Produto> buscar(FiltroCatalogo filtro, OrdenacaoCatalogo ordenacao, Pageable pageable) {
        List<Long> categoriaIds = new ArrayList<>();
        Long categoriaId = filtro.categoriaId();
        if (categoriaId != null) {
            Categoria categoria = categoriaRepository.findByIdAndAtivaTrue(categoriaId).orElse(null);
            if (categoria == null) {
                return Page.empty(pageable);
            }
            categoriaIds.add(categoria.getId());
            if (categoria.getCategoriaPai() == null) {
                categoriaRepository.findByCategoriaPaiIdAndAtivaTrueOrderByOrdemAsc(categoriaId)
                        .stream()
                        .map(Categoria::getId)
                        .forEach(categoriaIds::add);
            }
        }

        String termo = filtro.termo();
        String termoNormalizado = termo == null || termo.isBlank() ? null : termo.trim();
        return produtoRepository.buscarCatalogo(filtro.comTermo(termoNormalizado).comCategorias(categoriaIds),
                ordenacao, pageable);
    }

    /**
     * "Você quis dizer": troca cada palavra desconhecida (3+ letras) pela mais parecida do catálogo ativo.
     * Vazio se nada mudou ou se nenhuma palavra parecida passou do limite.
     */
    public Optional<String> sugerir(String termo) {
        if (termo == null || termo.isBlank()) {
            return Optional.empty();
        }
        List<String> corrigidas = new ArrayList<>();
        boolean mudou = false;
        for (String palavra : termo.trim().split("\\s+")) {
            String base = semAcento(palavra);
            if (base.length() < 3 || base.length() > 40) {
                corrigidas.add(palavra);
                continue;
            }
            var parecida = produtoRepository.palavraMaisParecida(base).orElse(null);
            if (parecida != null && !parecida.getPalavra().equals(base)
                    && parecida.getSimilaridade() != null
                    && parecida.getSimilaridade() >= sugestaoSimilaridade) {
                corrigidas.add(parecida.getPalavra());
                mudou = true;
            } else {
                corrigidas.add(palavra);
            }
        }
        return mudou ? Optional.of(String.join(" ", corrigidas)) : Optional.empty();
    }

    public List<String> marcas() {
        return produtoRepository.marcasAtivas();
    }

    public List<String> principiosAtivos() {
        return produtoRepository.principiosAtivosAtivos();
    }

    public List<String> formas() {
        return produtoRepository.formasAtivas();
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
