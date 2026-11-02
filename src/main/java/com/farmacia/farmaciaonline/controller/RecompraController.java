package com.farmacia.farmaciaonline.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.RecompraService;
import com.farmacia.farmaciaonline.service.RecompraService.Resultado;

import lombok.RequiredArgsConstructor;

/**
 * "Comprar novamente" (UC06-A3, F-25): põe no carrinho os itens de um pedido do próprio cliente e leva ao carrinho,
 * onde ele revisa antes de finalizar. Fora do {@code PedidosClienteController}, que continua só de leitura.
 */
@Controller
@RequiredArgsConstructor
public class RecompraController {

    private final RecompraService service;

    @PostMapping("/pedidos/{id}/comprar-novamente")
    public String comprarNovamente(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id,
            RedirectAttributes redirect) {
        if (usuario == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Resultado resultado = service.comprarNovamente(usuario.getId(), id);
        if (resultado.adicionados() == 0) {
            redirect.addFlashAttribute("erro", "Nenhum item do pedido nº " + id + " está disponível agora.");
        } else {
            redirect.addFlashAttribute("sucesso", resultado.adicionados() == resultado.total()
                    ? "Os itens do pedido nº " + id + " foram adicionados ao carrinho. Confira antes de finalizar."
                    : resultado.adicionados() + " de " + resultado.total() + " itens do pedido nº " + id
                            + " foram adicionados ao carrinho.");
        }
        if (!resultado.avisos().isEmpty()) {
            redirect.addFlashAttribute("avisos", resultado.avisos());
        }
        return "redirect:/carrinho";
    }
}
