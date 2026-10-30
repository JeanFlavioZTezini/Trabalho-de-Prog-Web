package com.farmacia.farmaciaonline.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.HistoricoStatusPedido;

@Repository
public interface HistoricoStatusPedidoRepository extends JpaRepository<HistoricoStatusPedido, Long> {

    /** Linha do tempo do pedido, da mais antiga para a mais recente. */
    List<HistoricoStatusPedido> findByPedidoIdOrderByCriadoEmAsc(Long pedidoId);
}
