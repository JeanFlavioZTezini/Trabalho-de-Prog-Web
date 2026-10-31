package com.farmacia.farmaciaonline.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpSession;

/**
 * Página provisória do passo "confirmar a conta" (UC04). A F-05 troca esta página pela tela do código; o contrato é
 * ler o id da sessão ({@link CadastroController#SESSAO_CONTA}), nunca da URL.
 */
@Controller
@RequestMapping("/confirmar-conta")
public class ConfirmacaoContaController {

    @GetMapping
    public String pagina(HttpSession sessao) {
        if (sessao.getAttribute(CadastroController.SESSAO_CONTA) == null) {
            return "redirect:/cadastro";
        }
        return "confirmar-conta";
    }
}
