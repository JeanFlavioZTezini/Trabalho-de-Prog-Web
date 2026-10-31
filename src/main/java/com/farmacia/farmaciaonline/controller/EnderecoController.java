package com.farmacia.farmaciaonline.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.farmacia.farmaciaonline.cep.ConsultaCep;
import com.farmacia.farmaciaonline.cep.EnderecoCep;
import com.farmacia.farmaciaonline.model.EnderecoForm;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.EnderecoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/enderecos")
@RequiredArgsConstructor
public class EnderecoController {

    public static final String RETORNO_CHECKOUT = "checkout";
    public static final String URL_CHECKOUT = "/checkout";

    private static final String LISTA = "redirect:/enderecos";
    private static final String LISTA_NO_CHECKOUT = "redirect:/enderecos?retorno=" + RETORNO_CHECKOUT;

    private final EnderecoService service;
    private final ConsultaCep consultaCep;

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) String retorno, Model model) {
        model.addAttribute("enderecos", service.listar(idDe(usuario)));
        model.addAttribute("checkout", noCheckout(retorno));
        return "enderecos/lista";
    }

    @GetMapping("/novo")
    public String novo(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) String retorno, Model model) {
        idDe(usuario);
        return formulario(model, null, new EnderecoForm(), retorno);
    }

    @GetMapping("/{id}/editar")
    public String editar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            @RequestParam(required = false) String retorno, Model model) {
        return formulario(model, id, service.formDe(idDe(usuario), id), retorno);
    }

    @PostMapping
    public String criar(@AuthenticationPrincipal UsuarioAutenticado usuario, @Valid EnderecoForm form,
            BindingResult result, @RequestParam(required = false) String retorno, Model model,
            RedirectAttributes ra) {
        return gravar(idDe(usuario), null, form, result, retorno, model, ra);
    }

    @PostMapping("/{id}")
    public String atualizar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            @Valid EnderecoForm form, BindingResult result, @RequestParam(required = false) String retorno,
            Model model, RedirectAttributes ra) {
        return gravar(idDe(usuario), id, form, result, retorno, model, ra);
    }

    @PostMapping("/cep")
    public String buscarCepNovo(@AuthenticationPrincipal UsuarioAutenticado usuario, EnderecoForm form,
            @RequestParam(required = false) String retorno, Model model) {
        idDe(usuario);
        return buscarCep(null, form, retorno, model);
    }

    @PostMapping("/{id}/cep")
    public String buscarCepEdicao(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            EnderecoForm form, @RequestParam(required = false) String retorno, Model model) {
        service.buscar(idDe(usuario), id);
        return buscarCep(id, form, retorno, model);
    }

    @GetMapping("/{id}/excluir")
    public String confirmarExclusao(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            @RequestParam(required = false) String retorno, Model model) {
        model.addAttribute("endereco", service.buscar(idDe(usuario), id));
        model.addAttribute("checkout", noCheckout(retorno));
        return "enderecos/exclusao";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            @RequestParam(required = false) String retorno, RedirectAttributes ra) {
        service.excluir(idDe(usuario), id);
        ra.addFlashAttribute("sucesso", "Endereço excluído. Pedidos já feitos não são alterados.");
        return noCheckout(retorno) ? LISTA_NO_CHECKOUT : LISTA;
    }

    @PostMapping("/{id}/selecionar")
    public String selecionar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            @RequestParam(required = false) String retorno) {
        service.buscar(idDe(usuario), id);
        return noCheckout(retorno) ? "redirect:" + URL_CHECKOUT + "?enderecoId=" + id : LISTA;
    }

    private String gravar(Long usuarioId, Long id, EnderecoForm form, BindingResult result, String retorno,
            Model model, RedirectAttributes ra) {
        if (id != null) {
            service.buscar(usuarioId, id);
        }
        if (result.hasErrors()) {
            return formulario(model, id, form, retorno);
        }
        Long salvoId = service.salvar(usuarioId, id, form);
        ra.addFlashAttribute("sucesso", "Endereço salvo.");
        return noCheckout(retorno) ? "redirect:" + URL_CHECKOUT + "?enderecoId=" + salvoId : LISTA;
    }

    private String buscarCep(Long id, EnderecoForm form, String retorno, Model model) {
        String digitos = form.getCep() == null ? "" : form.getCep().replaceAll("\\D", "");
        if (digitos.length() != 8) {
            avisarCep(model, "Informe um CEP com 8 números para buscar. Você também pode preencher o endereço à mão.", true);
            return formulario(model, id, form, retorno);
        }
        try {
            consultaCep.consultar(digitos).ifPresentOrElse(
                    achado -> {
                        preencher(form, achado);
                        avisarCep(model, "Endereço preenchido pelo CEP. Confira os dados e informe o número.", false);
                    },
                    () -> avisarCep(model, "CEP não localizado. Preencha o endereço à mão.", true));
        } catch (RuntimeException e) {
            avisarCep(model, "Não foi possível consultar o CEP agora. Preencha o endereço à mão.", true);
        }
        return formulario(model, id, form, retorno);
    }

    private static void preencher(EnderecoForm form, EnderecoCep achado) {
        form.setLogradouro(achado.logradouro());
        form.setBairro(achado.bairro());
        form.setCidade(achado.cidade());
        form.setUf(achado.uf());
    }

    private static void avisarCep(Model model, String mensagem, boolean erro) {
        model.addAttribute("avisoCep", mensagem);
        model.addAttribute("avisoCepErro", erro);
    }

    private String formulario(Model model, Long id, EnderecoForm form, String retorno) {
        model.addAttribute("enderecoForm", form);
        model.addAttribute("enderecoId", id);
        model.addAttribute("checkout", noCheckout(retorno));
        model.addAttribute("ufs", EnderecoForm.UFS);
        return "enderecos/form";
    }

    private static boolean noCheckout(String retorno) {
        return RETORNO_CHECKOUT.equals(retorno);
    }

    private static Long idDe(UsuarioAutenticado usuario) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return usuario.getId();
    }
}
