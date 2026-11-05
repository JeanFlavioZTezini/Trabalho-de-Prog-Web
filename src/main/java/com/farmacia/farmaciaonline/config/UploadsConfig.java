package com.farmacia.farmaciaonline.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
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
        // Cada foto tem nome UUID e nunca é sobrescrita (trocar a foto gera outro arquivo): pode ficar um ano no navegador.
        registry.addResourceHandler(ArmazenamentoImagem.URL_BASE + "**").addResourceLocations(local)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());
    }
}
