package com.farmacia.farmaciaonline.model;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class LocalRetirada {

    private String nome;

    private String cep;

    private String logradouro;

    private String numero;

    private String bairro;

    private String cidade;

    private String uf;

    private String horarioFuncionamento;

    public static LocalRetirada de(Loja loja) {
        LocalRetirada copia = new LocalRetirada();
        copia.setNome(loja.getNome());
        copia.setCep(loja.getCep());
        copia.setLogradouro(loja.getLogradouro());
        copia.setNumero(loja.getNumero());
        copia.setBairro(loja.getBairro());
        copia.setCidade(loja.getCidade());
        copia.setUf(loja.getUf());
        copia.setHorarioFuncionamento(loja.getHorarioFuncionamento());
        return copia;
    }
}
