package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ArmazenamentoImagemTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @TempDir
    Path tmp;

    private ArmazenamentoImagem armazenamento() {
        return new ArmazenamentoImagem(tmp.toString());
    }

    @Test
    void aceitaPngValido() {
        var f = new MockMultipartFile("f", "foto.png", "image/png", PNG);
        assertThat(armazenamento().validar(f)).isNull();
    }

    @Test
    void recusaExtensaoTipoOuConteudoInvalidos() {
        var a = armazenamento();
        assertThat(a.validar(new MockMultipartFile("f", "x.exe", "image/png", PNG))).isNotNull();
        assertThat(a.validar(new MockMultipartFile("f", "x.png", "text/html", PNG))).isNotNull();
        assertThat(a.validar(new MockMultipartFile("f", "x.png", "image/png", "<html>".getBytes()))).isNotNull();
    }

    @Test
    void recusaArquivoMaiorQueOLimite() {
        byte[] grande = new byte[(int) ArmazenamentoImagem.TAMANHO_MAXIMO + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);
        assertThat(armazenamento().validar(new MockMultipartFile("f", "x.png", "image/png", grande))).isNotNull();
    }

    @Test
    void salvaComNomeGeradoEExcluiSoDentroDaPasta() throws IOException {
        var a = armazenamento();
        String url = a.salvar(new MockMultipartFile("f", "../../etc/passwd.png", "image/png", PNG));
        assertThat(url).startsWith(ArmazenamentoImagem.URL_BASE).doesNotContain("..", "passwd");
        Path arquivo = a.pasta().resolve(url.substring(ArmazenamentoImagem.URL_BASE.length()));
        assertThat(arquivo).exists();

        a.excluir("/images/produtos/placeholder.svg");
        a.excluir(ArmazenamentoImagem.URL_BASE + "../../fora.txt");
        a.excluir(url);
        assertThat(Files.exists(arquivo)).isFalse();
    }
}
