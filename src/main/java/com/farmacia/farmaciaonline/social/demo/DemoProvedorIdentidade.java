package com.farmacia.farmaciaonline.social.demo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.farmacia.farmaciaonline.model.enums.ProvedorSocial;
import com.farmacia.farmaciaonline.social.IdentidadeExterna;
import com.farmacia.farmaciaonline.social.ProvedorIdentidade;

/**
 * Provedor de identidade SIMULADO (F-20): a tela de consentimento é nossa e deixa a pessoa escolher quem ela é no
 * "Google"/"Facebook", inclusive e-mail não verificado, sem e-mail, cancelar e provedor fora do ar. Nunca entra numa
 * conta predefinida. Os códigos de retorno são de uso único e ficam na memória, como as outras simulações.
 */
@Service
@Profile("demo")
public class DemoProvedorIdentidade implements ProvedorIdentidade {

    private final Map<String, IdentidadeExterna> codigos = new ConcurrentHashMap<>();

    @Override
    public String urlDeConsentimento(ProvedorSocial provedor, String state) {
        return UriComponentsBuilder.fromPath("/login/social/demo/{provedor}/consentimento")
                .queryParam("state", state)
                .buildAndExpand(provedor.name().toLowerCase()).toUriString();
    }

    /** Chamado pela tela de consentimento quando a pessoa permite. */
    public String emitirCodigo(IdentidadeExterna identidade) {
        String codigo = UUID.randomUUID().toString();
        codigos.put(codigo, identidade);
        return codigo;
    }

    @Override
    public IdentidadeExterna trocarCodigo(ProvedorSocial provedor, String codigo) {
        IdentidadeExterna identidade = codigo == null ? null : codigos.remove(codigo);
        if (identidade == null || identidade.provedor() != provedor) {
            throw new IllegalArgumentException("Código de retorno inválido ou já usado.");
        }
        return identidade;
    }
}
