package com.farmacia.farmaciaonline.mensagem;

import java.time.LocalDateTime;

import com.farmacia.farmaciaonline.model.enums.CanalCodigo;

/**
 * Uma mensagem guardada pelo envio de demonstração. {@code contaId} é a conta dona da mensagem (null quando o envio não
 * disse): a caixa demo só mostra a cada pessoa as mensagens da própria conta.
 */
public record MensagemEnviada(LocalDateTime enviadaEm, CanalCodigo canal, String destino, String assunto, String texto,
        Long contaId) {
}
