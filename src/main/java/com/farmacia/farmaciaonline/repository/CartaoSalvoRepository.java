package com.farmacia.farmaciaonline.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.CartaoSalvo;

@Repository
public interface CartaoSalvoRepository extends JpaRepository<CartaoSalvo, Long> {

    List<CartaoSalvo> findByUsuarioId(Long usuarioId);
}
