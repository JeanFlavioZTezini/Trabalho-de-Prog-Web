package com.farmacia.farmaciaonline.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.PedidosClienteService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Meus pedidos" (UC06). Só leitura: não há POST aqui, então o cliente não altera status nem dados do pedido (quem
 * muda status é o administrador, na F-17). O id do dono vem sempre da sessão. A rota exige login pela regra geral do
 * {@code SecurityConfig} (qualquer rota fora da lista de públicas); sessão expirada vai para o login.
 */
@Slf4j
@Controller
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidosClienteController {

    private final PedidosClienteService service;

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(defaultValue = "0") int pagina, Model model, HttpServletResponse response) {
        Long usuarioId = idDe(usuario);
        model.addAttribute("erroAoCarregar", false);
        try {
            model.addAttribute("lista", service.listar(usuarioId, pagina));
        } catch (DataAccessException | TransactionException e) {
            return falhaAoCarregar(e, model, response, "pedidos/lista");
        }
        return "pedidos/lista";
    }

    @GetMapping("/{id}")
    public String detalhe(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id, Model model,
            HttpServletResponse response) {
        Long usuarioId = idDe(usuario);
        model.addAttribute("erroAoCarregar", false);
        model.addAttribute("pedidoId", id);
        try {
            model.addAttribute("pedido", service.detalhar(usuarioId, id));
        } catch (DataAccessException | TransactionException e) {
            return falhaAoCarregar(e, model, response, "pedidos/detalhe");
        }
        return "pedidos/detalhe";
    }

    /** UC06-E1: a tela avisa e oferece tentar de novo; nada do erro técnico aparece para a pessoa. */
    private static String falhaAoCarregar(Exception e, Model model, HttpServletResponse response, String tela) {
        log.warn("Falha ao carregar pedidos do cliente: {}", e.getClass().getSimpleName());
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        model.addAttribute("erroAoCarregar", true);
        return tela;
    }

    private static Long idDe(UsuarioAutenticado usuario) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return usuario.getId();
    }
}
