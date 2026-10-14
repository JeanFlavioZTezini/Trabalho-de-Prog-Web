package com.farmacia.farmaciaonline.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/institucional")
public class InstitucionalController {

    @GetMapping("/boas-praticas")
    public String boasPraticas() {
        return "institucional/boas-praticas";
    }
}
