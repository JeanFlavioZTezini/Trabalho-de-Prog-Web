package com.farmacia.farmaciaonline.controller;

import java.util.Arrays;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.ClienteAdminService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * UC11: consulta de clientes, somente leitura (só GET: não há edição administrativa de conta). Protegido pela regra
 * /admin/** (ADMIN) do SecurityConfig.
 */
@Controller
@RequestMapping("/admin/clientes")
@RequiredArgsConstructor
public class AdminClienteController {

    private static final int TAMANHO_PAGINA = 20;

    private final ClienteAdminService service;

    @GetMapping
    public String listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int pagina, Authentication auth, HttpServletRequest request,
            Model model) {
        StatusUsuario filtro = statusDe(status);
        model.addAttribute("pagina", service.listar(adminId(auth), request.getRemoteAddr(), q, filtro,
                PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA, Sort.by("nome"))));
        model.addAttribute("q", q);
        model.addAttribute("status", filtro == null ? "" : filtro.name());
        model.addAttribute("statuses", StatusUsuario.values());
        return "admin/clientes";
    }

    @GetMapping("/{id}")
    public String detalhar(@PathVariable Long id, Authentication auth, HttpServletRequest request, Model model) {
        model.addAttribute("cliente", service.detalhar(adminId(auth), request.getRemoteAddr(), id));
        return "admin/cliente-detalhe";
    }

    /** Status desconhecido vira "todos". */
    private static StatusUsuario statusDe(String texto) {
        return Arrays.stream(StatusUsuario.values()).filter(s -> s.name().equals(texto)).findFirst().orElse(null);
    }

    private static Long adminId(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u ? u.getId() : null;
    }
}
