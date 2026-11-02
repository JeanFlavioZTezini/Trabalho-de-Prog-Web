package com.farmacia.farmaciaonline.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.CartaoSalvo;

@Repository
public interface CartaoSalvoRepository extends JpaRepository<CartaoSalvo, Long> {

    List<CartaoSalvo> findByUsuarioId(Long usuarioId);

    List<CartaoSalvo> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    /** F-25: todo uso de cartão salvo confere o dono; de outra pessoa vem vazio. */
    Optional<CartaoSalvo> findByIdAndUsuarioId(Long id, Long usuarioId);
}
