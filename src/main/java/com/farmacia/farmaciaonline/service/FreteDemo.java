package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Regra de frete e prazo de demonstração, por faixa de CEP e UF (a mesma tabela está no README).
 * O CEP e a UF precisam combinar: o endereço é digitado à mão, então CEP de São Paulo com UF de outro estado
 * conta como fora da área atendida. O que não está na tabela não é atendido e o checkout bloqueia a entrega.
 */
@Component
public class FreteDemo {

    public record Cotacao(BigDecimal valor, int prazoDias) {}

    private record Faixa(String uf, int cepInicial, int cepFinal, Cotacao cotacao) {

        boolean atende(String ufDestino, int cep) {
            return uf.equals(ufDestino) && cep >= cepInicial && cep <= cepFinal;
        }
    }

    private static final List<Faixa> TABELA = List.of(
            new Faixa("SP", 1_000_000, 9_999_999, new Cotacao(new BigDecimal("9.90"), 2)),
            new Faixa("SP", 11_000_000, 19_999_999, new Cotacao(new BigDecimal("14.90"), 3)),
            new Faixa("RJ", 20_000_000, 28_999_999, new Cotacao(new BigDecimal("19.90"), 4)),
            new Faixa("MG", 30_000_000, 39_999_999, new Cotacao(new BigDecimal("19.90"), 4)));

    /** @return a cotação, ou vazio se a região não é atendida */
    public Optional<Cotacao> cotar(String uf, String cep) {
        if (uf == null || cep == null || !cep.matches("\\d{8}")) {
            return Optional.empty();
        }
        int numero = Integer.parseInt(cep);
        return TABELA.stream()
                .filter(faixa -> faixa.atende(uf, numero))
                .map(Faixa::cotacao)
                .findFirst();
    }
}
