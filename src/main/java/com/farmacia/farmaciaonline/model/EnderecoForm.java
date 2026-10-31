package com.farmacia.farmaciaonline.model;

import java.util.List;
import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EnderecoForm {

    public static final List<String> UFS = List.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR",
            "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    @NotBlank(message = "Informe o destinatário.")
    @Size(max = 150, message = "O destinatário deve ter no máximo 150 caracteres.")
    private String destinatario;

    @NotBlank(message = "Informe o CEP.")
    @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido: use 8 números, por exemplo 01310-100.")
    private String cep;

    @NotBlank(message = "Informe o logradouro.")
    @Size(max = 200, message = "O logradouro deve ter no máximo 200 caracteres.")
    private String logradouro;

    @NotBlank(message = "Informe o número (ou S/N).")
    @Size(max = 20, message = "O número deve ter no máximo 20 caracteres.")
    private String numero;

    @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres.")
    private String complemento;

    @NotBlank(message = "Informe o bairro.")
    @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres.")
    private String bairro;

    @NotBlank(message = "Informe a cidade.")
    @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres.")
    private String cidade;

    @NotBlank(message = "Selecione a UF.")
    @Pattern(regexp = "^(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)$",
            message = "UF inválida: use a sigla de um estado, por exemplo SP.")
    private String uf;

    @Size(max = 200, message = "O ponto de referência deve ter no máximo 200 caracteres.")
    private String referencia;

    public void setCep(String cep) {
        this.cep = cep == null ? null : cep.trim();
    }

    public void setUf(String uf) {
        this.uf = uf == null ? null : uf.trim().toUpperCase(Locale.ROOT);
    }
}
