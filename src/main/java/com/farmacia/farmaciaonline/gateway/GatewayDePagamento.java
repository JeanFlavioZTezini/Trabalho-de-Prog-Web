package com.farmacia.farmaciaonline.gateway;

import com.farmacia.farmaciaonline.gateway.dto.RequisicaoPagamento;
import com.farmacia.farmaciaonline.gateway.dto.RespostaPagamento;

public interface GatewayDePagamento {
    RespostaPagamento processar(RequisicaoPagamento requisicao);
    RespostaPagamento consultar(String transacaoGatewayId);
}
