package com.farmacia.farmaciaonline.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Filtro de período das listas (F-23): datas do calendário de São Paulo, as duas inclusivas. Vira um intervalo
 * [início, fim) em Instant; sem data, usa limites bem largos (o PostgreSQL não aceita parâmetro nulo sem tipo).
 */
public record PeriodoFiltro(LocalDate de, LocalDate ate) {

    public static final PeriodoFiltro VAZIO = new PeriodoFiltro(null, null);

    public static final ZoneId FUSO_SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    static final ZoneId FUSO = FUSO_SAO_PAULO;
    private static final Instant MINIMO = LocalDate.of(1900, 1, 1).atStartOfDay(FUSO).toInstant();
    private static final Instant MAXIMO = LocalDate.of(9999, 12, 31).atStartOfDay(FUSO).toInstant();

    public boolean informado() {
        return de != null || ate != null;
    }

    /** Mensagem para a tela, ou null se o período é válido. */
    public String erro() {
        return de != null && ate != null && de.isAfter(ate)
                ? "A data inicial não pode ser depois da data final."
                : null;
    }

    public Instant inicio() {
        return de == null ? MINIMO : de.atStartOfDay(FUSO).toInstant();
    }

    /** Exclusivo: começo do dia seguinte a {@code ate}, para incluir o dia inteiro. */
    public Instant fim() {
        return ate == null ? MAXIMO : ate.plusDays(1).atStartOfDay(FUSO).toInstant();
    }
}
