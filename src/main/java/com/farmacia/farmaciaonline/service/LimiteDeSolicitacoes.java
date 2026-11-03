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
    /** Acima disso, a próxima solicitação varre as chaves vencidas: identificadores inventados não acumulam para sempre. */
    static final int MAXIMO_DE_CHAVES = 1000;
    static final int TAMANHO_MAXIMO_DA_CHAVE = 150;

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
        if (pedidos.size() > MAXIMO_DE_CHAVES) {
            varrerVencidas(agora);
        }
        String limitada = chave.length() > TAMANHO_MAXIMO_DA_CHAVE ? chave.substring(0, TAMANHO_MAXIMO_DA_CHAVE) : chave;
        Deque<Instant> lista = pedidos.computeIfAbsent(limitada, c -> new ArrayDeque<>());
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

    /** Tira as chaves cujo último pedido já não pesa em nenhum limite (janela de 1 h, bloqueio e intervalo). */
    private void varrerVencidas(Instant agora) {
        Duration maiorEspera = JANELA_DO_LIMITE.compareTo(bloqueio) >= 0 ? JANELA_DO_LIMITE : bloqueio;
        Instant corte = agora.minus(maiorEspera.compareTo(intervalo) >= 0 ? maiorEspera : intervalo);
        pedidos.values().removeIf(lista -> lista.isEmpty() || lista.peekLast().isBefore(corte));
    }

    int quantidadeDeChaves() {
        return pedidos.size();
    }

    /** Esquece todos os pedidos. Os testes usam para não depender da ordem em que rodam. */
    public synchronized void limpar() {
        pedidos.clear();
    }

    private static long segundosAte(Instant agora, Instant momento) {
        return Math.max(1, (Duration.between(agora, momento).toMillis() + 999) / 1000);
    }
}
