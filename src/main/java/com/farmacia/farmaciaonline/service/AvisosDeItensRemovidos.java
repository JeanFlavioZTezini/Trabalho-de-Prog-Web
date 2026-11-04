package com.farmacia.farmaciaonline.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Avisa o dono do carrinho de que um item saiu porque o produto foi excluído de vez: o banco apaga o item em cascata
 * ({@code item_carrinho} com ON DELETE CASCADE), então o nome é anotado aqui antes, por carrinho, e mostrado uma vez.
 * Fica na memória: reinicia junto com a aplicação.
 */
@Component
public class AvisosDeItensRemovidos {

    /** Carrinho abandonado não guarda aviso para sempre. */
    static final Duration VALIDADE = Duration.ofDays(30);
    /** Acima disso, a próxima anotação varre os avisos vencidos. */
    static final int MAXIMO_DE_CHAVES = 1000;

    private record Aviso(List<String> nomes, Instant anotadoEm) {
    }

    private final Map<Long, Aviso> avisos = new ConcurrentHashMap<>();

    public void anotar(Long carrinhoId, String nome) {
        anotar(carrinhoId, nome, Instant.now());
    }

    void anotar(Long carrinhoId, String nome, Instant agora) {
        if (avisos.size() >= MAXIMO_DE_CHAVES) {
            avisos.values().removeIf(aviso -> aviso.anotadoEm().plus(VALIDADE).isBefore(agora));
        }
        avisos.compute(carrinhoId, (id, atual) -> {
            List<String> nomes = atual == null ? new ArrayList<>() : new ArrayList<>(atual.nomes());
            if (!nomes.contains(nome)) {
                nomes.add(nome);
            }
            return new Aviso(List.copyOf(nomes), agora);
        });
    }

    /** Os nomes anotados para o carrinho (e os esquece); sem aviso, lista vazia. */
    public List<String> consumir(Long carrinhoId) {
        return consumir(carrinhoId, Instant.now());
    }

    List<String> consumir(Long carrinhoId, Instant agora) {
        Aviso aviso = carrinhoId == null ? null : avisos.remove(carrinhoId);
        if (aviso == null || aviso.anotadoEm().plus(VALIDADE).isBefore(agora)) {
            return List.of();
        }
        return aviso.nomes();
    }

    int quantidadeDeChaves() {
        return avisos.size();
    }
}
