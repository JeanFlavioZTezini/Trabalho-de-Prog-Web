package com.farmacia.farmaciaonline.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
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

import com.farmacia.farmaciaonline.model.CompletarSocialForm;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.ProvedorSocial;
import com.farmacia.farmaciaonline.security.LoginProgramatico;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoEnvio;
import com.farmacia.farmaciaonline.service.LoginSocialService;
import com.farmacia.farmaciaonline.service.LoginSocialService.Conclusao;
import com.farmacia.farmaciaonline.service.LoginSocialService.Resolucao;
import com.farmacia.farmaciaonline.social.IdentidadeExterna;
import com.farmacia.farmaciaonline.social.ProvedorIdentidade;
import com.farmacia.farmaciaonline.social.ProvedorIndisponivelException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * Login social (F-20). Rotas públicas ({@code /login/social/**}), mas só funcionam com um {@link ProvedorIdentidade}
 * (hoje, o simulado do perfil demo); sem ele respondem 404. O {@code state} fica na sessão e confere o retorno (evita
 * CSRF no login); a identidade do provedor e a conta a comprovar também ficam na sessão, nunca na URL.
 */
@Controller
@RequestMapping("/login/social")
public class LoginSocialController {

    static final String SESSAO_STATE = "loginSocialState";
    static final String SESSAO_IDENTIDADE = "loginSocialIdentidade";
    static final String SESSAO_CONTA_VINCULO = "loginSocialContaVinculo";

    private final ObjectProvider<ProvedorIdentidade> provedores;
    private final LoginSocialService service;
    private final LoginProgramatico login;

    public LoginSocialController(ObjectProvider<ProvedorIdentidade> provedores, LoginSocialService service,
            LoginProgramatico login) {
        this.provedores = provedores;
        this.service = service;
        this.login = login;
    }

    @InitBinder("completarSocialForm")
    void camposPermitidos(WebDataBinder binder) {
        binder.setAllowedFields("nome", "cpf", "dataNascimento", "genero", "celular", "email", "aceiteTermos");
    }

    @GetMapping("/{provedor}")
    public String iniciar(@PathVariable String provedor, HttpSession sessao) {
        ProvedorSocial p = provedorDe(provedor);
        String state = UUID.randomUUID().toString();
        sessao.setAttribute(SESSAO_STATE, p.name() + ":" + state);
        return "redirect:" + provedor().urlDeConsentimento(p, state);
    }

    @GetMapping("/{provedor}/retorno")
    public String retorno(@PathVariable String provedor, @RequestParam(required = false) String state,
            @RequestParam(required = false) String code, @RequestParam(required = false) String erro,
            @AuthenticationPrincipal UsuarioAutenticado logado, HttpServletRequest request,
            HttpServletResponse response, RedirectAttributes flash) {
        ProvedorSocial p = provedorDe(provedor);
        ProvedorIdentidade provedorIdentidade = provedor();
        HttpSession sessao = request.getSession();
        Object esperado = sessao.getAttribute(SESSAO_STATE);
        sessao.removeAttribute(SESSAO_STATE);
        String voltar = logado != null ? "redirect:/conta" : "redirect:/login";
        if (state == null || !(p.name() + ":" + state).equals(esperado)) {
            flash.addFlashAttribute("erroSocial", "A tentativa de entrar expirou ou não é válida. Tente de novo.");
            return voltar;
        }
        if ("cancelado".equals(erro)) {
            flash.addFlashAttribute("avisoSocial", "Você cancelou a entrada com " + nomeDe(p) + ". Nada foi alterado.");
            return voltar;
        }
        IdentidadeExterna identidade;
        try {
            if ("indisponivel".equals(erro)) {
                throw new ProvedorIndisponivelException(erro);
            }
            identidade = provedorIdentidade.trocarCodigo(p, code);
        } catch (ProvedorIndisponivelException e) {
            flash.addFlashAttribute("erroSocial", "O " + nomeDe(p)
                    + " não respondeu agora. Tente de novo em instantes ou entre com e-mail ou CPF e senha.");
            return voltar;
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("erroSocial", "A tentativa de entrar expirou ou não é válida. Tente de novo.");
            return voltar;
        }

        Resolucao resolucao = service.resolver(identidade, logado == null ? null : logado.getId());
        return switch (resolucao.tipo()) {
            case ENTRAR -> "redirect:" + login.entrar(resolucao.usuario(), request, response);
            case CONFIRMAR -> confirmarConta(sessao, resolucao.usuario().getId());
            case COMPLETAR -> {
                sessao.setAttribute(SESSAO_IDENTIDADE, identidade);
                sessao.removeAttribute(SESSAO_CONTA_VINCULO);
                yield "redirect:/login/social/completar";
            }
            case VINCULADO -> {
                flash.addFlashAttribute("sucesso", resolucao.mensagem());
                yield "redirect:/conta";
            }
            case RECUSADO -> {
                flash.addFlashAttribute("erroSocial", resolucao.mensagem());
                yield voltar;
            }
        };
    }

    @GetMapping("/completar")
    public String completar(HttpSession sessao, Model model) {
        IdentidadeExterna identidade = identidadeDa(sessao);
        if (identidade == null) {
            return "redirect:/login";
        }
        CompletarSocialForm form = new CompletarSocialForm();
        form.setNome(identidade.nome());
        return telaCompletar(model, form, identidade);
    }

    @PostMapping("/completar")
    public String concluir(@Valid CompletarSocialForm completarSocialForm, BindingResult result, HttpSession sessao,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        IdentidadeExterna identidade = identidadeDa(sessao);
        if (identidade == null) {
            return "redirect:/login";
        }
        if (result.hasErrors()) {
            return telaCompletar(model, completarSocialForm, identidade);
        }
        Conclusao conclusao = service.completar(identidade, completarSocialForm);
        switch (conclusao.desfecho()) {
            case CONTA_ATIVA -> {
                sessao.removeAttribute(SESSAO_IDENTIDADE);
                return "redirect:" + login.entrar(conclusao.usuario(), request, response);
            }
            case CONTA_PENDENTE -> {
                sessao.removeAttribute(SESSAO_IDENTIDADE);
                return confirmarConta(sessao, conclusao.usuario().getId());
            }
            case CPF_EXISTENTE -> {
                sessao.setAttribute(SESSAO_CONTA_VINCULO, conclusao.contaId());
                return "redirect:/login/social/vincular";
            }
            default -> {
                conclusao.erros().forEach((campo, mensagem) -> {
                    if ("geral".equals(campo)) {
                        result.reject("social.geral", mensagem);
                    } else {
                        result.rejectValue(campo, "social." + campo, mensagem);
                    }
                });
                return telaCompletar(model, completarSocialForm, identidade);
            }
        }
    }

    @GetMapping("/vincular")
    public String vincular(HttpSession sessao, Model model) {
        IdentidadeExterna identidade = identidadeDa(sessao);
        Long contaId = (Long) sessao.getAttribute(SESSAO_CONTA_VINCULO);
        if (identidade == null || contaId == null) {
            return "redirect:/login";
        }
        model.addAttribute("nomeProvedor", nomeDe(identidade.provedor()));
        model.addAttribute("emailMascarado", service.emailMascaradoDa(contaId));
        return "social/vincular";
    }

    @PostMapping("/vincular/senha")
    public String vincularComSenha(@RequestParam(required = false) String senha, HttpSession sessao,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        IdentidadeExterna identidade = identidadeDa(sessao);
        Long contaId = (Long) sessao.getAttribute(SESSAO_CONTA_VINCULO);
        if (identidade == null || contaId == null) {
            return "redirect:/login";
        }
        return concluirVinculo(service.vincularComSenha(identidade, contaId, senha), sessao, request, response, flash);
    }

    @PostMapping("/vincular/codigo/enviar")
    public String enviarCodigo(HttpSession sessao, RedirectAttributes flash) {
        Long contaId = (Long) sessao.getAttribute(SESSAO_CONTA_VINCULO);
        if (identidadeDa(sessao) == null || contaId == null) {
            return "redirect:/login";
        }
        ResultadoEnvio envio = service.enviarCodigoVinculo(contaId);
        switch (envio.situacao()) {
            case ENVIADO -> flash.addFlashAttribute("sucesso", "Enviamos um código para o e-mail da conta.");
            case AGUARDE -> flash.addFlashAttribute("erro",
                    "Aguarde " + envio.esperaSegundos() + " segundos para pedir outro código.");
            case BLOQUEADO -> flash.addFlashAttribute("erro", "Você pediu muitos códigos. Tente de novo em "
                    + (envio.esperaSegundos() + 59) / 60 + " minutos.");
        }
        return "redirect:/login/social/vincular";
    }

    @PostMapping("/vincular/codigo")
    public String vincularComCodigo(@RequestParam(required = false) String codigo, HttpSession sessao,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        IdentidadeExterna identidade = identidadeDa(sessao);
        Long contaId = (Long) sessao.getAttribute(SESSAO_CONTA_VINCULO);
        if (identidade == null || contaId == null) {
            return "redirect:/login";
        }
        return concluirVinculo(service.vincularComCodigo(identidade, contaId, codigo), sessao, request, response,
                flash);
    }

    private String concluirVinculo(Resolucao resolucao, HttpSession sessao, HttpServletRequest request,
            HttpServletResponse response, RedirectAttributes flash) {
        switch (resolucao.tipo()) {
            case ENTRAR -> {
                limpar(sessao);
                return "redirect:" + login.entrar(resolucao.usuario(), request, response);
            }
            case CONFIRMAR -> {
                limpar(sessao);
                return confirmarConta(sessao, resolucao.usuario().getId());
            }
            default -> {
                flash.addFlashAttribute("erro", resolucao.mensagem());
                return "redirect:/login/social/vincular";
            }
        }
    }

    /** Conta pendente segue o fluxo da F-05: o id fica na sessão e a pessoa confirma o contato com um código. */
    private static String confirmarConta(HttpSession sessao, Long contaId) {
        sessao.setAttribute(CadastroController.SESSAO_CONTA, contaId);
        return "redirect:/confirmar-conta";
    }

    private static void limpar(HttpSession sessao) {
        sessao.removeAttribute(SESSAO_IDENTIDADE);
        sessao.removeAttribute(SESSAO_CONTA_VINCULO);
    }

    private String telaCompletar(Model model, CompletarSocialForm form, IdentidadeExterna identidade) {
        model.addAttribute("completarSocialForm", form);
        model.addAttribute("nomeProvedor", nomeDe(identidade.provedor()));
        model.addAttribute("emailDoProvedor", identidade.temEmail() ? identidade.email() : null);
        model.addAttribute("emailVerificado", identidade.temEmail() && identidade.emailVerificado());
        model.addAttribute("generos", Genero.values());
        model.addAttribute("hoje", LocalDate.now());
        return "social/completar";
    }

    private static IdentidadeExterna identidadeDa(HttpSession sessao) {
        return sessao.getAttribute(SESSAO_IDENTIDADE) instanceof IdentidadeExterna i ? i : null;
    }

    private ProvedorIdentidade provedor() {
        ProvedorIdentidade provedor = provedores.getIfAvailable();
        if (provedor == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return provedor;
    }

    /** "google" ou "facebook" na URL; qualquer outra coisa é 404. */
    public static ProvedorSocial provedorDe(String caminho) {
        return Arrays.stream(ProvedorSocial.values()).filter(p -> p.name().equalsIgnoreCase(caminho)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public static String nomeDe(ProvedorSocial provedor) {
        return provedor == ProvedorSocial.GOOGLE ? "Google (simulado)" : "Facebook (simulado)";
    }
}
