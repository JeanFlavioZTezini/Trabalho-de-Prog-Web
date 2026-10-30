package com.farmacia.farmaciaonline.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Guarda as fotos de produto numa pasta fora do JAR (app.uploads.dir) e devolve a URL pública. */
@Component
public class ArmazenamentoImagem {

    public static final String URL_BASE = "/uploads/produtos/";
    public static final long TAMANHO_MAXIMO = 2L * 1024 * 1024;

    private static final Map<String, String> TIPOS = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final Path pasta;

    public ArmazenamentoImagem(@Value("${app.uploads.dir:uploads}") String diretorio) {
        this.pasta = pastaDe(diretorio);
    }

    public static Path pastaDe(String diretorio) {
        return Path.of(diretorio).toAbsolutePath().normalize().resolve("produtos");
    }

    public Path pasta() {
        return pasta;
    }

    /** @return mensagem de erro, ou {@code null} se o arquivo é aceito */
    public String validar(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename() == null ? "arquivo" : arquivo.getOriginalFilename();
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            return "Imagem \"" + nome + "\" maior que 2 MB.";
        }
        String ext = extensao(arquivo);
        if (ext == null || !TIPOS.get(ext).equals(arquivo.getContentType())) {
            return "Imagem \"" + nome + "\": use JPG, PNG ou WebP.";
        }
        try (InputStream in = arquivo.getInputStream()) {
            if (!assinaturaConfere(ext, in.readNBytes(12))) {
                return "Imagem \"" + nome + "\": o conteúdo não corresponde a um arquivo " + ext.toUpperCase() + ".";
            }
        } catch (IOException e) {
            return "Não foi possível ler a imagem \"" + nome + "\".";
        }
        return null;
    }

    /** Grava com nome gerado (UUID): o nome enviado pelo usuário nunca vira caminho. */
    public String salvar(MultipartFile arquivo) throws IOException {
        String nome = UUID.randomUUID() + "." + extensao(arquivo);
        Files.createDirectories(pasta);
        try (InputStream in = arquivo.getInputStream()) {
            Files.copy(in, pasta.resolve(nome));
        }
        return URL_BASE + nome;
    }

    /** Apaga o arquivo se ele estiver na pasta de uploads (as fotos do seed em /images não são tocadas). */
    public void excluir(String url) {
        if (url == null || !url.startsWith(URL_BASE)) {
            return;
        }
        Path alvo = pasta.resolve(url.substring(URL_BASE.length())).normalize();
        if (!alvo.startsWith(pasta)) {
            return;
        }
        try {
            Files.deleteIfExists(alvo);
        } catch (IOException e) {
            // arquivo órfão não afeta o catálogo
        }
    }

    private static String extensao(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename();
        if (nome == null || nome.lastIndexOf('.') < 0) {
            return null;
        }
        String ext = nome.substring(nome.lastIndexOf('.') + 1).toLowerCase();
        return TIPOS.containsKey(ext) ? ext : null;
    }

    private static boolean assinaturaConfere(String ext, byte[] b) {
        return switch (ext) {
            case "jpg", "jpeg" -> b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "png" -> b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "webp" -> b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }
}
