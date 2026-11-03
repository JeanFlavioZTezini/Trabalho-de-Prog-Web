package com.farmacia.farmaciaonline.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Tentativas de senha erradas contadas na memória, por identificador digitado (e-mail ou CPF), com as mesmas regras do
 * login: {@link LoginTentativasService#MAXIMO_FALHAS} erros bloqueiam por {@link LoginTentativasService#DURACAO_BLOQUEIO}.
 * Serve onde o banco não conta: identificador que não é de conta ativa (login) e a retomada da confirmação (conta
 * pendente). Conta também identificador inexistente, para o bloqueio não revelar se a conta existe. Reinicia junto com a
 * aplicação.
 */
@Component
public class LimiteDeTentativasEmMemoria {

    /** Acima disso, o próximo registro varre as entradas vencidas: identificadores inventados não acumulam. */
    static final int MAXIMO_DE_CHAVES = 1000;
    static final int TAMANHO_MAXIMO_DA_CHAVE = 150;

    private final Map<String, Registro> registros = new HashMap<>();

    public void registrarFalha(String identificador) {
        registrarFalha(identificador, Instant.now());
    }

    public boolean bloqueado(String identificador) {
        return bloqueado(identificador, Instant.now());
    }

    public synchronized void limpar(String identificador) {
        String chave = chave(identificador);
        if (chave != null) {
            registros.remove(chave);
        }
    }

    synchronized void registrarFalha(String identificador, Instant agora) {
        String chave = chave(identificador);
        if (chave == null) {
            return;
        }
        if (registros.size() > MAXIMO_DE_CHAVES) {
            registros.values().removeIf(r -> r.vencido(agora));
        }
        Registro registro = registros.get(chave);
        // Uma janela de bloqueio vencida inicia uma nova sequência de tentativas, como no login.
        if (registro == null || registro.vencido(agora)) {
            registro = new Registro();
            registros.put(chave, registro);
        }
        registro.falhas++;
        registro.ultimaFalha = agora;
        if (registro.falhas >= LoginTentativasService.MAXIMO_FALHAS) {
            registro.bloqueadoAte = agora.plus(LoginTentativasService.DURACAO_BLOQUEIO);
        }
    }

    synchronized boolean bloqueado(String identificador, Instant agora) {
        String chave = chave(identificador);
        Registro registro = chave == null ? null : registros.get(chave);
        return registro != null && registro.bloqueadoAte != null && registro.bloqueadoAte.isAfter(agora);
    }

    synchronized int quantidadeDeChaves() {
        return registros.size();
    }

    /** Esquece tudo. Os testes usam para não depender da ordem em que rodam. */
    public synchronized void limparTudo() {
        registros.clear();
    }

    /** E-mail em minúsculas; CPF só com os dígitos (com ou sem pontos é a mesma chave); no máximo 150 caracteres. */
    private static String chave(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return null;
        }
        String valor = identificador.trim().toLowerCase(Locale.ROOT);
        if (!valor.contains("@")) {
            String digitos = valor.replaceAll("\\D", "");
            if (digitos.length() == 11) {
                valor = digitos;
            }
        }
        return valor.length() > TAMANHO_MAXIMO_DA_CHAVE ? valor.substring(0, TAMANHO_MAXIMO_DA_CHAVE) : valor;
    }

    private static final class Registro {
        private int falhas;
        private Instant ultimaFalha;
        private Instant bloqueadoAte;

        /** Nem bloqueado nem com falha recente: já não pesa em nada. */
        boolean vencido(Instant agora) {
            if (bloqueadoAte != null) {
                return !bloqueadoAte.isAfter(agora);
            }
            return ultimaFalha.plus(LoginTentativasService.DURACAO_BLOQUEIO).isBefore(agora);
        }
    }
}
