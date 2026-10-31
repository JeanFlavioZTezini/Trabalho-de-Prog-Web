package com.farmacia.farmaciaonline.mensagem;

import java.time.LocalDateTime;

import com.farmacia.farmaciaonline.model.enums.CanalCodigo;

/** Uma mensagem guardada pelo envio de demonstração. */
public record MensagemEnviada(LocalDateTime enviadaEm, CanalCodigo canal, String destino, String assunto, String texto) {
}
