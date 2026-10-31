package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.mensagem.EnvioDeMensagem;
import com.farmacia.farmaciaonline.model.ItemPedido;
import com.farmacia.farmaciaonline.model.LocalRetirada;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.PedidoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResumoPedidoService {

    private static final Logger log = LoggerFactory.getLogger(ResumoPedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final EnvioDeMensagem envio;

    public boolean enviarSePendente(Long pedidoId) {
        if (pedidoRepository.marcarResumoEnviado(pedidoId, Instant.now()) == 0) {
            return true;
        }
        try {
            Pedido pedido = pedidoRepository.buscarParaResumo(pedidoId).orElseThrow();
            envio.enviar(CanalCodigo.EMAIL, pedido.getUsuario().getEmail(),
                    "Resumo do pedido nº " + pedido.getId(), texto(pedido));
            return true;
        } catch (RuntimeException e) {
            log.warn("Não foi possível enviar o resumo do pedido {}: {}", pedidoId, e.getClass().getSimpleName());
            pedidoRepository.desmarcarResumoEnviado(pedidoId);
            return false;
        }
    }

    public boolean reenviar(Long usuarioId, Long pedidoId) {
        pedidoRepository.findByIdAndUsuarioId(pedidoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return enviarSePendente(pedidoId);
    }

    static String texto(Pedido pedido) {
        StringBuilder texto = new StringBuilder("Pedido nº ").append(pedido.getId()).append('\n')
                .append("Situação: ").append(pedido.getStatus().getRotulo()).append("\n\nItens:\n");
        for (ItemPedido item : pedido.getItens()) {
            texto.append("- ").append(item.getQuantidade()).append(" x ").append(item.getNomeProduto()).append(' ')
                    .append(item.getDescricaoApresentacao()).append(": ").append(reais(item.getSubtotal())).append('\n');
        }
        texto.append("\nSubtotal: ").append(reais(pedido.getSubtotal()))
                .append("\nFrete: ").append(reais(pedido.getFrete()))
                .append("\nTotal: ").append(reais(pedido.getTotal())).append("\n\n");
        if (pedido.getTipoEntrega() == TipoEntrega.RETIRADA && pedido.getLocalRetirada() != null) {
            LocalRetirada loja = pedido.getLocalRetirada();
            texto.append("Retirada em ").append(loja.getNome()).append(": ").append(loja.getLogradouro()).append(", ")
                    .append(loja.getNumero()).append(" - ").append(loja.getCidade()).append('/').append(loja.getUf());
        } else if (pedido.getEnderecoEntrega() != null) {
            texto.append("Entrega em até ").append(pedido.getPrazoEntregaDias()).append(" dias: ")
                    .append(pedido.getEnderecoEntrega().getLogradouro()).append(", ")
                    .append(pedido.getEnderecoEntrega().getNumero()).append(" - ")
                    .append(pedido.getEnderecoEntrega().getCidade()).append('/')
                    .append(pedido.getEnderecoEntrega().getUf());
        }
        if (pedido.temItemQueExigeReceita()) {
            texto.append("\n\nAtenção: apresente a receita na loja para retirar este pedido.");
        }
        return texto.toString();
    }

    private static String reais(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor);
    }
}
