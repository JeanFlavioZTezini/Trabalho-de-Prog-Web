package com.farmacia.farmaciaonline.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Pagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    /** Tentativas de pagamento de um pedido. */
    List<Pagamento> findByPedidoId(Long pedidoId);

    /** Tentativas de um pedido, da mais recente para a mais antiga. */
    List<Pagamento> findByPedidoIdOrderByIdDesc(Long pedidoId);

    Optional<Pagamento> findByPedidoIdAndChaveIdempotencia(Long pedidoId, UUID chave);

    Optional<Pagamento> findByTransacaoGatewayId(String transacaoGatewayId);

    /** O índice {@code ux_pagamento_ativo} garante no máximo uma tentativa pendente por pedido. */
    Optional<Pagamento> findByPedidoIdAndStatus(Long pedidoId, StatusPagamento status);
}
