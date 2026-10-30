package com.farmacia.farmaciaonline.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.farmacia.farmaciaonline.model.ProdutoForm;
import com.farmacia.farmaciaonline.model.enums.Tarja;
import com.farmacia.farmaciaonline.model.enums.TipoProduto;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CategoriaService;
import com.farmacia.farmaciaonline.service.ProdutoException;
import com.farmacia.farmaciaonline.service.ProdutoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** UC12: gerenciar produtos. Protegido pela regra /admin/** (ADMIN) do SecurityConfig. */
@Controller
@RequestMapping("/admin/produtos")
@RequiredArgsConstructor
public class AdminProdutoController {

    private static final String LISTA = "redirect:/admin/produtos";
    private static final int TAMANHO_PAGINA = 20;

    private final ProdutoService service;
    private final CategoriaService categoriaService;

    @GetMapping
    public String listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("pagina", service.buscar(q, status,
                PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA, Sort.by("nome"))));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        return "admin/produtos";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        return formulario(model, null, new ProdutoForm());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        return formulario(model, id, service.formDe(id));
    }

    @PostMapping
    public String criar(@Valid ProdutoForm form, BindingResult result, Model model, RedirectAttributes ra,
            Authentication auth) {
        return gravar(null, form, result, model, ra, auth);
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id, @Valid ProdutoForm form, BindingResult result, Model model,
            RedirectAttributes ra, Authentication auth) {
        return gravar(id, form, result, model, ra, auth);
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra, Authentication auth) {
        try {
            boolean apagado = service.excluir(id, adminId(auth));
            ra.addFlashAttribute("sucesso", apagado ? "Produto excluído."
                    : "Produto com pedidos não pode ser apagado: foi inativado e o histórico foi preservado.");
        } catch (ProdutoException e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return LISTA;
    }

    private static Long adminId(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u ? u.getId() : null;
    }

    private String gravar(Long id, ProdutoForm form, BindingResult result, Model model, RedirectAttributes ra,
            Authentication auth) {
        if (!result.hasErrors()) {
            try {
                service.salvar(id, form, adminId(auth));
                ra.addFlashAttribute("sucesso", "Produto salvo.");
                return LISTA;
            } catch (ProdutoException e) {
                model.addAttribute("erros", e.getErros());
            }
        }
        return formulario(model, id, form);
    }

    private String formulario(Model model, Long id, ProdutoForm form) {
        service.prepararLinhas(form);
        model.addAttribute("produtoForm", form);
        model.addAttribute("produtoId", id);
        model.addAttribute("categorias", categoriaService.listarArvore());
        model.addAttribute("tarjas", Tarja.values());
        model.addAttribute("tipos", TipoProduto.values());
        model.addAttribute("imagens", id == null ? java.util.List.of() : service.imagensDe(id));
        return "admin/produto-form";
    }
}
