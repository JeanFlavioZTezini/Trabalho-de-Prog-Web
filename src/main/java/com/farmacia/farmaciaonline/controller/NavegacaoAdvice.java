package com.farmacia.farmaciaonline.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;

import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class NavegacaoAdvice {

    private final CategoriaRepository categoriaRepository;

    @ModelAttribute("menuCategorias")
    public List<CategoriaMenu> menuCategorias() {
        List<Categoria> raizes = categoriaRepository.findByCategoriaPaiIsNullAndAtivaTrueOrderByOrdemAsc();
        if (raizes.isEmpty()) {
            return List.of();
        }

        List<Long> idsRaizes = raizes.stream().map(Categoria::getId).toList();
        List<Categoria> filhas = categoriaRepository
                .findByCategoriaPaiIdInAndAtivaTrueOrderByCategoriaPaiIdAscOrdemAsc(idsRaizes);
        Map<Long, List<Categoria>> filhasPorRaiz = new LinkedHashMap<>();
        for (Categoria filha : filhas) {
            Long idRaiz = filha.getCategoriaPai().getId();
            filhasPorRaiz.computeIfAbsent(idRaiz, id -> new ArrayList<>()).add(filha);
        }

        return raizes.stream()
                .map(raiz -> new CategoriaMenu(raiz, filhasPorRaiz.getOrDefault(raiz.getId(), List.of())))
                .toList();
    }

    public record CategoriaMenu(Categoria categoria, List<Categoria> filhas) {
    }
}
