package com.farmacia.farmaciaonline.controller;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.farmacia.farmaciaonline.model.ContatoForm;
import com.farmacia.farmaciaonline.security.RevogacaoDeSessoes;
import com.farmacia.farmaciaonline.security.SessaoAutenticada;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoEnvio;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoValidacao;
import com.farmacia.farmaciaonline.service.ContaInvalidaException;
import com.farmacia.farmaciaonline.service.ContaService;
import com.farmacia.farmaciaonline.service.ContatoService;
import com.farmacia.farmaciaonline.service.ContatoService.ResultadoConfirmacao;
import com.farmacia.farmaciaonline.service.ContatoService.Tipo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Troca de e-mail ou celular com confirmação do novo contato (UC07 / F-21). Só conta autenticada; o dono é sempre o
 * usuário da sessão (nenhuma rota recebe id de conta).
 */
@Controller
@RequestMapping("/conta/contato/{tipo}")
@RequiredArgsConstructor
public class ContaContatoController {

    private static final String TELA = "conta/contato";

    private final ContatoService service;
    private final ContaService conta;
    private final CodigoVerificacaoService codigos;
    private final SessaoAutenticada sessao;
    private final RevogacaoDeSessoes revogacao;
    private final Environment ambiente;

    @InitBinder("contatoForm")
    public void campos(WebDataBinder binder) {
        binder.setAllowedFields("novoContato", "senhaAtual");
    }

    @GetMapping
    public String pagina(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String tipo, Model model) {
        Tipo contato = tipoDe(tipo);
        if (!model.containsAttribute("contatoForm")) {
            model.addAttribute("contatoForm", new ContatoForm());
        }
        return tela(model, contato, idDe(usuario));
    }

    @PostMapping("/enviar")
    public String enviar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String tipo,
            @Valid ContatoForm contatoForm, BindingResult result, Model model, RedirectAttributes flash) {
        Tipo contato = tipoDe(tipo);
        Long id = idDe(usuario);
        if (!result.hasErrors()) {
            try {
                ResultadoEnvio envio = service.solicitar(id, contato, contatoForm.getNovoContato(),
                        contatoForm.getSenhaAtual());
                switch (envio.situacao()) {
                    case ENVIADO -> flash.addFlashAttribute("sucesso", contato == Tipo.EMAIL
                            ? "Enviamos um código para o novo e-mail. Digite-o abaixo para concluir a troca."
                            : "Enviamos um código por SMS para o novo celular. Digite-o abaixo para concluir a troca.");
                    case AGUARDE -> flash.addFlashAttribute("erro",
                            "Aguarde " + envio.esperaSegundos() + " segundos para pedir outro código.");
                    case BLOQUEADO -> flash.addFlashAttribute("erro", "Você pediu muitos códigos. Tente de novo em "
                            + (envio.esperaSegundos() + 59) / 60 + " minutos.");
                }
                return "redirect:/conta/contato/" + contato.getCaminho();
            } catch (ContaInvalidaException invalida) {
                invalida.getErros().forEach((campo, mensagem) -> result.rejectValue(campo, "conta." + campo, mensagem));
            }
        }
        model.addAttribute("temErros", true);
        return tela(model, contato, id);
    }

    @PostMapping("/confirmar")
    public String confirmar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable String tipo,
            @RequestParam(required = false) String codigo, HttpServletRequest request, HttpServletResponse response,
            RedirectAttributes flash) {
        Tipo contato = tipoDe(tipo);
        Long id = idDe(usuario);
        ResultadoConfirmacao resultado;
        try {
            resultado = service.confirmar(id, contato, codigo);
        } catch (DataIntegrityViolationException disputa) {
            flash.addFlashAttribute("erro", "Não foi possível usar este e-mail. Informe outro.");
            return "redirect:/conta/contato/" + contato.getCaminho();
        }

        if (resultado.confirmado()) {
            if (contato == Tipo.EMAIL) {
                // O e-mail é o identificador de login: as outras sessões caem e esta continua com o id renovado.
                revogacao.encerrarOutrasSessoesDe(id, request.getSession().getId());
                sessao.atualizar(resultado.conta(), request, response, true);
                flash.addFlashAttribute("sucesso", "E-mail alterado e confirmado. Use o novo e-mail para entrar.");
            } else {
                sessao.atualizar(resultado.conta(), request, response, false);
                flash.addFlashAttribute("sucesso", "Celular alterado e confirmado.");
            }
            return "redirect:/conta";
        }
        flash.addFlashAttribute("erro", resultado.emUso()
                ? "Não foi possível usar este e-mail. Informe outro." : mensagem(resultado.validacao()));
        return "redirect:/conta/contato/" + contato.getCaminho();
    }

    private String tela(Model model, Tipo tipo, Long usuarioId) {
        model.addAttribute("tipo", tipo.getCaminho());
        model.addAttribute("ehEmail", tipo == Tipo.EMAIL);
        model.addAttribute("dados", conta.dados(usuarioId));
        model.addAttribute("validadeMinutos", codigos.getValidade().toMinutes());
        model.addAttribute("tentativasMaximas", codigos.getTentativasMaximas());
        model.addAttribute("demo", ambiente.acceptsProfiles(Profiles.of("demo")));
        return TELA;
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

    private static Tipo tipoDe(String caminho) {
        Tipo tipo = Tipo.doCaminho(caminho);
        if (tipo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return tipo;
    }

    private static Long idDe(UsuarioAutenticado usuario) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return usuario.getId();
    }
}
