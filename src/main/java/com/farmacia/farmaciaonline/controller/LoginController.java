package com.farmacia.farmaciaonline.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.farmacia.farmaciaonline.security.LoginFailureHandler;

/**
 * Mostra a página de login. Quem processa o POST /login é o Spring Security (ver SecurityConfig).
 *
 * {@code /login?erro=<motivo>} vem do LoginFailureHandler e {@code /login?logout} da saída da conta.
 * A mensagem sai de uma lista fixa: nada do que vem na URL é repetido na página.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(
            @RequestParam(name = "erro", required = false) String erro,
            @RequestParam(name = "logout", required = false) String logout,
            Model model) {
        model.addAttribute("erroLogin", mensagemDeErro(erro));
        model.addAttribute("saiu", logout != null);
        return "login";
    }

    private static String mensagemDeErro(String erro) {
        if (erro == null) {
            return null;
        }
        return switch (erro) {
            case LoginFailureHandler.MOTIVO_PENDENTE ->
                    "Sua conta ainda não foi confirmada. Confirme seu e-mail para poder entrar.";
            case LoginFailureHandler.MOTIVO_BLOQUEADA ->
                    "Sua conta está temporariamente bloqueada. Tente novamente mais tarde.";
            case LoginFailureHandler.MOTIVO_INDISPONIVEL ->
                    "Não foi possível entrar agora. Tente novamente em instantes.";
            default -> "E-mail, CPF ou senha incorretos. Confira os dados e tente de novo.";
        };
    }
}
