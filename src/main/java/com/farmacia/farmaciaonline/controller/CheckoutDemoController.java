package com.farmacia.farmaciaonline.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.gateway.demo.DemoGatewayAdapter;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.PagamentoPedidoService;

import lombok.RequiredArgsConstructor;

@Controller
@Profile("demo")
@RequestMapping("/checkout/pedidos/{id}/demo")
@RequiredArgsConstructor
public class CheckoutDemoController {

    private final DemoGatewayAdapter gatewayDemo;
    private final PagamentoPedidoService pagamentos;

    @PostMapping("/aprovar")
    public String aprovar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return simular(usuario, id, StatusPagamento.APROVADO);
    }

    @PostMapping("/expirar")
    public String expirar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return simular(usuario, id, StatusPagamento.EXPIRADO);
    }

    private String simular(UsuarioAutenticado usuario, Long id, StatusPagamento status) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        pagamentos.transacaoPendente(usuario.getId(), id).ifPresent(transacao -> {
            try {
                gatewayDemo.forcarStatus(transacao, status);
            } catch (IllegalArgumentException e) {
            }
            pagamentos.processarEvento(transacao);
        });
        return "redirect:/checkout/pedidos/" + id;
    }
}
