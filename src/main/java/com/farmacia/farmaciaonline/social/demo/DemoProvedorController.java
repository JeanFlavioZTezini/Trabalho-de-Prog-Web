package com.farmacia.farmaciaonline.social.demo;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import com.farmacia.farmaciaonline.model.enums.ProvedorSocial;
import com.farmacia.farmaciaonline.controller.LoginSocialController;
import com.farmacia.farmaciaonline.social.IdentidadeExterna;

/**
 * Tela do provedor simulado (só no perfil demo): faz o papel da página do Google/Facebook. O resultado volta para
 * {@code /login/social/{provedor}/retorno} com o mesmo {@code state}, como um OAuth faria.
 */
@Controller
@Profile("demo")
@RequestMapping("/login/social/demo/{provedor}/consentimento")
public class DemoProvedorController {

    private final DemoProvedorIdentidade provedor;

    public DemoProvedorController(DemoProvedorIdentidade provedor) {
        this.provedor = provedor;
    }

    @GetMapping
    public String tela(@PathVariable String provedor, @RequestParam String state, Model model) {
        ProvedorSocial p = LoginSocialController.provedorDe(provedor);
        model.addAttribute("provedor", p);
        model.addAttribute("nomeProvedor", LoginSocialController.nomeDe(p));
        model.addAttribute("caminho", provedor);
        model.addAttribute("state", state);
        model.addAttribute("idSugerido", "demo-" + UUID.randomUUID().toString().substring(0, 8));
        return "social/consentimento";
    }

    @PostMapping
    public String responder(@PathVariable String provedor, @RequestParam String state, @RequestParam String acao,
            @RequestParam(required = false) String idExterno, @RequestParam(required = false) String nome,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "false") boolean emailVerificado) {
        ProvedorSocial p = LoginSocialController.provedorDe(provedor);
        UriComponentsBuilder retorno = UriComponentsBuilder.fromPath("/login/social/{provedor}/retorno")
                .queryParam("state", state);
        switch (acao) {
            case "permitir" -> {
                String id = idExterno == null || idExterno.isBlank() ? "demo-" + UUID.randomUUID() : idExterno.trim();
                boolean temEmail = email != null && !email.isBlank();
                IdentidadeExterna identidade = new IdentidadeExterna(p, limitar(id, 100), limitar(nome, 150),
                        temEmail ? limitar(email.trim().toLowerCase(), 254) : null, temEmail && emailVerificado);
                retorno.queryParam("code", this.provedor.emitirCodigo(identidade));
            }
            case "indisponivel" -> retorno.queryParam("erro", "indisponivel");
            default -> retorno.queryParam("erro", "cancelado");
        }
        return "redirect:" + retorno.buildAndExpand(provedor).toUriString();
    }

    private static String limitar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo);
    }
}
