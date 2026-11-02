package com.farmacia.farmaciaonline.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.ClienteAdminService;
import com.farmacia.farmaciaonline.service.PeriodoFiltro;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * UC11: consulta de clientes, somente leitura (só GET: não há edição administrativa de conta). Protegido pela regra
 * /admin/** (ADMIN) do SecurityConfig.
 */
@Controller
@RequestMapping("/admin/clientes")
@RequiredArgsConstructor
public class AdminClienteController {

    private static final int TAMANHO_PAGINA = 20;

    private final ClienteAdminService service;

    @GetMapping
    public String listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "0") int pagina, Authentication auth, HttpServletRequest request,
            Model model) {
        StatusUsuario filtro = statusDe(status);
        PeriodoFiltro periodo = new PeriodoFiltro(de, ate);
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA, Sort.by("nome"));
        String erroFiltro = periodo.erro();
        model.addAttribute("pagina", erroFiltro != null ? Page.empty(pageable)
                : service.listar(adminId(auth), request.getRemoteAddr(), q, filtro, periodo, pageable));
        model.addAttribute("q", q);
        model.addAttribute("status", filtro == null ? "" : filtro.name());
        model.addAttribute("de", de);
        model.addAttribute("ate", ate);
        model.addAttribute("erroFiltro", erroFiltro);
        model.addAttribute("statuses", StatusUsuario.values());
        return "admin/clientes";
    }

    /** F-23: mesmos filtros da lista; o arquivo sai como anexo e a exportação vai para a auditoria. */
    @GetMapping("/exportar.csv")
    public ResponseEntity<byte[]> exportar(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            Authentication auth, HttpServletRequest request) {
        PeriodoFiltro periodo = new PeriodoFiltro(de, ate);
        if (periodo.erro() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, periodo.erro());
        }
        String csv = service.exportarCsv(adminId(auth), request.getRemoteAddr(), q, statusDe(status), periodo);
        String arquivo = "clientes-" + LocalDate.now(PeriodoFiltro.FUSO_SAO_PAULO).format(DateTimeFormatter.BASIC_ISO_DATE)
                + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}")
    public String detalhar(@PathVariable Long id, Authentication auth, HttpServletRequest request, Model model) {
        model.addAttribute("cliente", service.detalhar(adminId(auth), request.getRemoteAddr(), id));
        return "admin/cliente-detalhe";
    }

    /** Status desconhecido vira "todos". */
    private static StatusUsuario statusDe(String texto) {
        return Arrays.stream(StatusUsuario.values()).filter(s -> s.name().equals(texto)).findFirst().orElse(null);
    }

    private static Long adminId(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u ? u.getId() : null;
    }
}
