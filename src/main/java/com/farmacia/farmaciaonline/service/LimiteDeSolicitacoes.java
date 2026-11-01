package com.farmacia.farmaciaonline.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limite de pedidos de recuperação de senha por identificador digitado (e-mail ou CPF), exista a conta ou não: assim a
 * resposta "aguarde" sai igual para conta existente e inexistente. Usa os mesmos valores dos códigos
 * ({@code app.codigos.*}). Fica na memória: reinicia junto com a aplicação.
 */
@Component
public class LimiteDeSolicitacoes {

    private static final Duration JANELA_DO_LIMITE = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> pedidos = new ConcurrentHashMap<>();
    private final Duration intervalo;
    private final int porHora;
    private final Duration bloqueio;

    public LimiteDeSolicitacoes(@Value("${app.codigos.intervalo-reenvio:60s}") Duration intervalo,
            @Value("${app.codigos.envios-por-hora:5}") int porHora,
            @Value("${app.codigos.bloqueio-envio:15m}") Duration bloqueio) {
        this.intervalo = intervalo;
        this.porHora = porHora;
        this.bloqueio = bloqueio;
    }

    /** Registra o pedido e devolve 0, ou devolve quantos segundos faltam (e não registra). */
    public long registrar(String chave) {
        return registrar(chave, Instant.now());
    }

    synchronized long registrar(String chave, Instant agora) {
        Deque<Instant> lista = pedidos.computeIfAbsent(chave, c -> new ArrayDeque<>());
        while (!lista.isEmpty() && lista.peekFirst().isBefore(agora.minus(JANELA_DO_LIMITE))) {
            lista.pollFirst();
        }
        Instant ultimo = lista.peekLast();
        if (ultimo != null && agora.isBefore(ultimo.plus(intervalo))) {
            return segundosAte(agora, ultimo.plus(intervalo));
        }
        if (ultimo != null && lista.size() >= porHora && agora.isBefore(ultimo.plus(bloqueio))) {
            return segundosAte(agora, ultimo.plus(bloqueio));
        }
        lista.addLast(agora);
        return 0;
    }

    /** Esquece todos os pedidos. Os testes usam para não depender da ordem em que rodam. */
    public synchronized void limpar() {
        pedidos.clear();
    }

    private static long segundosAte(Instant agora, Instant momento) {
        return Math.max(1, (Duration.between(agora, momento).toMillis() + 999) / 1000);
    }
}
