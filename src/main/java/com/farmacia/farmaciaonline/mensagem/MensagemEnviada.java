package com.farmacia.farmaciaonline.mensagem;

import java.time.LocalDateTime;

import com.farmacia.farmaciaonline.model.enums.CanalCodigo;

/**
 * Uma mensagem guardada pelo envio de demonstração. {@code caixaId} identifica a sessão do navegador que gerou a mensagem
 * (null quando não houve requisição, como num job): a caixa demo só mostra a cada navegador o que ele mesmo gerou.
 */
public record MensagemEnviada(LocalDateTime enviadaEm, CanalCodigo canal, String destino, String assunto, String texto,
        String caixaId) {
}
