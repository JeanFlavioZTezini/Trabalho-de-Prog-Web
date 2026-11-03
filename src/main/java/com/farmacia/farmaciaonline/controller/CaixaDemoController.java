package com.farmacia.farmaciaonline.controller;

import java.util.HashSet;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;

/**
 * Caixa de mensagens de teste: mostra os e-mails e SMS que o envio simulado guardou. Só existe no perfil demo; fora
 * dele a rota não existe (404) e nenhum código aparece em tela. Cada pessoa vê só as mensagens da própria conta: a da
 * conta logada e a da conta em confirmação na sessão (que só o cadastro, a retomada com senha e o login social de conta
 * pendente preenchem). O administrador vê todas.
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
        boolean admin = autenticacao != null && autenticacao.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        Set<Long> contas = new HashSet<>();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
            contas.add(usuario.getId());
        }
        HttpSession sessao = request.getSession(false);
        if (sessao != null && sessao.getAttribute(CadastroController.SESSAO_CONTA) instanceof Long emConfirmacao) {
            contas.add(emConfirmacao);
        }
        model.addAttribute("mensagens", caixa.mensagensVisiveis(contas, admin));
        return "caixa-demo";
    }
}
