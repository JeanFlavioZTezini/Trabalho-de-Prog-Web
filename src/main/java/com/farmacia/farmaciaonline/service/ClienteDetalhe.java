package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;

import lombok.Value;

/**
 * Detalhe somente leitura de um cliente (UC11). De propósito não tem senha, hash, tentativas de login, termos,
 * códigos de verificação nem dados de pagamento. O CPF aparece inteiro porque a consulta é auditada.
 */
@Value
public class ClienteDetalhe {

    Long id;
    String nome;
    String cpfFormatado;
    String email;
    boolean emailConfirmado;
    String celularFormatado;
    boolean celularConfirmado;
    LocalDate dataNascimento;
    Genero genero;
    StatusUsuario status;
    String statusRotulo;
    LocalDateTime criadoEm;
    List<PedidoResumo> pedidosRecentes;

    /** Linha do histórico mínimo: número, data, total e status. Sem itens nem pagamento. */
    @Value
    public static class PedidoResumo {

        Long id;
        LocalDateTime criadoEm;
        BigDecimal total;
        String statusRotulo;

        static PedidoResumo de(Pedido pedido) {
            return new PedidoResumo(pedido.getId(), ClienteAdminService.paraHorarioLocal(pedido.getCriadoEm()),
                    pedido.getTotal(), pedido.getStatus().getRotulo());
        }
    }

    static ClienteDetalhe de(Usuario usuario, List<Pedido> pedidos) {
        return new ClienteDetalhe(
                usuario.getId(),
                usuario.getNome(),
                DadosDaConta.formatarCpf(usuario.getCpf()),
                usuario.getEmail(),
                usuario.getEmailConfirmadoEm() != null,
                DadosDaConta.formatarCelular(usuario.getCelular()),
                usuario.getCelularConfirmadoEm() != null,
                usuario.getDataNascimento(),
                usuario.getGenero(),
                usuario.getStatus(),
                ClienteResumo.rotulo(usuario.getStatus()),
                ClienteAdminService.paraHorarioLocal(usuario.getCriadoEm()),
                pedidos.stream().map(PedidoResumo::de).toList());
    }
}
