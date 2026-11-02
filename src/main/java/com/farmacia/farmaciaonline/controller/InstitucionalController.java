package com.farmacia.farmaciaonline.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/institucional")
public class InstitucionalController {

    @Value("${app.termos.versao:1.0}")
    private String termosVersao;

    @GetMapping("/boas-praticas")
    public String boasPraticas() {
        return "institucional/boas-praticas";
    }

    @GetMapping("/termos-de-uso")
    public String termosDeUso(Model model) {
        model.addAttribute("termosVersao", termosVersao);
        return "institucional/termos-de-uso";
    }

    @GetMapping("/privacidade")
    public String privacidade(Model model) {
        model.addAttribute("termosVersao", termosVersao);
        return "institucional/privacidade";
    }
}
