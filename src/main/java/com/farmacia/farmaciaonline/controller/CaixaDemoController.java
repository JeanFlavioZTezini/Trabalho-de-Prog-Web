package com.farmacia.farmaciaonline.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;

/**
 * Caixa de mensagens de teste: mostra os e-mails e SMS que o envio simulado guardou. Só existe no perfil demo; fora
 * dele a rota não existe (404) e nenhum código aparece em tela. Cada navegador vê só as mensagens geradas na própria
 * sessão; o administrador vê todas.
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
    public String caixa(HttpServletRequest request, Authentication autenticacao, Model model) {
        HttpSession sessao = request.getSession(false);
        Object caixaId = sessao == null ? null : sessao.getAttribute(DemoEnvioAdapter.ATRIBUTO_CAIXA);
        boolean admin = autenticacao != null && autenticacao.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        model.addAttribute("mensagens", caixa.mensagensVisiveis(caixaId instanceof String id ? id : null, admin));
        return "caixa-demo";
    }
}
