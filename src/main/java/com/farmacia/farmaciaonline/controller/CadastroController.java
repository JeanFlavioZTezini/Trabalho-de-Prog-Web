package com.farmacia.farmaciaonline.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.farmacia.farmaciaonline.model.CadastroForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CadastroInvalidoException;
import com.farmacia.farmaciaonline.service.CadastroService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * Cadastro de cliente (UC03). Depois de gravar a conta PENDENTE, guarda na sessão (nunca na URL) qual conta está em
 * confirmação e manda para {@code /confirmar-conta} (F-05).
 */
@Controller
@RequestMapping("/cadastro")
public class CadastroController {

    /** Sessão: id da conta recém-criada que falta confirmar. A F-05 lê este valor. */
    public static final String SESSAO_CONTA = "contaEmConfirmacao";

    /** Sessão: de onde a pessoa veio ({@code carrinho} ou {@code checkout}), para voltar depois de entrar. */
    public static final String SESSAO_RETORNO = "retornoCadastro";

    private static final Map<String, String> DESTINOS = Map.of("carrinho", "/carrinho", "checkout", "/checkout");

    private final CadastroService service;

    public CadastroController(CadastroService service) {
        this.service = service;
    }

    /** Caminho interno para o valor de {@code ?retorno=}. Só duas palavras valem; qualquer outra coisa é ignorada. */
    public static String destinoDoRetorno(String retorno) {
        return retorno == null ? null : DESTINOS.get(retorno);
    }

    @GetMapping
    public String formulario(@AuthenticationPrincipal UsuarioAutenticado logado,
            @RequestParam(required = false) String retorno, Model model) {
        if (logado != null) {
            return "redirect:/";
        }
        return tela(model, new CadastroForm(), retorno, false);
    }

    @PostMapping
    public String cadastrar(@AuthenticationPrincipal UsuarioAutenticado logado, @Valid CadastroForm form,
            BindingResult result, @RequestParam(required = false) String retorno, HttpSession sessao, Model model) {
        if (logado != null) {
            return "redirect:/";
        }
        Map<String, String> regras = service.verificar(form);
        regras.forEach((campo, mensagem) -> result.rejectValue(campo, "cadastro." + campo, mensagem));
        if (result.hasErrors()) {
            return tela(model, form, retorno, regras.containsKey("cpf") || regras.containsKey("email"));
        }

        Usuario conta;
        try {
            conta = service.cadastrar(form);
        } catch (CadastroInvalidoException e) {
            e.getErros().forEach((campo, mensagem) -> result.rejectValue(campo, "cadastro." + campo, mensagem));
            return tela(model, form, retorno, e.isDuplicidade());
        }

        sessao.setAttribute(SESSAO_CONTA, conta.getId());
        if (destinoDoRetorno(retorno) != null) {
            sessao.setAttribute(SESSAO_RETORNO, retorno);
        } else {
            sessao.removeAttribute(SESSAO_RETORNO);
        }
        return "redirect:/confirmar-conta";
    }

    private String tela(Model model, CadastroForm form, String retorno, boolean duplicidade) {
        model.addAttribute("cadastroForm", form);
        model.addAttribute("generos", Genero.values());
        model.addAttribute("hoje", LocalDate.now());
        model.addAttribute("retorno", destinoDoRetorno(retorno) == null ? null : retorno);
        model.addAttribute("duplicidade", duplicidade);
        return "cadastro/form";
    }
}
