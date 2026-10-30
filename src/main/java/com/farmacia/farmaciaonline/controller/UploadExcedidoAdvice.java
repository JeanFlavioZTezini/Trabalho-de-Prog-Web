package com.farmacia.farmaciaonline.controller;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Upload acima do limite do contêiner (spring.servlet.multipart.*): a requisição falha antes de
 * chegar ao controller, então o formulário de produto é reaberto com uma mensagem, em vez da página de erro.
 */
@ControllerAdvice
public class UploadExcedidoAdvice {

    private static final Pattern EDICAO = Pattern.compile("^/admin/produtos/(\\d+)$");

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String excedeu(MaxUploadSizeExceededException e, HttpServletRequest request, RedirectAttributes ra) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        String destino;
        Matcher edicao = EDICAO.matcher(caminho);
        if (edicao.matches()) {
            destino = "redirect:/admin/produtos/" + edicao.group(1) + "/editar";
        } else if (caminho.equals("/admin/produtos")) {
            destino = "redirect:/admin/produtos/novo";
        } else {
            throw e;
        }
        ra.addFlashAttribute("erros",
                List.of("Envio grande demais: cada foto pode ter no máximo 2 MB. Nada foi salvo."));
        return destino;
    }
}
