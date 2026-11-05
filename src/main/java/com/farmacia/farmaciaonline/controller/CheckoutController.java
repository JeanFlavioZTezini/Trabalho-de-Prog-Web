package com.farmacia.farmaciaonline.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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

import com.farmacia.farmaciaonline.model.CartaoForm;
import com.farmacia.farmaciaonline.model.CheckoutForm;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CheckoutException;
import com.farmacia.farmaciaonline.service.CheckoutService;
import com.farmacia.farmaciaonline.service.EnderecoService;
import com.farmacia.farmaciaonline.service.PagamentoPedidoService;
import com.farmacia.farmaciaonline.service.CartaoSalvoService;
import com.farmacia.farmaciaonline.service.ResumoCheckout;
import com.farmacia.farmaciaonline.service.ResumoPedidoService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkout;
    private final EnderecoService enderecos;
    private final PagamentoPedidoService pagamentos;
    private final ResumoPedidoService resumos;
    private final CartaoSalvoService cartoesSalvos;
    private final Environment ambiente;

    @GetMapping
    public String escolha(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Long enderecoId, Model model) {
        Long usuarioId = idDe(usuario);
        if (enderecoId != null) {
            enderecos.buscar(usuarioId, enderecoId); 
        }
        model.addAttribute("tela", checkout.telaEscolha(usuarioId));
        model.addAttribute("enderecoId", enderecoId);
        model.addAttribute("metodos", List.of(MetodoPagamento.values()));
        model.addAttribute("demo", modoDemo());
        return "checkout/escolha";
    }

    @GetMapping("/resumo")
    public String resumo(@AuthenticationPrincipal UsuarioAutenticado usuario, CheckoutForm checkoutForm,
            BindingResult ignorado, Model model) {
        Optional<ResumoCheckout> resumo = checkout.montarResumo(idDe(usuario), checkoutForm);
        if (resumo.isEmpty()) {
            return "redirect:/checkout";
        }
        model.addAttribute("resumo", resumo.get());
        model.addAttribute("cartoesSalvos", cartoesSalvos.listar(idDe(usuario)));
        model.addAttribute("chave", UUID.randomUUID());
        model.addAttribute("demo", modoDemo());
        return "checkout/resumo";
    }

    @PostMapping("/precos")
    public String aceitarPrecos(@AuthenticationPrincipal UsuarioAutenticado usuario, CheckoutForm checkoutForm,
            BindingResult ignorado, RedirectAttributes ra) {
        checkout.aceitarPrecos(idDe(usuario));
        return voltarAoResumo(checkoutForm, ra);
    }

    @PostMapping("/confirmar")
    public String confirmar(@AuthenticationPrincipal UsuarioAutenticado usuario, CheckoutForm checkoutForm,
            BindingResult ignorado, CartaoForm cartaoForm, @RequestParam String conferencia, @RequestParam UUID chave,
            RedirectAttributes ra) {
        Long usuarioId = idDe(usuario);
        lembrarEscolhas(checkoutForm, cartaoForm, ra);
        if (checkoutForm.isPagoComCartao() && !cartaoForm.errosPorCampo().isEmpty()) {
            ra.addFlashAttribute("errosCartao", cartaoForm.errosPorCampo());
            return voltarAoResumo(checkoutForm, ra);
        }
        try {
            Long pedidoId = pagamentos.confirmarCompra(usuarioId, checkoutForm, conferencia, chave, cartaoForm);
            return "redirect:/checkout/pedidos/" + pedidoId;
        } catch (CheckoutException e) {
            ra.addFlashAttribute("erros", e.getErros());
            return voltarAoResumo(checkoutForm, ra);
        }
    }

    @GetMapping("/pedidos/{id}")
    public String pedido(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id, Model model) {
        model.addAttribute("tela", pagamentos.telaPedido(idDe(usuario), id));
        model.addAttribute("cartoesSalvos", cartoesSalvos.listar(idDe(usuario)));
        model.addAttribute("demo", modoDemo());
        return "checkout/pedido";
    }

    @PostMapping("/pedidos/{id}/pagamento")
    public String pagar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            CheckoutForm checkoutForm, BindingResult ignorado, CartaoForm cartaoForm, @RequestParam UUID chave,
            RedirectAttributes ra) {
        Long usuarioId = idDe(usuario);
        lembrarEscolhas(checkoutForm, cartaoForm, ra);
        if (checkoutForm.getMetodo() == null) {
            ra.addFlashAttribute("erros", List.of("Escolha a forma de pagamento."));
        } else if (checkoutForm.isParcelasForaDoLimite()) {
            ra.addFlashAttribute("erros", List.of(CheckoutForm.MENSAGEM_PARCELAS));
        } else if (checkoutForm.isPagoComCartao() && !cartaoForm.errosPorCampo().isEmpty()) {
            ra.addFlashAttribute("errosCartao", cartaoForm.errosPorCampo());
        } else {
            pagamentos.pagar(usuarioId, id, chave, checkoutForm.getMetodo(), checkoutForm.parcelasEfetivas(), cartaoForm);
        }
        return "redirect:/checkout/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/atualizar")
    public String atualizar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        pagamentos.atualizarSituacao(idDe(usuario), id);
        return "redirect:/checkout/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/resumo")
    public String reenviarResumo(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            RedirectAttributes ra) {
        if (resumos.reenviar(idDe(usuario), id)) {
            ra.addFlashAttribute("sucesso", "Resumo enviado para o seu e-mail.");
        } else {
            ra.addFlashAttribute("erros", List.of("Não foi possível enviar o resumo agora. Seu pedido continua registrado; tente de novo em instantes."));
        }
        return "redirect:/checkout/pedidos/" + id;
    }

    /**
     * O que a tela seguinte (resumo com erro ou pedido com pagamento recusado) mostra de novo, para a pessoa não
     * redigitar tudo. Do cartão, só o que {@link CartaoForm#digitado()} deixa passar.
     */
    private static void lembrarEscolhas(CheckoutForm form, CartaoForm cartao, RedirectAttributes ra) {
        if (form.getMetodo() != null) {
            ra.addFlashAttribute("metodoEscolhido", form.getMetodo().name());
            ra.addFlashAttribute("parcelasEscolhidas", form.parcelasEfetivas());
        }
        if (form.isPagoComCartao()) {
            ra.addFlashAttribute("cartaoDigitado", cartao.digitado());
        }
    }

    private static String voltarAoResumo(CheckoutForm form, RedirectAttributes ra) {
        if (form.getTipoEntrega() != null) {
            ra.addAttribute("tipoEntrega", form.getTipoEntrega());
        }
        if (form.getEnderecoId() != null) {
            ra.addAttribute("enderecoId", form.getEnderecoId());
        }
        if (form.getLojaId() != null) {
            ra.addAttribute("lojaId", form.getLojaId());
        }
        if (form.getMetodo() != null) {
            ra.addAttribute("metodo", form.getMetodo());
        }
        if (form.getParcelas() != null) {
            ra.addAttribute("parcelas", form.getParcelas());
        }
        return "redirect:/checkout/resumo";
    }

    private boolean modoDemo() {
        return ambiente.acceptsProfiles(Profiles.of("demo"));
    }

    private static Long idDe(UsuarioAutenticado usuario) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return usuario.getId();
    }
}
