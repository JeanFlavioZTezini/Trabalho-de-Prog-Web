package com.farmacia.farmaciaonline.model.enums;

/**
 * Situação do pedido. PRONTO_PARA_RETIRADA é do pedido de retirada em loja. A lista precisa
 * ser igual à dos CHECK da V1 (pedido e historico_status_pedido).
 */
public enum StatusPedido {
    AGUARDANDO_PAGAMENTO,
    PAGAMENTO_APROVADO,
    EM_SEPARACAO,
    ENVIADO,
    PRONTO_PARA_RETIRADA,
    ENTREGUE,
    CANCELADO
}
