package com.farmacia.farmaciaonline.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.farmacia.farmaciaonline.model.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SessaoAutenticada {

    private final SecurityContextHolderStrategy estrategia = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository repositorio = new HttpSessionSecurityContextRepository();

    public void atualizar(Usuario usuario, HttpServletRequest request, HttpServletResponse response,
            boolean renovarIdDaSessao) {
        Authentication atual = estrategia.getContext().getAuthentication();
        if (atual == null) {
            return;
        }
        if (renovarIdDaSessao && request.getSession(false) != null) {
            request.changeSessionId();
        }

        UsuarioAutenticado principal = new UsuarioAutenticado(
                usuario.getId(), usuario.getNome(), usuario.getPerfil(), usuario.getEmail(), null);
        UsernamePasswordAuthenticationToken nova =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, atual.getAuthorities());
        nova.setDetails(atual.getDetails());

        SecurityContext contexto = estrategia.createEmptyContext();
        contexto.setAuthentication(nova);
        estrategia.setContext(contexto);
        repositorio.saveContext(contexto, request, response);
    }
}
