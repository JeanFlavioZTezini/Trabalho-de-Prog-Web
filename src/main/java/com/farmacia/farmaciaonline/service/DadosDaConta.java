package com.farmacia.farmaciaonline.service;

import java.time.LocalDate;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;

import lombok.Value;

@Value
public class DadosDaConta {

    String nome;
    String cpfFormatado;
    LocalDate dataNascimento;
    Genero genero;
    String email;
    boolean emailConfirmado;
    String celularFormatado;
    boolean celularConfirmado;

    public static DadosDaConta de(Usuario usuario) {
        return new DadosDaConta(
                usuario.getNome(),
                formatarCpf(usuario.getCpf()),
                usuario.getDataNascimento(),
                usuario.getGenero(),
                usuario.getEmail(),
                usuario.getEmailConfirmadoEm() != null,
                formatarCelular(usuario.getCelular()),
                usuario.getCelularConfirmadoEm() != null);
    }

    static String formatarCpf(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}")) {
            return null;
        }
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    static String formatarCelular(String celular) {
        if (celular == null || !celular.matches("\\d{11}")) {
            return null;
        }
        return "(" + celular.substring(0, 2) + ") " + celular.substring(2, 7) + "-" + celular.substring(7);
    }
}
