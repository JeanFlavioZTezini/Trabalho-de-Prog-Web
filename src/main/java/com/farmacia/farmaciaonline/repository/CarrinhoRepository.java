package com.farmacia.farmaciaonline.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Carrinho;

import jakarta.persistence.LockModeType;

@Repository
public interface CarrinhoRepository extends JpaRepository<Carrinho, Long> {

    /** Carrinho de um usuário logado. */
    Optional<Carrinho> findByUsuarioId(Long usuarioId);

    /** Carrinho de um visitante, pelo token do cookie. */
    Optional<Carrinho> findByToken(UUID token);

    /** Carrinho do usuário com a linha travada até o fim da transação. Os itens só são lidos depois da espera. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carrinho c where c.usuario.id = :usuarioId")
    Optional<Carrinho> buscarTravadoPorUsuario(@Param("usuarioId") Long usuarioId);
}
