package com.farmacia.farmaciaonline.controller;

import java.time.LocalDate;
import java.util.Arrays;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.AcaoPedidoInvalidaException;
import com.farmacia.farmaciaonline.service.AdminPedidosService;
import com.farmacia.farmaciaonline.service.AdminPedidosService.FiltroPedidos;
import com.farmacia.farmaciaonline.service.PeriodoFiltro;
import com.farmacia.farmaciaonline.service.ConflitoPedidoException;

import lombok.RequiredArgsConstructor;

/** UC17: consulta e atualização de pedidos; /admin/** é exclusivo de ADMIN e os POST mantêm CSRF habilitado. */
@Controller
@RequestMapping("/admin/pedidos")
@RequiredArgsConstructor
public class AdminPedidoController {

    private static final String REDIRECT_DETALHE = "redirect:/admin/pedidos/{id}";
    private static final int TAMANHO_PAGINA = 20;

    private final AdminPedidosService service;

    @GetMapping
    public String listar(@RequestParam(required = false) Long numero,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "0") int pagina, Model model) {
        StatusPedido filtroStatus = statusDe(status);
        PeriodoFiltro periodo = new PeriodoFiltro(de, ate);
        PageRequest pageable = PageRequest.of(Math.max(0, pagina), TAMANHO_PAGINA, Sort.by(Sort.Direction.DESC, "id"));
        model.addAttribute("pagina", periodo.erro() != null ? Page.empty(pageable)
                : service.listar(new FiltroPedidos(numero, filtroStatus, cliente, periodo), pageable));
        model.addAttribute("numero", numero);
        model.addAttribute("status", filtroStatus);
        model.addAttribute("cliente", cliente);
        model.addAttribute("de", de);
        model.addAttribute("ate", ate);
        model.addAttribute("erroFiltro", periodo.erro());
        model.addAttribute("statuses", StatusPedido.values());
        return "admin/pedidos";
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        model.addAttribute("pedido", service.detalhar(id));
        model.addAttribute("statuses", StatusPedido.values());
        return "admin/pedido-detalhe";
    }

    @PostMapping("/{id}/status")
    public String atualizarStatus(@PathVariable Long id, @RequestParam Long versao,
            @RequestParam StatusPedido novoStatus, @RequestParam(required = false) String observacao,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            service.transicionar(id, versao, novoStatus, administradorId(authentication), observacao);
            redirect.addFlashAttribute("sucesso", "Status do pedido atualizado.");
        } catch (ConflitoPedidoException | AcaoPedidoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            redirect.addFlashAttribute("observacao", observacao);
            redirect.addFlashAttribute("novoStatusEscolhido", novoStatus.name());
        }
        return REDIRECT_DETALHE;
    }

    @PostMapping("/{id}/conferencia-receita")
    public String registrarConferencia(@PathVariable Long id, @RequestParam Long versao,
            @RequestParam(defaultValue = "false") boolean confirmado, Authentication authentication,
            RedirectAttributes redirect) {
        if (!confirmado) {
            redirect.addFlashAttribute("erro", "Confirme que a receita física foi conferida presencialmente.");
            return REDIRECT_DETALHE;
        }
        try {
            service.registrarConferencia(id, versao, administradorId(authentication));
            redirect.addFlashAttribute("sucesso", "Conferência presencial registrada com responsável e horário.");
        } catch (ConflitoPedidoException | AcaoPedidoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return REDIRECT_DETALHE;
    }

    /** Status desconhecido vira "todos" (antes respondia 400). */
    private static StatusPedido statusDe(String texto) {
        return Arrays.stream(StatusPedido.values()).filter(s -> s.name().equals(texto)).findFirst().orElse(null);
    }

    private static Long administradorId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof UsuarioAutenticado usuario
                ? usuario.getId() : null;
    }
}
