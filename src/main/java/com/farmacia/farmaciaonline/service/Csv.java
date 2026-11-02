package com.farmacia.farmaciaonline.service;

/**
 * Montagem de CSV para planilha (F-23): separador ";" (Excel em português), todo valor entre aspas com as aspas
 * internas dobradas, e valor que começa com = + - @ tab ou CR ganha um apóstrofo na frente para a planilha não o
 * executar como fórmula (CSV injection).
 */
final class Csv {

    /** Marca de ordem de bytes: faz o Excel abrir o arquivo como UTF-8 (acentos corretos). */
    static final String BOM = "﻿";

    private Csv() {
    }

    static void linha(StringBuilder destino, String... valores) {
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) {
                destino.append(';');
            }
            destino.append(celula(valores[i]));
        }
        destino.append("\r\n");
    }

    static String celula(String valor) {
        String texto = valor == null ? "" : valor;
        if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        return "\"" + texto.replace("\"", "\"\"") + "\"";
    }
}
