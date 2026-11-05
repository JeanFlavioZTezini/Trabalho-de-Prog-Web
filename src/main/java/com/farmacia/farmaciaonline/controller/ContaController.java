package com.farmacia.farmaciaonline.controller;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import com.farmacia.farmaciaonline.model.ContaForm;
import com.farmacia.farmaciaonline.model.SenhaForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.security.RevogacaoDeSessoes;
import com.farmacia.farmaciaonline.security.SessaoAutenticada;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.ContaInvalidaException;
import com.farmacia.farmaciaonline.service.ContaService;
import com.farmacia.farmaciaonline.service.PeriodoFiltro;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/conta")
@RequiredArgsConstructor
public class ContaController {

    private static final String VER_CONTA = "redirect:/conta";

    private final ContaService service;
    private final SessaoAutenticada sessao;
    private final RevogacaoDeSessoes revogacao;

    @InitBinder("contaForm")
    public void camposDaConta(WebDataBinder binder) {
        binder.setAllowedFields("nome", "dataNascimento", "genero");
    }

    @InitBinder("senhaForm")
    public void camposDaSenha(WebDataBinder binder) {
        binder.setAllowedFields("senhaAtual", "novaSenha", "confirmarNovaSenha");
    }

    @GetMapping
    public String ver(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("dados", service.dados(idDe(usuario)));
        return "conta/ver";
    }

    @GetMapping("/editar")
    public String editar(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        Long id = idDe(usuario);
        return telaDeDados(model, service.formDe(id), id);
    }

    @PostMapping("/editar")
    public String salvarDados(@AuthenticationPrincipal UsuarioAutenticado usuario, @Valid ContaForm contaForm,
            BindingResult result, HttpServletRequest request, HttpServletResponse response, Model model,
            RedirectAttributes redirect) {
        Long id = idDe(usuario);
        if (!result.hasErrors()) {
            try {
                Usuario atualizado = service.atualizarDados(id, contaForm);
                sessao.atualizar(atualizado, request, response, false);
                redirect.addFlashAttribute("sucesso", "Dados atualizados com sucesso.");
                return VER_CONTA;
            } catch (ContaInvalidaException invalida) {
                invalida.getErros().forEach((campo, mensagem) -> result.rejectValue(campo, "conta." + campo, mensagem));
            }
        }
        model.addAttribute("temErros", true);
        return telaDeDados(model, contaForm, id);
    }

    @GetMapping("/senha")
    public String senha(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        idDe(usuario);
        model.addAttribute("senhaForm", new SenhaForm());
        return "conta/senha";
    }

    @PostMapping("/senha")
    public String trocarSenha(@AuthenticationPrincipal UsuarioAutenticado usuario, @Valid SenhaForm senhaForm,
            BindingResult result, HttpServletRequest request, HttpServletResponse response, Model model,
            RedirectAttributes redirect) {
        Long id = idDe(usuario);
        if (!result.hasErrors()) {
            try {
                Usuario atualizado = service.alterarSenha(id, senhaForm);
                // Contrato da F-06: a senha mudou, então as outras sessões da conta caem; esta continua (id renovado).
                revogacao.encerrarOutrasSessoesDe(id, request.getSession().getId());
                sessao.atualizar(atualizado, request, response, true);
                redirect.addFlashAttribute("sucesso", "Senha alterada com sucesso.");
                return VER_CONTA;
            } catch (ContaInvalidaException invalida) {
                invalida.getErros().forEach((campo, mensagem) -> result.rejectValue(campo, "conta." + campo, mensagem));
            }
        }
        model.addAttribute("temErros", true);
        return "conta/senha";
    }

    private String telaDeDados(Model model, ContaForm form, Long usuarioId) {
        model.addAttribute("contaForm", form);
        model.addAttribute("dados", service.dados(usuarioId));
        model.addAttribute("generos", Genero.values());
        model.addAttribute("hoje", LocalDate.now(PeriodoFiltro.FUSO_SAO_PAULO));
        return "conta/editar";
    }

    private static Long idDe(UsuarioAutenticado usuario) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return usuario.getId();
    }
}
