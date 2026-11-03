package com.farmacia.farmaciaonline.social.demo;

import java.time.Duration;
import java.time.Instant;
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
 * conta predefinida. Os códigos de retorno são de uso único, valem por 5 minutos e ficam na memória, como as outras
 * simulações; os vencidos são varridos a cada código novo, para os abandonados não acumularem.
 */
@Service
@Profile("demo")
public class DemoProvedorIdentidade implements ProvedorIdentidade {

    static final Duration VALIDADE_DO_CODIGO = Duration.ofMinutes(5);

    private final Map<String, Emitido> codigos = new ConcurrentHashMap<>();

    @Override
    public String urlDeConsentimento(ProvedorSocial provedor, String state) {
        return UriComponentsBuilder.fromPath("/login/social/demo/{provedor}/consentimento")
                .queryParam("state", state)
                .buildAndExpand(provedor.name().toLowerCase()).toUriString();
    }

    /** Chamado pela tela de consentimento quando a pessoa permite. */
    public String emitirCodigo(IdentidadeExterna identidade) {
        return emitirCodigo(identidade, Instant.now());
    }

    String emitirCodigo(IdentidadeExterna identidade, Instant agora) {
        codigos.values().removeIf(emitido -> emitido.vencido(agora));
        String codigo = UUID.randomUUID().toString();
        codigos.put(codigo, new Emitido(identidade, agora));
        return codigo;
    }

    @Override
    public IdentidadeExterna trocarCodigo(ProvedorSocial provedor, String codigo) {
        return trocarCodigo(provedor, codigo, Instant.now());
    }

    IdentidadeExterna trocarCodigo(ProvedorSocial provedor, String codigo, Instant agora) {
        Emitido emitido = codigo == null ? null : codigos.remove(codigo);
        if (emitido == null || emitido.vencido(agora) || emitido.identidade().provedor() != provedor) {
            throw new IllegalArgumentException("Código de retorno inválido ou já usado.");
        }
        return emitido.identidade();
    }

    int quantidadeDeCodigos() {
        return codigos.size();
    }

    private record Emitido(IdentidadeExterna identidade, Instant emitidoEm) {
        boolean vencido(Instant agora) {
            return emitidoEm.plus(VALIDADE_DO_CODIGO).isBefore(agora);
        }
    }
}
