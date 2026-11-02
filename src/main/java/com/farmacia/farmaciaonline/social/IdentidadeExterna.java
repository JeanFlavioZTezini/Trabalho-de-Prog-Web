package com.farmacia.farmaciaonline.social;

import java.io.Serializable;

import com.farmacia.farmaciaonline.model.enums.ProvedorSocial;

/**
 * O que o provedor (Google/Facebook) diz sobre a pessoa depois do consentimento (F-20). {@code id} é o identificador
 * estável dela no provedor ("sub"); {@code email} pode faltar; {@code emailVerificado} só vale se o provedor afirmar.
 * Fica na sessão enquanto o primeiro acesso não termina (por isso Serializable); nunca vai na URL.
 */
public record IdentidadeExterna(ProvedorSocial provedor, String id, String nome, String email, boolean emailVerificado)
        implements Serializable {

    public boolean temEmail() {
        return email != null && !email.isBlank();
    }
}
