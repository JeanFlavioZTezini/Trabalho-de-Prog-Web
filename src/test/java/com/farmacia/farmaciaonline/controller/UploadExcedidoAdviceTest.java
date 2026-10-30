package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class UploadExcedidoAdviceTest {

    private final UploadExcedidoAdvice advice = new UploadExcedidoAdvice();
    private final MaxUploadSizeExceededException erro = new MaxUploadSizeExceededException(10);

    @Test
    void edicaoVoltaParaOFormularioDoProdutoComMensagem() {
        var ra = new RedirectAttributesModelMap();
        String destino = advice.excedeu(erro, new MockHttpServletRequest("POST", "/admin/produtos/7"), ra);
        assertThat(destino).isEqualTo("redirect:/admin/produtos/7/editar");
        assertThat(ra.getFlashAttributes()).containsKey("erros");
    }

    @Test
    void cadastroVoltaParaOFormularioNovo() {
        var ra = new RedirectAttributesModelMap();
        String destino = advice.excedeu(erro, new MockHttpServletRequest("POST", "/admin/produtos"), ra);
        assertThat(destino).isEqualTo("redirect:/admin/produtos/novo");
    }

    @Test
    void outrasRotasNaoSaoTratadas() {
        assertThatThrownBy(() -> advice.excedeu(erro, new MockHttpServletRequest("POST", "/outra"),
                new RedirectAttributesModelMap())).isSameAs(erro);
    }
}
