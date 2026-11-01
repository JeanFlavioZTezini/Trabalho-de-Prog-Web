package com.farmacia.farmaciaonline.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.farmacia.farmaciaonline.security.MesclagemCarrinhoNoLogin;
import com.farmacia.farmaciaonline.service.CarrinhoService;
import com.farmacia.farmaciaonline.service.DonoCarrinho;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * Entrega ao cabeçalho (fragments/layout.html) o número de unidades no carrinho e o aviso "seu carrinho foi mantido"
 * que o login deixa na sessão.
 *
 * Os dois valores são lidos só quando o template pede: resposta que é redirect não gasta consulta e, principalmente,
 * não consome o aviso (que precisa aparecer na primeira página realmente exibida). O {@link CarrinhoService} vem por
 * {@link ObjectProvider} para os testes de controller que não o simulam continuarem subindo.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class CarrinhoContagemAdvice {

    private final ObjectProvider<CarrinhoService> carrinhoService;

    @ModelAttribute("carrinhoNoCabecalho")
    public CarrinhoNoCabecalho carrinhoNoCabecalho(HttpServletRequest request, Authentication autenticacao) {
        return new CarrinhoNoCabecalho(request, autenticacao, carrinhoService.getIfAvailable());
    }

    public static final class CarrinhoNoCabecalho {
        private final HttpServletRequest request;
        private final Authentication autenticacao;
        private final CarrinhoService servico;

        private Integer unidades;
        private boolean avisoLido;
        private String avisoMantido;

        private CarrinhoNoCabecalho(HttpServletRequest request, Authentication autenticacao, CarrinhoService servico) {
            this.request = request;
            this.autenticacao = autenticacao;
            this.servico = servico;
        }

        public int getUnidades() {
            if (unidades == null) {
                DonoCarrinho dono = CarrinhoController.donoParaLeitura(request, autenticacao);
                unidades = servico == null || dono == null ? 0 : servico.contarUnidades(dono);
            }
            return unidades;
        }

        /** Texto do aviso, uma única vez: a primeira leitura tira o aviso da sessão. */
        public String getAvisoMantido() {
            if (!avisoLido) {
                avisoLido = true;
                HttpSession sessao = request.getSession(false);
                if (sessao != null) {
                    Object guardado = sessao.getAttribute(MesclagemCarrinhoNoLogin.AVISO_MANTIDO_NA_SESSAO);
                    sessao.removeAttribute(MesclagemCarrinhoNoLogin.AVISO_MANTIDO_NA_SESSAO);
                    if (guardado instanceof String texto) {
                        avisoMantido = texto;
                    }
                }
            }
            return avisoMantido;
        }
    }
}
