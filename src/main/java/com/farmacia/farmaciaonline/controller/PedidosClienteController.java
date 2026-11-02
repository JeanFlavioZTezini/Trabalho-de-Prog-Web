package com.farmacia.farmaciaonline.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
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
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.service.PedidosClienteService;
import com.farmacia.farmaciaonline.service.PedidosClienteService.FiltroMeusPedidos;
import com.farmacia.farmaciaonline.service.PeriodoFiltro;

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
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "0") int pagina, Model model, HttpServletResponse response) {
        Long usuarioId = idDe(usuario);
        StatusPedido filtroStatus = Arrays.stream(StatusPedido.values()).filter(s -> s.name().equals(status))
                .findFirst().orElse(null);
        FiltroMeusPedidos filtro = new FiltroMeusPedidos(filtroStatus, new PeriodoFiltro(de, ate));
        String erroFiltro = filtro.periodo().erro();
        model.addAttribute("erroAoCarregar", false);
        model.addAttribute("status", filtroStatus == null ? "" : filtroStatus.name());
        model.addAttribute("de", de);
        model.addAttribute("ate", ate);
        model.addAttribute("filtrado", filtro.informado());
        model.addAttribute("erroFiltro", erroFiltro);
        model.addAttribute("statuses", StatusPedido.values());
        try {
            model.addAttribute("lista", erroFiltro != null
                    ? new PedidosClienteService.PaginaDePedidos(List.of(), 0, 0, 0)
                    : service.listar(usuarioId, filtro, pagina));
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
