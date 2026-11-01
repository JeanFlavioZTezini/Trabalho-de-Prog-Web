package com.farmacia.farmaciaonline.controller;

import java.time.Instant;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.farmacia.farmaciaonline.model.NovaSenhaForm;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService;
import com.farmacia.farmaciaonline.service.RecuperacaoSenhaService;
import com.farmacia.farmaciaonline.service.RecuperacaoSenhaService.Solicitacao;
import com.farmacia.farmaciaonline.validacao.PoliticaDeSenha;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * Recuperação de senha sem login (UC14), em três passos: pedir o código, validar o código e definir a nova senha.
 * A conta fica na sessão, nunca na URL, e as mensagens são as mesmas exista a conta ou não.
 */
@Controller
@RequestMapping("/recuperar-senha")
public class RecuperacaoSenhaController {

    /** Sessão: o pedido de código foi feito (a conta pode ou não existir). */
    static final String SESSAO_SOLICITADA = "recuperacaoSolicitada";
    /** Sessão: id da conta elegível; ausente quando não há conta elegível. */
    static final String SESSAO_CONTA = "recuperacaoConta";
    /** Sessão: até quando o código validado permite definir a senha. */
    static final String SESSAO_VALIDADA_ATE = "recuperacaoValidadaAte";

    private static final String MENSAGEM_ENVIO = "Se houver uma conta com esses dados e com contato confirmado, "
            + "enviamos um código para ele. Confira o e-mail ou o SMS.";
    private static final String MENSAGEM_CODIGO = "Código incorreto ou expirado. Confira ou peça um novo código.";

    private final RecuperacaoSenhaService service;
    private final CodigoVerificacaoService codigos;

    public RecuperacaoSenhaController(RecuperacaoSenhaService service, CodigoVerificacaoService codigos) {
        this.service = service;
        this.codigos = codigos;
    }

    @GetMapping
    public String solicitacao() {
        return "recuperar-senha/solicitar";
    }

    @PostMapping
    public String solicitar(@RequestParam(required = false) String identificador,
            @RequestParam(required = false) CanalCodigo canal, HttpSession sessao, RedirectAttributes flash) {
        Solicitacao resultado = service.solicitar(identificador, canal);
        if (resultado.esperaSegundos() > 0) {
            flash.addFlashAttribute("erro", "Aguarde " + tempo(resultado.esperaSegundos()) + " para pedir outro código.");
            return "redirect:/recuperar-senha";
        }
        sessao.setAttribute(SESSAO_SOLICITADA, true);
        sessao.removeAttribute(SESSAO_VALIDADA_ATE);
        if (resultado.contaId() != null) {
            sessao.setAttribute(SESSAO_CONTA, resultado.contaId());
        } else {
            sessao.removeAttribute(SESSAO_CONTA);
        }
        flash.addFlashAttribute("sucesso", MENSAGEM_ENVIO);
        return "redirect:/recuperar-senha/codigo";
    }

    @GetMapping("/codigo")
    public String codigo(HttpSession sessao, Model model) {
        if (sessao.getAttribute(SESSAO_SOLICITADA) == null) {
            return "redirect:/recuperar-senha";
        }
        model.addAttribute("validadeMinutos", codigos.getValidade().toMinutes());
        return "recuperar-senha/codigo";
    }

    @PostMapping("/codigo")
    public String validar(@RequestParam(required = false) String codigo, HttpSession sessao, RedirectAttributes flash) {
        if (sessao.getAttribute(SESSAO_SOLICITADA) == null) {
            return "redirect:/recuperar-senha";
        }
        Long contaId = (Long) sessao.getAttribute(SESSAO_CONTA);
        if (!service.validarCodigo(contaId, codigo)) {
            flash.addFlashAttribute("erro", MENSAGEM_CODIGO);
            return "redirect:/recuperar-senha/codigo";
        }
        sessao.setAttribute(SESSAO_VALIDADA_ATE, Instant.now().plus(codigos.getValidade()));
        return "redirect:/recuperar-senha/nova-senha";
    }

    @GetMapping("/nova-senha")
    public String novaSenha(HttpSession sessao, Model model) {
        if (!codigoValidado(sessao)) {
            return "redirect:/recuperar-senha";
        }
        model.addAttribute("novaSenhaForm", new NovaSenhaForm());
        return "recuperar-senha/nova-senha";
    }

    @PostMapping("/nova-senha")
    public String redefinir(@Valid NovaSenhaForm form, BindingResult result, HttpSession sessao) {
        if (!codigoValidado(sessao)) {
            return "redirect:/recuperar-senha";
        }
        if (!result.hasFieldErrors("senha") && PoliticaDeSenha.excedeLimite(form.getSenha())) {
            result.rejectValue("senha", "senha.limite", PoliticaDeSenha.MENSAGEM_LIMITE);
        }
        if (!result.hasErrors() && !form.getSenha().equals(form.getConfirmarSenha())) {
            result.rejectValue("confirmarSenha", "senha.diferente", "As senhas não conferem.");
        }
        if (!result.hasErrors() && service.mesmaSenhaAtual((Long) sessao.getAttribute(SESSAO_CONTA), form.getSenha())) {
            result.rejectValue("senha", "senha.igualAtual", "A nova senha precisa ser diferente da atual.");
        }
        if (result.hasErrors()) {
            return "recuperar-senha/nova-senha";
        }
        try {
            service.redefinir((Long) sessao.getAttribute(SESSAO_CONTA), form.getSenha());
        } catch (IllegalStateException e) {
            limpar(sessao);
            return "redirect:/recuperar-senha";
        }
        limpar(sessao);
        return "redirect:/login?senhaAlterada";
    }

    private static boolean codigoValidado(HttpSession sessao) {
        Instant ate = (Instant) sessao.getAttribute(SESSAO_VALIDADA_ATE);
        return ate != null && Instant.now().isBefore(ate) && sessao.getAttribute(SESSAO_CONTA) != null;
    }

    private static void limpar(HttpSession sessao) {
        sessao.removeAttribute(SESSAO_SOLICITADA);
        sessao.removeAttribute(SESSAO_CONTA);
        sessao.removeAttribute(SESSAO_VALIDADA_ATE);
    }

    private static String tempo(long segundos) {
        return segundos < 120 ? segundos + " segundos" : (segundos + 59) / 60 + " minutos";
    }
}
