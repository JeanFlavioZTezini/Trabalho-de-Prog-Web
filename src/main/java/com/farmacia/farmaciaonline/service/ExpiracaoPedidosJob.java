package com.farmacia.farmaciaonline.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Libera o estoque de pedidos com a reserva vencida. */
@Component
@RequiredArgsConstructor
public class ExpiracaoPedidosJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiracaoPedidosJob.class);

    private final PagamentoPedidoService pagamentos;

    @Scheduled(cron = "${app.checkout.expiracao.cron}")
    public void executar() {
        int expirados = pagamentos.expirarVencidos();
        if (expirados > 0) {
            log.info("{} pedido(s) expirado(s) e estoque liberado", expirados);
        }
    }
}
