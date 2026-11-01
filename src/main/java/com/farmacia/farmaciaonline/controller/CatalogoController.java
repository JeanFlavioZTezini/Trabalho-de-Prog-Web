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
            @RequestParam(defaultValue = "NOME") OrdenacaoCatalogo ordenacao,
            @RequestParam(defaultValue = "0") int pagina, Model model) {
        Pageable pageable = PageRequest.of(Math.min(Math.max(pagina, 0), 100_000), TAMANHO_PAGINA);
        Page<Produto> produtos = Page.empty(pageable);
        String erroFiltro = null;
        String erroCarregamento = null;

        if ((precoMin != null && precoMin.signum() < 0)
                || (precoMax != null && precoMax.signum() < 0)) {
            erroFiltro = "Os preços devem ser iguais ou maiores que zero.";
        } else if (precoMin != null && precoMax != null && precoMin.compareTo(precoMax) > 0) {
            erroFiltro = "O preço mínimo não pode ser maior que o preço máximo.";
        } else {
            try {
                produtos = catalogoService.buscar(q, categoria, precoMin, precoMax, disponivel, ordenacao, pageable);
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
        model.addAttribute("ordenacao", ordenacao);
        model.addAttribute("erroFiltro", erroFiltro);
        model.addAttribute("erroCarregamento", erroCarregamento);
        return "produtos";
    }
}
