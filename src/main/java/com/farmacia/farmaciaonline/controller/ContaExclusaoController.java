package com.farmacia.farmaciaonline.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.farmacia.farmaciaonline.model.ExclusaoContaForm;
import com.farmacia.farmaciaonline.security.RevogacaoDeSessoes;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.ContaInvalidaException;
import com.farmacia.farmaciaonline.service.ExclusaoContaRecusadaException;
import com.farmacia.farmaciaonline.service.ExclusaoContaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Excluir a própria conta (F-24). A conta é sempre a da sessão: não existe id na rota nem no formulário. */
@Controller
@RequestMapping("/conta/excluir")
@RequiredArgsConstructor
public class ContaExclusaoController {

    private final ExclusaoContaService service;
    private final RevogacaoDeSessoes revogacao;

    @InitBinder("exclusaoContaForm")
    public void campos(WebDataBinder binder) {
        binder.setAllowedFields("senhaAtual", "confirmacao");
    }

    @GetMapping
    public String tela(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("exclusaoContaForm", new ExclusaoContaForm());
        return tela(model, idDe(usuario));
    }

    @PostMapping
    public String excluir(@AuthenticationPrincipal UsuarioAutenticado usuario, @Valid ExclusaoContaForm exclusaoContaForm,
            BindingResult result, HttpServletRequest request, HttpServletResponse response, Model model,
            RedirectAttributes redirect) {
        Long id = idDe(usuario);
        if (!result.hasErrors()) {
            try {
                service.excluir(id, exclusaoContaForm, request.getRemoteAddr());
                // Depois do commit: sai desta sessão e derruba as dos outros navegadores.
                new SecurityContextLogoutHandler().logout(request, response,
                        SecurityContextHolder.getContext().getAuthentication());
                revogacao.encerrarSessoesDe(id);
                redirect.addFlashAttribute("contaExcluida", true);
                return "redirect:/login";
            } catch (ContaInvalidaException invalida) {
                invalida.getErros().forEach((campo, mensagem) -> result.rejectValue(campo, "conta." + campo, mensagem));
            } catch (ExclusaoContaRecusadaException recusada) {
                model.addAttribute("erroExclusao", recusada.getMessage());
            }
        }
        if (result.hasErrors()) {
            model.addAttribute("temErros", true);
        }
        return tela(model, id);
    }

    private String tela(Model model, Long usuarioId) {
        model.addAttribute("situacao", service.situacao(usuarioId));
        return "conta/excluir";
    }

    private static Long idDe(UsuarioAutenticado usuario) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return usuario.getId();
    }
}
