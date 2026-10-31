package com.farmacia.farmaciaonline.service;

/** Mostra só um pedaço do contato, para a pessoa reconhecer sem expor o dado inteiro. */
public final class ContatoMascarado {

    private ContatoMascarado() {
    }

    /** maria.silva@exemplo.com vira m***@exemplo.com. */
    public static String email(String email) {
        if (email == null || email.indexOf('@') < 1) {
            return "";
        }
        return email.charAt(0) + "***" + email.substring(email.indexOf('@'));
    }

    /** 67999998888 vira (67) 9****-8888. */
    public static String celular(String celular) {
        String digitos = celular == null ? "" : celular.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            return "";
        }
        return "(" + digitos.substring(0, 2) + ") " + digitos.charAt(2) + "****-" + digitos.substring(7);
    }
}
