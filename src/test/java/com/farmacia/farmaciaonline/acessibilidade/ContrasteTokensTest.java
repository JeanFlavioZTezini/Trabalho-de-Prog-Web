package com.farmacia.farmaciaonline.acessibilidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Confere o contraste (WCAG 2.1 AA) dos pares de cores de {@code tokens.css} que estão na tabela de
 * {@code docs/acessibilidade.md}. Teste unitário puro: sem Spring e sem banco.
 * Se alguém trocar uma cor e um par cair abaixo da meta, o teste falha e diz qual.
 */
class ContrasteTokensTest {

    private static final Pattern TOKEN = Pattern.compile("--([\\w-]+):\\s*(#[0-9A-Fa-f]{6})");

    private static final Map<String, String> TOKENS = new HashMap<>();

    @BeforeAll
    static void lerTokens() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/static/css/tokens.css"));
        Matcher encontrado = TOKEN.matcher(css);
        while (encontrado.find()) {
            TOKENS.put(encontrado.group(1), encontrado.group(2));
        }
    }

    @Test
    @DisplayName("o branco principal continua sendo #FFFFFF")
    void brancoPrincipalNaoMuda() {
        assertThat(TOKENS.get("cor-branco")).isEqualToIgnoringCase("#FFFFFF");
    }

    @ParameterizedTest(name = "{0} sobre {1} deve ter pelo menos {2}:1")
    @CsvSource({
            "cor-texto, cor-branco, 4.5",
            "cor-texto, cor-fundo, 4.5",
            "cor-texto, cor-azul-suave, 4.5",
            "cor-branco, cor-azul, 4.5",
            "cor-branco, cor-azul-hover, 4.5",
            "cor-azul, cor-branco, 4.5",
            "cor-azul, cor-fundo, 4.5",
            "cor-azul, cor-azul-suave, 4.5",
            "cor-azul-hover, cor-branco, 4.5",
            "cor-texto-hint, cor-branco, 4.5",
            "cor-texto-hint, cor-fundo, 4.5",
            "cor-erro, cor-branco, 4.5",
            "cor-erro, cor-fundo, 4.5",
            "cor-sucesso, cor-branco, 4.5",
            "cor-sucesso, cor-fundo, 4.5",
            "cor-borda-controle, cor-branco, 3.0",
            "cor-azul, cor-branco, 3.0",
            "cor-azul, cor-fundo, 3.0",
            "cor-branco, cor-azul, 3.0",
            "cor-erro, cor-branco, 3.0",
            "cor-erro, cor-fundo, 3.0",
            "cor-sucesso, cor-fundo, 3.0"
    })
    void parDeCoresAtingeAMetaDoWcagAA(String frente, String fundo, double meta) {
        assertThat(TOKENS).containsKeys(frente, fundo);

        double razao = razao(TOKENS.get(frente), TOKENS.get(fundo));

        assertThat(razao).as("%s sobre %s = %.2f:1", frente, fundo, razao).isGreaterThanOrEqualTo(meta);
    }

    private static double razao(String corA, String corB) {
        double luminanciaA = luminancia(corA);
        double luminanciaB = luminancia(corB);
        return (Math.max(luminanciaA, luminanciaB) + 0.05) / (Math.min(luminanciaA, luminanciaB) + 0.05);
    }

    private static double luminancia(String hex) {
        double r = canal(Integer.parseInt(hex.substring(1, 3), 16));
        double g = canal(Integer.parseInt(hex.substring(3, 5), 16));
        double b = canal(Integer.parseInt(hex.substring(5, 7), 16));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double canal(int valor) {
        double c = valor / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
