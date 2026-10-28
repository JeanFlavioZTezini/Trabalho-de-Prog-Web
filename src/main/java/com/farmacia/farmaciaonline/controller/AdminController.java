package com.farmacia.farmaciaonline.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Painel do administrador. Por enquanto é uma página provisória, só para provar a proteção:
 * a regra de que /admin/** é exclusiva do papel ADMIN está no SecurityConfig.
 */
@Controller
public class AdminController {

    @GetMapping("/admin")
    public String painel() {
        return "admin/index";
    }
}
