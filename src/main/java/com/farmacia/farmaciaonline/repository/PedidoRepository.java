package com.farmacia.farmaciaonline.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Pedido;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /** Pedidos do usuário, do mais recente para o mais antigo (UC06). */
    Page<Pedido> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId, Pageable pageable);

    /** Garante que o usuário só enxerga os próprios pedidos (UC06): de outro usuário, vem vazio. */
    Optional<Pedido> findByIdAndUsuarioId(Long id, Long usuarioId);
}
