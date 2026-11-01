package com.farmacia.farmaciaonline.service;

import java.time.LocalDateTime;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;

import lombok.Value;

/** Linha da lista de clientes do administrador (UC11). Sem senha, hash nem CPF completo. */
@Value
public class ClienteResumo {

    Long id;
    String nome;
    String cpfMascarado;
    String email;
    StatusUsuario status;
    String statusRotulo;
    LocalDateTime criadoEm;

    public static ClienteResumo de(Usuario usuario) {
        return new ClienteResumo(
                usuario.getId(),
                usuario.getNome(),
                ContatoMascarado.cpf(usuario.getCpf()),
                usuario.getEmail(),
                usuario.getStatus(),
                rotulo(usuario.getStatus()),
                ClienteAdminService.paraHorarioLocal(usuario.getCriadoEm()));
    }

    static String rotulo(StatusUsuario status) {
        return switch (status) {
            case PENDENTE -> "Pendente";
            case ATIVA -> "Ativa";
            case EXCLUIDA -> "Excluída";
        };
    }
}
