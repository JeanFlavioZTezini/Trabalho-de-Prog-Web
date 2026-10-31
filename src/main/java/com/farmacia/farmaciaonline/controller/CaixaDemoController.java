package com.farmacia.farmaciaonline.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;

/**
 * Caixa de mensagens de teste: mostra os e-mails e SMS que o envio simulado guardou. Só existe no perfil demo; fora
 * dele a rota não existe (404) e nenhum código aparece em tela.
 */
@Controller
@Profile("demo")
@RequestMapping("/caixa-demo")
public class CaixaDemoController {

    private final DemoEnvioAdapter caixa;

    public CaixaDemoController(DemoEnvioAdapter caixa) {
        this.caixa = caixa;
    }

    @GetMapping
    public String caixa(Model model) {
        model.addAttribute("mensagens", caixa.mensagens());
        return "caixa-demo";
    }
}
