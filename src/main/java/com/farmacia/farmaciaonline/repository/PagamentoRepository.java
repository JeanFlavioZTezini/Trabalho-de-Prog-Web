package com.farmacia.farmaciaonline.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Pagamento;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    /** Tentativas de pagamento de um pedido. */
    List<Pagamento> findByPedidoId(Long pedidoId);
}
