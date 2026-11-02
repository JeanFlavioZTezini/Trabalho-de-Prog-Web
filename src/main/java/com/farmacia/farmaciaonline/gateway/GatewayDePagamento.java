package com.farmacia.farmaciaonline.gateway;

import com.farmacia.farmaciaonline.gateway.dto.RequisicaoPagamento;
import com.farmacia.farmaciaonline.gateway.dto.RespostaPagamento;
import com.farmacia.farmaciaonline.gateway.dto.TokenCartao;

public interface GatewayDePagamento {
    RespostaPagamento processar(RequisicaoPagamento requisicao);

    RespostaPagamento consultar(String transacaoGatewayId);

    /**
     * F-25: troca o cartão por um token que só este gateway entende (para "salvar cartão"). Dado inválido lança
     * {@link IllegalArgumentException}, como em {@link #processar}.
     */
    TokenCartao tokenizar(String numeroCartao, String validadeCartao, String nomeImpresso);
}
