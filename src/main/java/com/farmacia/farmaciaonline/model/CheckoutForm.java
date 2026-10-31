package com.farmacia.farmaciaonline.model;

import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckoutForm {

    private TipoEntrega tipoEntrega;
    private Long enderecoId;
    private Long lojaId;
    private MetodoPagamento metodo;
    private Integer parcelas;

    /** Parcelas só existem no crédito. */
    public int parcelasEfetivas() {
        return metodo == MetodoPagamento.CARTAO_CREDITO && parcelas != null && parcelas > 1 ? parcelas : 1;
    }

    public boolean isPagoComCartao() {
        return metodo == MetodoPagamento.CARTAO_CREDITO || metodo == MetodoPagamento.CARTAO_DEBITO;
    }
}
