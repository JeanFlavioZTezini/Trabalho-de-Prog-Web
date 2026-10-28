package com.farmacia.farmaciaonline.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.farmacia.farmaciaonline.security.UsuarioAutenticado;

/**
 * Entrega a todas as páginas o nome que o cabeçalho (fragments/layout.html) mostra para quem está logado.
 *
 * Se o principal for o nosso {@link UsuarioAutenticado}, é o primeiro nome. Qualquer outro principal
 * (ex.: o usuário falso de {@code @WithMockUser} nos testes) cai no nome de login, em vez de quebrar a página.
 * Visitante não recebe nada (null).
 */
@ControllerAdvice
public class UsuarioLogadoAdvice {

    @ModelAttribute("primeiroNomeUsuario")
    public String primeiroNomeUsuario() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null
                || autenticacao instanceof AnonymousAuthenticationToken
                || !autenticacao.isAuthenticated()) {
            return null;
        }
        if (autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario.getPrimeiroNome();
        }
        return autenticacao.getName();
    }
}
