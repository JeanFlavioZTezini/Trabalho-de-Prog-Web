package com.farmacia.farmaciaonline.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.gateway.dto.TokenCartao;
import com.farmacia.farmaciaonline.model.CartaoSalvo;
import com.farmacia.farmaciaonline.repository.CartaoSalvoRepository;
import com.farmacia.farmaciaonline.repository.PagamentoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Cartões salvos (F-25, UC09 opcional). Só o token do gateway e o que dá para mostrar (bandeira, final, validade,
 * nome) são gravados; número completo e CVV nunca. Todo acesso passa pelo dono: cartão de outra pessoa é 404.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CartaoSalvoService {

    private final CartaoSalvoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final PagamentoRepository pagamentoRepository;

    @Transactional(readOnly = true)
    public List<CartaoSalvo> listar(Long usuarioId) {
        return repository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public CartaoSalvo doUsuario(Long usuarioId, Long cartaoId) {
        return repository.findByIdAndUsuarioId(cartaoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** O mesmo cartão (bandeira, final e validade) não é salvo duas vezes. */
    public CartaoSalvo salvar(Long usuarioId, TokenCartao token, String nomeImpresso) {
        return repository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId).stream()
                .filter(c -> c.getBandeira().equals(token.bandeira()) && c.getUltimos4().equals(token.ultimos4())
                        && c.getValidadeMes() == token.validadeMes() && c.getValidadeAno() == token.validadeAno())
                .findFirst()
                .orElseGet(() -> {
                    CartaoSalvo cartao = new CartaoSalvo();
                    cartao.setUsuario(usuarioRepository.getReferenceById(usuarioId));
                    cartao.setTokenGateway(token.token());
                    cartao.setBandeira(token.bandeira());
                    cartao.setUltimos4(token.ultimos4());
                    cartao.setValidadeMes(token.validadeMes());
                    cartao.setValidadeAno(token.validadeAno());
                    cartao.setNomeImpresso(nomeImpresso == null || nomeImpresso.isBlank() ? "-"
                            : nomeImpresso.trim().substring(0, Math.min(nomeImpresso.trim().length(), 100)));
                    return repository.saveAndFlush(cartao);
                });
    }

    /** Pagamentos antigos ficam com o cartão nulo (ON DELETE SET NULL); só o token é descartado. */
    public void remover(Long usuarioId, Long cartaoId) {
        repository.delete(doUsuario(usuarioId, cartaoId));
        repository.flush();
    }

    /** Registra no pagamento qual cartão salvo foi usado. */
    public void vincularAoPagamento(Long pagamentoId, Long cartaoId) {
        pagamentoRepository.findById(pagamentoId)
                .ifPresent(pagamento -> pagamento.setCartaoSalvo(repository.getReferenceById(cartaoId)));
    }
}
