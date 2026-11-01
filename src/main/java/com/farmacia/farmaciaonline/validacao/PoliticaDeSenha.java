package com.farmacia.farmaciaonline.validacao;

import java.nio.charset.StandardCharsets;

/**
 * Regra de senha do sistema, usada no cadastro (F-04) e na recuperação de senha (F-06): 8 caracteres ou mais, com letra e
 * número, e no máximo 72 bytes (o BCrypt só lê os primeiros 72 bytes; acima disso o Spring recusa).
 */
public final class PoliticaDeSenha {

    /** Para o {@code @Pattern}: vazio passa, porque quem exige o preenchimento é o {@code @NotBlank}. */
    public static final String PADRAO = "^$|^(?=.*\\p{L})(?=.*\\d).{8,}$";

    public static final String MENSAGEM = "A senha deve ter 8 caracteres ou mais, com pelo menos uma letra e um número.";

    public static final int LIMITE_BYTES = 72;

    public static final String MENSAGEM_LIMITE =
            "A senha é longa demais: use até 72 caracteres (letras com acento contam mais).";

    private PoliticaDeSenha() {
    }

    public static boolean excedeLimite(String senha) {
        return senha != null && senha.getBytes(StandardCharsets.UTF_8).length > LIMITE_BYTES;
    }
}
