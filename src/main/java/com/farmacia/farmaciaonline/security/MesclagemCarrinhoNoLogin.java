package com.farmacia.farmaciaonline.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.farmacia.farmaciaonline.service.CarrinhoService;
import com.farmacia.farmaciaonline.service.DonoCarrinho;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class MesclagemCarrinhoNoLogin {
    public static final String AVISOS_NA_SESSAO = "carrinhoAvisosDeLogin";
    public static final String AVISO_MANTIDO_NA_SESSAO = "carrinhoAvisoMantido";

    private final CarrinhoService carrinhoService;

    @EventListener
    public void aoAutenticar(InteractiveAuthenticationSuccessEvent evento) {
        if (!(evento.getAuthentication().getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return;
        }
        HttpServletRequest request = atributos.getRequest();
        HttpServletResponse response = atributos.getResponse();

        Optional<UUID> token = CarrinhoCookie.ler(request);
        if (token.isPresent()) {
            try {
                List<String> avisos = carrinhoService.mesclar(token.get(), usuario.getId());
                if (!avisos.isEmpty()) {
                    request.getSession().setAttribute(AVISOS_NA_SESSAO, new ArrayList<>(avisos));
                }
                if (response != null) {
                    CarrinhoCookie.apagar(request, response);
                }
            } catch (RuntimeException erro) {
                log.warn("Não foi possível mesclar o carrinho do visitante no login do usuário {}", usuario.getId(), erro);
            }
        }
        guardarAvisoDeCarrinhoMantido(request, usuario.getId());
    }

    /** Vale também sem cookie: quem entra e já tinha itens na conta também precisa saber que o carrinho está lá. */
    private void guardarAvisoDeCarrinhoMantido(HttpServletRequest request, Long usuarioId) {
        try {
            int unidades = carrinhoService.contarUnidades(DonoCarrinho.deUsuario(usuarioId));
            if (unidades > 0) {
                request.getSession().setAttribute(AVISO_MANTIDO_NA_SESSAO, unidades == 1
                        ? "Você tem 1 item no seu carrinho."
                        : "Você tem " + unidades + " itens no seu carrinho.");
            }
        } catch (RuntimeException erro) {
            log.warn("Não foi possível contar o carrinho do usuário {} no login", usuarioId, erro);
        }
    }
}
