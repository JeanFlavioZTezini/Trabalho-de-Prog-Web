package com.farmacia.farmaciaonline.controller;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.model.enums.TipoProduto;
import com.farmacia.farmaciaonline.repository.FiltroCatalogo;
import com.farmacia.farmaciaonline.repository.OrdenacaoCatalogo;
import com.farmacia.farmaciaonline.service.CatalogoService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CatalogoController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogoController.class);
    private static final int TAMANHO_PAGINA = 12;

    private final CatalogoService catalogoService;

    @GetMapping("/produtos")
    public String listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) BigDecimal precoMin,
            @RequestParam(required = false) BigDecimal precoMax,
            @RequestParam(defaultValue = "false") boolean disponivel,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) String principioAtivo,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String forma,
            @RequestParam(required = false) String receita,
            @RequestParam(defaultValue = "NOME") OrdenacaoCatalogo ordenacao,
            @RequestParam(defaultValue = "0") int pagina, Model model) {
        Pageable pageable = PageRequest.of(Math.min(Math.max(pagina, 0), 100_000), TAMANHO_PAGINA);
        Page<Produto> produtos = Page.empty(pageable);
        String erroFiltro = null;
        String erroCarregamento = null;
        String sugestao = null;

        if ((precoMin != null && precoMin.signum() < 0)
                || (precoMax != null && precoMax.signum() < 0)) {
            erroFiltro = "Os preços devem ser iguais ou maiores que zero.";
        } else if (precoMin != null && precoMax != null && precoMin.compareTo(precoMax) > 0) {
            erroFiltro = "O preço mínimo não pode ser maior que o preço máximo.";
        } else {
            try {
                FiltroCatalogo filtro = new FiltroCatalogo(q, categoria, null, precoMin, precoMax, disponivel,
                        marca, principioAtivo, tipoOuNulo(tipo), forma, receitaOuNulo(receita));
                produtos = catalogoService.buscar(filtro, ordenacao, pageable);
                if (produtos.isEmpty() && q != null && !q.isBlank()) {
                    sugestao = sugerir(q);
                }
                model.addAttribute("marcas", catalogoService.marcas());
                model.addAttribute("principiosAtivos", catalogoService.principiosAtivos());
                model.addAttribute("formas", catalogoService.formas());
            } catch (DataAccessException exception) {
                LOGGER.error("Não foi possível carregar o catálogo público", exception);
                erroCarregamento = "Não foi possível carregar os produtos agora. Tente novamente.";
            }
        }

        model.addAttribute("pagina", produtos);
        model.addAttribute("q", q);
        model.addAttribute("categoriaId", categoria);
        model.addAttribute("precoMin", precoMin);
        model.addAttribute("precoMax", precoMax);
        model.addAttribute("disponivel", disponivel);
        model.addAttribute("marca", marca);
        model.addAttribute("principioAtivo", principioAtivo);
        model.addAttribute("tipo", tipoOuNulo(tipo));
        model.addAttribute("forma", forma);
        model.addAttribute("receita", receitaOuNulo(receita) == null ? "" : receita.trim().toLowerCase());
        model.addAttribute("sugestao", sugestao);
        model.addAttribute("ordenacao", ordenacao);
        model.addAttribute("erroFiltro", erroFiltro);
        model.addAttribute("erroCarregamento", erroCarregamento);
        return "produtos";
    }

    /** Falha na sugestão não derruba a página (e o texto digitado não vai para o log). */
    private String sugerir(String q) {
        try {
            return catalogoService.sugerir(q).orElse(null);
        } catch (DataAccessException exception) {
            LOGGER.warn("Não foi possível calcular a sugestão de busca", exception);
            return null;
        }
    }

    private static TipoProduto tipoOuNulo(String tipo) {
        try {
            return tipo == null || tipo.isBlank() ? null : TipoProduto.valueOf(tipo.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Boolean receitaOuNulo(String receita) {
        if (receita == null) {
            return null;
        }
        return switch (receita.trim().toLowerCase()) {
            case "sim" -> Boolean.TRUE;
            case "nao" -> Boolean.FALSE;
            default -> null;
        };
    }
}
