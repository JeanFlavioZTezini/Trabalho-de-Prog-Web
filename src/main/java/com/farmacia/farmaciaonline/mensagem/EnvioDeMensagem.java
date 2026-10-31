package com.farmacia.farmaciaonline.mensagem;

import com.farmacia.farmaciaonline.model.enums.CanalCodigo;

/**
 * Envio de e-mail ou SMS. Hoje só existe o adaptador de demonstração ({@code DemoEnvioAdapter}), que não manda nada de
 * verdade. Serve para os códigos de verificação (F-05) e para o resumo do pedido (F-14).
 * O texto pode conter um código de verificação: quem implementar esta interface nunca deve gravá-lo em log.
 */
public interface EnvioDeMensagem {

    void enviar(CanalCodigo canal, String destino, String assunto, String texto);
}
