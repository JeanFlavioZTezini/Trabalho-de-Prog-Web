package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.repository.OrdenacaoCatalogo;
import com.farmacia.farmaciaonline.service.CatalogoService;

@WebMvcTest(CatalogoController.class)
@Import(SecurityConfig.class)
class CatalogoErroTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CatalogoService catalogoService;

    @MockBean
    private CategoriaRepository categoriaRepository;

    @BeforeEach
    void prepararMenuVazio() {
        when(categoriaRepository.findByCategoriaPaiIsNullAndAtivaTrueOrderByOrdemAsc())
                .thenReturn(List.<Categoria>of());
    }

    @Test
    void falhaDeBancoExibeMensagemETentativaDeRecarregar() throws Exception {
        when(catalogoService.buscar(any(), nullable(Long.class), nullable(BigDecimal.class),
                nullable(BigDecimal.class), anyBoolean(), any(OrdenacaoCatalogo.class), any(Pageable.class)))
                .thenThrow(new DataAccessResourceFailureException("database indisponível"));

        String html = mvc.perform(get("/produtos").param("q", "dipirona"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Não foi possível carregar os produtos agora");
        assertThat(html).contains("Tentar novamente");
        assertThat(html).contains("Voltar para a página inicial");
        assertThat(html).contains("q=dipirona");
    }
}
