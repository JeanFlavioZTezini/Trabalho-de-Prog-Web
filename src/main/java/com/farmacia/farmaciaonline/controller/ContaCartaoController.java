package com.farmacia.farmaciaonline.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CartaoSalvoService;

import lombok.RequiredArgsConstructor;

/** "Meus cartões" (F-25): lista e remove cartões salvos da própria conta; cartão de outra pessoa é 404. */
@Controller
@RequestMapping("/conta/cartoes")
@RequiredArgsConstructor
public class ContaCartaoController {

    private final CartaoSalvoService service;

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("cartoes", service.listar(idDe(usuario)));
        return "conta/cartoes";
    }

    @PostMapping("/{id}/remover")
    public String remover(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            RedirectAttributes ra) {
        service.remover(idDe(usuario), id);
        ra.addFlashAttribute("sucesso", "Cartão removido. Pagamentos já feitos não mudam.");
        return "redirect:/conta/cartoes";
    }

    private static Long idDe(UsuarioAutenticado usuario) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return usuario.getId();
    }
}
