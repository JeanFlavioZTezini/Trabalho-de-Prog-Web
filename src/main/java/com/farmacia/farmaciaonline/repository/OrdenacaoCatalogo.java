package com.farmacia.farmaciaonline.repository;

public enum OrdenacaoCatalogo {
    NOME,
    NOME_DESC,
    PRECO,
    PRECO_DESC,
    /** Mais comprados (qtd_vendida). */
    MAIS_VENDIDOS,
    /** Onde o termo casa; sem termo cai para NOME. */
    RELEVANCIA
}
