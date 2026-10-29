package com.farmacia.farmaciaonline.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.farmacia.farmaciaonline.repository.ProdutoRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProdutoRepository produtoRepository;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("maisComprados", produtoRepository.findTop8ByAtivoTrueOrderByQtdVendidaDesc());
        model.addAttribute("maisVistos", produtoRepository.findTop8ByAtivoTrueOrderByVisualizacoesDesc());
        return "index";
    }
}