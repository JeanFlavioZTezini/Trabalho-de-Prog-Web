package com.farmacia.farmaciaonline.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import com.farmacia.farmaciaonline.security.CarrinhoCookie;
import com.farmacia.farmaciaonline.security.MesclagemCarrinhoNoLogin;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CarrinhoException;
import com.farmacia.farmaciaonline.service.CarrinhoService;
import com.farmacia.farmaciaonline.service.DonoCarrinho;
import com.farmacia.farmaciaonline.service.ResumoCarrinho;
import com.farmacia.farmaciaonline.service.ResumoCarrinho.LinhaCarrinho;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/carrinho")
@RequiredArgsConstructor
public class CarrinhoController {
    static final String DESTINO_CHECKOUT = "/checkout";

    private static final String VOLTAR_AO_CARRINHO = "redirect:/carrinho";
    static final String MENSAGEM_ADMIN = "Contas de administrador não fazem compras.";

    private final CarrinhoService carrinhoService;

    @GetMapping
    public String ver(HttpServletRequest request, Authentication autenticacao, Model model) {
        DonoCarrinho dono = donoParaLeitura(request, autenticacao);
        model.addAttribute("resumo", carrinhoService.resumo(dono));
        List<String> removidos = carrinhoService.consumirAvisosDeRemocao(dono);
        if (!removidos.isEmpty()) {
            model.addAttribute("removidos", removidos);
        }

        List<String> avisosDoLogin = consumirAvisosDoLogin(request);
        if (!avisosDoLogin.isEmpty()) {
            model.addAttribute("avisosDoLogin", avisosDoLogin);
        }
        return "carrinho";
    }

    @GetMapping("/itens/{itemId}/remover")
    public String confirmarRemocao(@PathVariable Long itemId, HttpServletRequest request,
            Authentication autenticacao, Model model, RedirectAttributes redirect) {
        ResumoCarrinho resumo = carrinhoService.resumo(donoParaLeitura(request, autenticacao));
        LinhaCarrinho linha = resumo.getLinhas().stream()
                .filter(l -> l.getItemId().equals(itemId))
                .findFirst()
                .orElse(null);
        if (linha == null) {
            redirect.addFlashAttribute("erro", "Este item não está no seu carrinho.");
            return VOLTAR_AO_CARRINHO;
        }
        model.addAttribute("linha", linha);
        return "carrinho-remover";
    }

    @PostMapping("/itens")
    public String adicionar(@RequestParam Long apresentacaoId,
            @RequestParam(defaultValue = "1") int quantidade,
            HttpServletRequest request, HttpServletResponse response,
            Authentication autenticacao, RedirectAttributes redirect) {
        DonoCarrinho dono = donoParaEscrita(request, response, autenticacao);
        return executar(redirect, "Item adicionado ao carrinho.",
                () -> carrinhoService.adicionar(dono, apresentacaoId, quantidade));
    }

    /**
     * Mesma operação do formulário, para o mini-painel da home (carrinho-rapido.js). Só responde JSON a quem pede
     * JSON; sem JavaScript, o formulário continua caindo no método acima.
     */
    @PostMapping(value = "/itens", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> adicionarRapido(@RequestParam Long apresentacaoId,
            @RequestParam(defaultValue = "1") int quantidade,
            HttpServletRequest request, HttpServletResponse response, Authentication autenticacao) {
        if (ehAdministrador(autenticacao)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new RespostaErro(MENSAGEM_ADMIN));
        }
        DonoCarrinho dono = donoParaEscrita(request, response, autenticacao);
        try {
            List<String> avisos = carrinhoService.adicionar(dono, apresentacaoId, quantidade);
            ResumoCarrinho resumo = carrinhoService.resumo(dono);
            ItemAdicionado item = resumo.getLinhas().stream()
                    .filter(l -> apresentacaoId.equals(l.getApresentacaoId()))
                    .findFirst()
                    .map(l -> new ItemAdicionado(l.getNome(), l.getDescricao(), l.getImagem(), l.getQuantidade(),
                            l.getPrecoUnitario(), l.getSubtotal()))
                    .orElse(null);
            return ResponseEntity.ok(new RespostaAdicao("Item adicionado ao carrinho.", avisos, item,
                    carrinhoService.contarUnidades(dono), resumo.getTotal()));
        } catch (CarrinhoException recusada) {
            return ResponseEntity.unprocessableEntity().body(new RespostaErro(recusada.getMessage()));
        } catch (DataIntegrityViolationException concorrencia) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new RespostaErro("Não foi possível atualizar o carrinho agora. Tente novamente."));
        }
    }

    @PostMapping("/itens/{itemId}/quantidade")
    public String alterarQuantidade(@PathVariable Long itemId, @RequestParam int quantidade,
            HttpServletRequest request, HttpServletResponse response,
            Authentication autenticacao, RedirectAttributes redirect) {
        if (quantidade < 1) {
            return "redirect:/carrinho/itens/" + itemId + "/remover";
        }
        DonoCarrinho dono = donoParaEscrita(request, response, autenticacao);
        return executar(redirect, "Quantidade atualizada.",
                () -> carrinhoService.alterarQuantidade(dono, itemId, quantidade));
    }

    @PostMapping("/itens/{itemId}/remover")
    public String remover(@PathVariable Long itemId, HttpServletRequest request, HttpServletResponse response,
            Authentication autenticacao, RedirectAttributes redirect) {
        DonoCarrinho dono = donoParaEscrita(request, response, autenticacao);
        return executar(redirect, "Item removido do carrinho.", () -> {
            carrinhoService.remover(dono, itemId);
            return List.of();
        });
    }

    @PostMapping("/itens/{itemId}/aceitar-preco")
    public String aceitarPreco(@PathVariable Long itemId, HttpServletRequest request, HttpServletResponse response,
            Authentication autenticacao, RedirectAttributes redirect) {
        DonoCarrinho dono = donoParaEscrita(request, response, autenticacao);
        return executar(redirect, "Novo preço aceito.", () -> {
            carrinhoService.aceitarPreco(dono, itemId);
            return List.of();
        });
    }

    @GetMapping("/esvaziar")
    public String confirmarEsvaziar(HttpServletRequest request, Authentication autenticacao, Model model) {
        ResumoCarrinho resumo = carrinhoService.resumo(donoParaLeitura(request, autenticacao));
        if (resumo.isVazio()) {
            return VOLTAR_AO_CARRINHO;
        }
        model.addAttribute("resumo", resumo);
        return "carrinho-esvaziar";
    }

    @PostMapping("/esvaziar")
    public String esvaziar(HttpServletRequest request, HttpServletResponse response,
            Authentication autenticacao, RedirectAttributes redirect) {
        DonoCarrinho dono = donoParaEscrita(request, response, autenticacao);
        return executar(redirect, "Carrinho esvaziado.", () -> {
            carrinhoService.esvaziar(dono);
            return List.of();
        });
    }

    @GetMapping("/finalizar")
    public String finalizar(HttpServletRequest request, HttpServletResponse response,
            Authentication autenticacao, RedirectAttributes redirect) {
        if (ehAdministrador(autenticacao)) {
            redirect.addFlashAttribute("erro", MENSAGEM_ADMIN);
            return VOLTAR_AO_CARRINHO;
        }
        Long usuarioId = usuarioId(autenticacao);
        if (usuarioId == null) {
            new HttpSessionRequestCache().saveRequest(request, response);
            return "redirect:/login";
        }

        ResumoCarrinho resumo = carrinhoService.resumo(DonoCarrinho.deUsuario(usuarioId));
        if (resumo.isVazio()) {
            redirect.addFlashAttribute("erro", "Seu carrinho está vazio.");
            return VOLTAR_AO_CARRINHO;
        }
        if (!resumo.isPodeFinalizar()) {
            redirect.addFlashAttribute("erro",
                    "Há itens indisponíveis ou acima do permitido. Ajuste ou remova esses itens para finalizar a compra.");
            return VOLTAR_AO_CARRINHO;
        }
        return "redirect:" + DESTINO_CHECKOUT;
    }

    private String executar(RedirectAttributes redirect, String mensagemDeSucesso, Supplier<List<String>> operacao) {
        try {
            List<String> avisos = operacao.get();
            redirect.addFlashAttribute("sucesso", mensagemDeSucesso);
            if (!avisos.isEmpty()) {
                redirect.addFlashAttribute("avisos", avisos);
            }
        } catch (CarrinhoException recusada) {
            redirect.addFlashAttribute("erro", recusada.getMessage());
        } catch (DataIntegrityViolationException concorrencia) {
            redirect.addFlashAttribute("erro", "Não foi possível atualizar o carrinho agora. Tente novamente.");
        }
        return VOLTAR_AO_CARRINHO;
    }

    public record ItemAdicionado(String nome, String descricao, String imagem, int quantidade,
            BigDecimal precoUnitario, BigDecimal subtotal) {
    }

    public record RespostaAdicao(String mensagem, List<String> avisos, ItemAdicionado item,
            int totalUnidades, BigDecimal total) {
    }

    public record RespostaErro(String erro) {
    }

    private static Long usuarioId(Authentication autenticacao) {
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario.getId();
        }
        return null;
    }

    static DonoCarrinho donoParaLeitura(HttpServletRequest request, Authentication autenticacao) {
        Long usuarioId = usuarioId(autenticacao);
        if (usuarioId != null) {
            return DonoCarrinho.deUsuario(usuarioId);
        }
        return CarrinhoCookie.ler(request).map(DonoCarrinho::deVisitante).orElse(null);
    }

    /**
     * O checkout é só de cliente: o administrador não monta carrinho. Recusa toda escrita (e o "Finalizar") com uma
     * mensagem no carrinho, em vez de deixar ele descobrir só no 403 do checkout.
     */
    @ExceptionHandler(CompraDeAdministradorException.class)
    public String recusarAdministrador(HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("erro", MENSAGEM_ADMIN);
        return VOLTAR_AO_CARRINHO;
    }

    static boolean ehAdministrador(Authentication autenticacao) {
        return autenticacao != null && autenticacao.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static DonoCarrinho donoParaEscrita(HttpServletRequest request, HttpServletResponse response,
            Authentication autenticacao) {
        if (ehAdministrador(autenticacao)) {
            throw new CompraDeAdministradorException();
        }
        Long usuarioId = usuarioId(autenticacao);
        if (usuarioId != null) {
            return DonoCarrinho.deUsuario(usuarioId);
        }
        UUID token = CarrinhoCookie.ler(request).orElseGet(UUID::randomUUID);
        CarrinhoCookie.gravar(request, response, token);
        return DonoCarrinho.deVisitante(token);
    }

    private static List<String> consumirAvisosDoLogin(HttpServletRequest request) {
        HttpSession sessao = request.getSession(false);
        if (sessao == null) {
            return List.of();
        }
        Object guardado = sessao.getAttribute(MesclagemCarrinhoNoLogin.AVISOS_NA_SESSAO);
        sessao.removeAttribute(MesclagemCarrinhoNoLogin.AVISOS_NA_SESSAO);
        if (guardado instanceof List<?> avisos) {
            return avisos.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /** Escrita no carrinho pedida por administrador; vira a mensagem do {@link #recusarAdministrador}. */
    static class CompraDeAdministradorException extends RuntimeException {
    }
}
