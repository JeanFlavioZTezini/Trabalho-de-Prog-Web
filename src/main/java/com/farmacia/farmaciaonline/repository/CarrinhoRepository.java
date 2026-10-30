package com.farmacia.farmaciaonline.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Carrinho;

@Repository
public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {

    /** Carrinho de um usuário logado. */
    Optional<Carrinho> findByUsuarioId(Long usuarioId);

    /** Carrinho de um visitante, pelo token do cookie. */
    Optional<Carrinho> findByToken(UUID token);
}
