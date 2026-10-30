package com.farmacia.farmaciaonline.gateway.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.farmacia.farmaciaonline.model.enums.StatusPagamento;

public record RespostaPagamento(
        String transacaoGatewayId,
        StatusPagamento status,
        String mensagem,
        String codigoPagamento,
        Instant expiraEm,
        String bandeira,
        String ultimos4,
        BigDecimal valorConfirmado
) {}
