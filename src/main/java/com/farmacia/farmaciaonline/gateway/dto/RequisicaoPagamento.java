package com.farmacia.farmaciaonline.gateway.dto;

import java.math.BigDecimal;

import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;

public record RequisicaoPagamento(
        Long pedidoId,
        BigDecimal valor,
        MetodoPagamento metodo,
        String numeroCartao,
        String cvv,
        String validadeCartao,
        String nomeImpressoCartao,
        Integer parcelas,
        Long cartaoSalvoId,
        String chaveIdempotencia
) {
    public RequisicaoPagamento {
        if (parcelas == null || parcelas < 1) {
            parcelas = 1;
        }
        if (numeroCartao != null) {
            numeroCartao = numeroCartao.replaceAll("[\\s-]", "");
        }
    }

    public RequisicaoPagamento(Long pedidoId, BigDecimal valor, MetodoPagamento metodo, String numeroCartao, String cvv,
            String validadeCartao, String nomeImpressoCartao, Integer parcelas, Long cartaoSalvoId) {
        this(pedidoId, valor, metodo, numeroCartao, cvv, validadeCartao, nomeImpressoCartao, parcelas, cartaoSalvoId,
                null);
    }

    @Override
    public String toString() {
        return "RequisicaoPagamento[pedidoId=" + pedidoId + ", metodo=" + metodo + ", parcelas=" + parcelas + "]";
    }
}
