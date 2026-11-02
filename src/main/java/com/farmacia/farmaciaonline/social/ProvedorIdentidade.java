package com.farmacia.farmaciaonline.social;

import com.farmacia.farmaciaonline.model.enums.ProvedorSocial;

/**
 * Login social (F-20, UC10-A1/A2), no mesmo molde das outras integrações simuladas (CEP, gateway, mensagens): esta
 * interface é o que o sistema usa; o único adaptador é o demo ({@code social.demo}), que só existe no perfil
 * {@code demo}. Um OAuth real com Google/Facebook seria outro adaptador; credenciais reais não fazem parte do projeto.
 */
public interface ProvedorIdentidade {

    /** Para onde mandar o navegador pedir o consentimento; {@code state} volta intacto no retorno. */
    String urlDeConsentimento(ProvedorSocial provedor, String state);

    /**
     * Troca o código de uso único do retorno pela identidade da pessoa. Código desconhecido, já usado ou de outro
     * provedor lança {@link IllegalArgumentException}; provedor fora do ar lança {@link ProvedorIndisponivelException}.
     */
    IdentidadeExterna trocarCodigo(ProvedorSocial provedor, String codigo);
}
