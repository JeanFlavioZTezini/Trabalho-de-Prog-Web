package com.farmacia.farmaciaonline.controller;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.service.DetalheProdutoService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class DetalheProdutoController {

    private final DetalheProdutoService detalheProdutoService;

    @GetMapping("/produtos/{id}")
    public String detalhe(@PathVariable Long id,
            @RequestParam(required = false) Long apresentacaoId, Model model) {
        var detalhe = detalheProdutoService.buscar(id, apresentacaoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("produto", detalhe);
        model.addAttribute("metodosPagamento", Arrays.asList(MetodoPagamento.values()));
        return "produto-detalhe";
    }
}
