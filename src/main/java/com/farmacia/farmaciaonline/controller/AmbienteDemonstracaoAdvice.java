package com.farmacia.farmaciaonline.controller;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class AmbienteDemonstracaoAdvice {

    private final Environment ambiente;

    @ModelAttribute("ambienteDemo")
    public boolean ambienteDemo() {
        return ambiente.acceptsProfiles(Profiles.of("demo"));
    }

    /** Quem clica em "cadastrar-se" no cabeçalho enquanto está no carrinho volta ao carrinho depois do cadastro (F-04/F-18). */
    @ModelAttribute("retornoCadastroCabecalho")
    public String retornoCadastroCabecalho(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return caminho.equals("/carrinho") || caminho.startsWith("/carrinho/") ? "carrinho" : null;
    }
}
