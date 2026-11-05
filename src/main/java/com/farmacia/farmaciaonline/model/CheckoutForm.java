package com.farmacia.farmaciaonline.model;

import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckoutForm {

    /** Mesmo teto da tela (select de 1 a 12) e do gateway. */
    public static final int MAX_PARCELAS = 12;
    public static final String MENSAGEM_PARCELAS = "Escolha de 1 a " + MAX_PARCELAS + " parcelas.";

    private TipoEntrega tipoEntrega;
    private Long enderecoId;
    private Long lojaId;
    private MetodoPagamento metodo;
    private Integer parcelas;

    /** Parcelas só existem no crédito. */
    public int parcelasEfetivas() {
        return metodo == MetodoPagamento.CARTAO_CREDITO && parcelas != null && parcelas > 1 ? parcelas : 1;
    }

    /** Envio adulterado (o select vai só até 12): barra antes de reservar estoque ou abrir tentativa. */
    public boolean isParcelasForaDoLimite() {
        return parcelasEfetivas() > MAX_PARCELAS;
    }

    public boolean isPagoComCartao() {
        return metodo == MetodoPagamento.CARTAO_CREDITO || metodo == MetodoPagamento.CARTAO_DEBITO;
    }
}
