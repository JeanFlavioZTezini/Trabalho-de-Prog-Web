package com.farmacia.farmaciaonline.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.farmacia.farmaciaonline.gateway.demo.DemoGatewayAdapter;
import com.farmacia.farmaciaonline.gateway.dto.RespostaPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;

@RestController
@RequestMapping("/demo/pagamento")
public class DemoPagamentoController {

    private final DemoGatewayAdapter demoGatewayAdapter;

    public DemoPagamentoController(DemoGatewayAdapter demoGatewayAdapter) {
        this.demoGatewayAdapter = demoGatewayAdapter;
    }

    @PostMapping("/forcar-status")
    public ResponseEntity<?> forcarStatus(
            @RequestParam("transacaoGatewayId") String transacaoGatewayId,
            @RequestParam("novoStatus") StatusPagamento novoStatus) {
        try {
            demoGatewayAdapter.forcarStatus(transacaoGatewayId, novoStatus);
            RespostaPagamento resposta = demoGatewayAdapter.consultar(transacaoGatewayId);
            return ResponseEntity.ok(resposta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}
