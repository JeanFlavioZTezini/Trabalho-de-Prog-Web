package com.farmacia.farmaciaonline.model.enums;

public enum StatusPedido {
    AGUARDANDO_PAGAMENTO("Aguardando pagamento"),
    PAGAMENTO_APROVADO("Pagamento aprovado"),
    EM_SEPARACAO("Em separação"),
    ENVIADO("Enviado"),
    PRONTO_PARA_RETIRADA("Pronto para retirada"),
    ENTREGUE("Entregue"),
    CANCELADO("Cancelado");

    private final String rotulo;

    StatusPedido(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
