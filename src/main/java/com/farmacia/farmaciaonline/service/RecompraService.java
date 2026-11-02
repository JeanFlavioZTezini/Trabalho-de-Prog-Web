package com.farmacia.farmaciaonline.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.repository.PedidoRepository;

import lombok.RequiredArgsConstructor;

/**
 * "Comprar novamente" (UC06-A3, F-25). Os itens passam um a um por {@link CarrinhoService#adicionar}, que revalida
 * produto/apresentação ativos, estoque, limite por pedido e preço atual. Sem transação em volta: cada item tem a
 * própria, então um item recusado vira aviso e não desfaz os outros. O preço do pedido antigo nunca é reaproveitado.
 */
@Service
@RequiredArgsConstructor
public class RecompraService {

    private final CheckoutService checkout;
    private final PedidoRepository pedidoRepository;
    private final CarrinhoService carrinho;
    private final TransactionTemplate transacao;

    public Resultado comprarNovamente(Long usuarioId, Long pedidoId) {
        checkout.exigirClienteAtivo(usuarioId);
        List<ItemAnterior> itens = transacao.execute(status -> pedidoRepository.findByIdAndUsuarioId(pedidoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
                .getItens().stream()
                .map(i -> new ItemAnterior(i.getApresentacao().getId(), i.getNomeProduto(), i.getQuantidade()))
                .toList());

        DonoCarrinho dono = DonoCarrinho.deUsuario(usuarioId);
        List<String> avisos = new ArrayList<>();
        int adicionados = 0;
        for (ItemAnterior item : itens) {
            try {
                avisos.addAll(carrinho.adicionar(dono, item.apresentacaoId(), item.quantidade()));
                adicionados++;
            } catch (CarrinhoException recusado) {
                avisos.add(item.nome() + ": " + recusado.getMessage());
            }
        }
        return new Resultado(adicionados, itens.size(), avisos);
    }

    private record ItemAnterior(Long apresentacaoId, String nome, int quantidade) {
    }

    public record Resultado(int adicionados, int total, List<String> avisos) {
    }
}
