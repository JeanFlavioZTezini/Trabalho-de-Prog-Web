package com.farmacia.farmaciaonline.acessibilidade;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Revisão visual: as cores, sombras e camadas do {@code style.css} vêm de {@code tokens.css}. Uma cor escrita à mão foge
 * do {@link ContrasteTokensTest}, que só confere os tokens. Teste unitário puro: sem Spring e sem banco.
 */
class EstiloSemCorSoltaTest {

    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{3,8}\\b");
    private static final Pattern RGB = Pattern.compile("\\b(rgba?|hsla?)\\(");
    private static final Pattern Z_INDEX_FIXO = Pattern.compile("z-index:\\s*-?\\d");

    private static List<String> declaracoes;

    @BeforeAll
    static void lerEstilo() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/static/css/style.css"));
        String semComentarios = css.replaceAll("(?s)/\\*.*?\\*/", "");
        declaracoes = new ArrayList<>();
        // Só o que está entre ":" e ";" é valor de propriedade; seletores como "#conteudo" ficam de fora.
        Matcher valor = Pattern.compile(":([^;{}]*);").matcher(semComentarios);
        while (valor.find()) {
            declaracoes.add(valor.group(0).trim());
        }
    }

    @Test
    @DisplayName("nenhuma cor em hexadecimal no style.css: use var(--cor-*) de tokens.css")
    void semHexadecimal() {
        assertThat(declaracoes).filteredOn(d -> HEX.matcher(d).find()).isEmpty();
    }

    @Test
    @DisplayName("nenhuma cor em rgb()/rgba()/hsl() no style.css: sombras usam var(--sombra-*)")
    void semRgb() {
        assertThat(declaracoes).filteredOn(d -> RGB.matcher(d).find()).isEmpty();
    }

    @Test
    @DisplayName("z-index só pela escala de tokens.css (var(--z-*))")
    void zIndexPelaEscala() {
        assertThat(declaracoes).filteredOn(d -> Z_INDEX_FIXO.matcher(d).find()).isEmpty();
    }

    @Test
    @DisplayName("a leitura das declarações funciona (o teste não passa à toa)")
    void leituraFunciona() {
        assertThat(declaracoes).anyMatch(d -> d.contains("var(--cor-azul)"));
    }
}
