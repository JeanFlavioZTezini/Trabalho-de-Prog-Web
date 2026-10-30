package com.farmacia.farmaciaonline.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.farmacia.farmaciaonline.model.CategoriaForm;
import com.farmacia.farmaciaonline.service.CategoriaException;
import com.farmacia.farmaciaonline.service.CategoriaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** UC13: gerenciar categorias. Protegido pela regra /admin/** (ADMIN) do SecurityConfig. */
@Controller
@RequestMapping("/admin/categorias")
@RequiredArgsConstructor
public class AdminCategoriaController {

    private static final String LISTA = "redirect:/admin/categorias";

    private final CategoriaService service;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("linhas", service.listarArvore());
        return "admin/categorias";
    }

    @GetMapping("/nova")
    public String nova(Model model) {
        return formulario(model, null, new CategoriaForm());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        return formulario(model, id, service.formDe(id));
    }

    @PostMapping
    public String criar(@Valid CategoriaForm form, BindingResult result, Model model, RedirectAttributes ra) {
        return gravar(null, form, result, model, ra);
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id, @Valid CategoriaForm form, BindingResult result, Model model,
            RedirectAttributes ra) {
        return gravar(id, form, result, model, ra);
    }

    @GetMapping("/{id}/excluir")
    public String confirmarExclusao(@PathVariable Long id, Model model) {
        model.addAttribute("resumo", service.resumoExclusao(id));
        return "admin/categoria-exclusao";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        try {
            service.excluir(id);
            ra.addFlashAttribute("sucesso", "Categoria excluída.");
            return LISTA;
        } catch (CategoriaException e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return voltarParaExclusao(id);
        }
    }

    @PostMapping("/{id}/inativar")
    public String inativar(@PathVariable Long id, RedirectAttributes ra) {
        service.inativar(id);
        ra.addFlashAttribute("sucesso", "Categoria inativada (subcategorias incluídas).");
        return LISTA;
    }

    @PostMapping("/{id}/reatribuir")
    public String reatribuir(@PathVariable Long id, @RequestParam(required = false) Long destinoId,
            RedirectAttributes ra) {
        try {
            service.reatribuirEExcluir(id, destinoId);
            ra.addFlashAttribute("sucesso", "Produtos reatribuídos e categoria excluída.");
            return LISTA;
        } catch (CategoriaException e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return voltarParaExclusao(id);
        }
    }

    private String voltarParaExclusao(Long id) {
        return "redirect:/admin/categorias/" + id + "/excluir";
    }

    private String gravar(Long id, CategoriaForm form, BindingResult result, Model model, RedirectAttributes ra) {
        if (!result.hasErrors()) {
            try {
                service.salvar(id, form);
                ra.addFlashAttribute("sucesso", "Categoria salva.");
                return LISTA;
            } catch (CategoriaException e) {
                model.addAttribute("erro", e.getMessage());
            }
        }
        return formulario(model, id, form);
    }

    private String formulario(Model model, Long id, CategoriaForm form) {
        model.addAttribute("categoriaForm", form);
        model.addAttribute("categoriaId", id);
        model.addAttribute("opcoesPai", service.opcoesDePai(id));
        return "admin/categoria-form";
    }
}
