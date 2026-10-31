package com.farmacia.farmaciaonline.model.enums;

public enum MetodoPagamento {
    CARTAO_CREDITO("Cartão de crédito"),
    CARTAO_DEBITO("Cartão de débito"),
    PIX("Pix"),
    BOLETO("Boleto");

    private final String rotulo;

    MetodoPagamento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
