package com.farmacia.farmaciaonline.service;

import java.util.Objects;
import java.util.UUID;

public record DonoCarrinho(Long usuarioId, UUID token) {
    public DonoCarrinho {
        if ((usuarioId == null) == (token == null)) {
            throw new IllegalArgumentException("O carrinho tem um único dono: o usuário OU o token do visitante.");
        }
    }

    public static DonoCarrinho deUsuario(Long usuarioId) {
        return new DonoCarrinho(Objects.requireNonNull(usuarioId), null);
    }

    public static DonoCarrinho deVisitante(UUID token) {
        return new DonoCarrinho(null, Objects.requireNonNull(token));
    }

    public boolean isVisitante() {
        return token != null;
    }
}
