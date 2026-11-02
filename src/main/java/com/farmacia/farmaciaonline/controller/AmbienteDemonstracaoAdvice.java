package com.farmacia.farmaciaonline.controller;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class AmbienteDemonstracaoAdvice {

    private final Environment ambiente;

    @ModelAttribute("ambienteDemo")
    public boolean ambienteDemo() {
        return ambiente.acceptsProfiles(Profiles.of("demo"));
    }
}
