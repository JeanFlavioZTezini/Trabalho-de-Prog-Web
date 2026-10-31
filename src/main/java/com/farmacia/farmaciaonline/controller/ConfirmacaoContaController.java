package com.farmacia.farmaciaonline.controller;

import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoEnvio;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoValidacao;
import com.farmacia.farmaciaonline.service.ConfirmacaoContaService;

import jakarta.servlet.http.HttpSession;

/**
 * Confirmação da conta (UC04). A conta em confirmação vem da sessão ({@link CadastroController#SESSAO_CONTA}), que só o
 * cadastro e a retomada (e-mail ou CPF mais a senha) preenchem; nunca vem da URL.
 */
@Controller
@RequestMapping("/confirmar-conta")
public class ConfirmacaoContaController {

    private final ConfirmacaoContaService service;
    private final CodigoVerificacaoService codigos;
    private final Environment ambiente;

    public ConfirmacaoContaController(ConfirmacaoContaService service, CodigoVerificacaoService codigos,
            Environment ambiente) {
        this.service = service;
        this.codigos = codigos;
        this.ambiente = ambiente;
    }

    @GetMapping
    public String pagina(HttpSession sessao, Model model) {
        Long id = contaDaSessao(sessao);
        if (id == null) {
            return "redirect:/cadastro";
        }
        Optional<Usuario> conta = service.pendente(id);
        if (conta.isEmpty()) {
            sessao.removeAttribute(CadastroController.SESSAO_CONTA);
            return "redirect:/login";
        }
        ConfirmacaoContaService.Contatos contatos = service.contatos(conta.get());
        model.addAttribute("emailMascarado", contatos.emailMascarado());
        model.addAttribute("celularMascarado", contatos.celularMascarado());
        model.addAttribute("validadeMinutos", codigos.getValidade().toMinutes());
        model.addAttribute("tentativasMaximas", codigos.getTentativasMaximas());
        model.addAttribute("demo", ambiente.acceptsProfiles(Profiles.of("demo")));
        return "confirmar-conta";
    }

    @PostMapping("/enviar")
    public String enviar(@RequestParam CanalCodigo canal, HttpSession sessao, RedirectAttributes flash) {
        Long id = contaDaSessao(sessao);
        if (id == null) {
            return "redirect:/cadastro";
        }
        ResultadoEnvio resultado;
        try {
            resultado = service.enviarCodigo(id, canal);
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("erro", "Não foi possível enviar o código para esse contato.");
            return "redirect:/confirmar-conta";
        }
        switch (resultado.situacao()) {
            case ENVIADO -> flash.addFlashAttribute("sucesso", canal == CanalCodigo.EMAIL
                    ? "Enviamos um código para o seu e-mail." : "Enviamos um código por SMS para o seu celular.");
            case AGUARDE -> flash.addFlashAttribute("erro",
                    "Aguarde " + resultado.esperaSegundos() + " segundos para pedir outro código.");
            case BLOQUEADO -> flash.addFlashAttribute("erro", "Você pediu muitos códigos. Tente de novo em "
                    + (resultado.esperaSegundos() + 59) / 60 + " minutos.");
        }
        return "redirect:/confirmar-conta";
    }

    @PostMapping
    public String confirmar(@RequestParam(required = false) String codigo, HttpSession sessao,
            RedirectAttributes flash) {
        Long id = contaDaSessao(sessao);
        if (id == null) {
            return "redirect:/cadastro";
        }
        ResultadoValidacao resultado;
        try {
            resultado = service.confirmar(id, codigo);
        } catch (IllegalStateException e) {
            sessao.removeAttribute(CadastroController.SESSAO_CONTA);
            return "redirect:/login";
        }
        if (resultado.valido()) {
            // o retornoCadastro continua na sessão: quem usa é o login (F-18)
            sessao.removeAttribute(CadastroController.SESSAO_CONTA);
            return "redirect:/login?confirmada";
        }
        flash.addFlashAttribute("erro", mensagem(resultado));
        return "redirect:/confirmar-conta";
    }

    @GetMapping("/retomar")
    public String retomada() {
        return "confirmar-conta-retomar";
    }

    @PostMapping("/retomar")
    public String retomar(@RequestParam(required = false) String identificador,
            @RequestParam(required = false) String senha, HttpSession sessao, Model model) {
        Optional<Usuario> conta = service.retomar(identificador, senha);
        if (conta.isPresent() && conta.get().getStatus() == StatusUsuario.PENDENTE) {
            sessao.setAttribute(CadastroController.SESSAO_CONTA, conta.get().getId());
            return "redirect:/confirmar-conta";
        }
        if (conta.isPresent() && conta.get().getStatus() == StatusUsuario.ATIVA) {
            return "redirect:/login?confirmada";
        }
        model.addAttribute("erro", "Não encontramos uma conta pendente com esses dados. Confira o e-mail ou CPF e a senha.");
        return "confirmar-conta-retomar";
    }

    private static Long contaDaSessao(HttpSession sessao) {
        return (Long) sessao.getAttribute(CadastroController.SESSAO_CONTA);
    }

    private static String mensagem(ResultadoValidacao resultado) {
        return switch (resultado.situacao()) {
            case INCORRETO -> "Código incorreto. Você ainda tem " + resultado.tentativasRestantes()
                    + (resultado.tentativasRestantes() == 1 ? " tentativa." : " tentativas.");
            case BLOQUEADO -> "Este código foi bloqueado depois de muitas tentativas erradas. Peça um novo código.";
            case EXPIRADO -> "Este código expirou. Peça um novo código.";
            case JA_USADO -> "Este código já foi usado. Peça um novo código.";
            case INEXISTENTE, VALIDO -> "Peça um código antes de confirmar.";
        };
    }
}
