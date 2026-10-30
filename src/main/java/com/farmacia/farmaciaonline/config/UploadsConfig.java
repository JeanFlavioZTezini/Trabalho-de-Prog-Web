package com.farmacia.farmaciaonline.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.farmacia.farmaciaonline.service.ArmazenamentoImagem;

/** Serve /uploads/produtos/** direto da pasta de uploads (fora do JAR). */
@Configuration
public class UploadsConfig implements WebMvcConfigurer {

    private final String local;

    public UploadsConfig(@Value("${app.uploads.dir:uploads}") String diretorio) {
        String uri = ArmazenamentoImagem.pastaDe(diretorio).toUri().toString();
        this.local = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ArmazenamentoImagem.URL_BASE + "**").addResourceLocations(local);
    }
}
