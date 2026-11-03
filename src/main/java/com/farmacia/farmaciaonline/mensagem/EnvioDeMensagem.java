package com.farmacia.farmaciaonline.mensagem;

import com.farmacia.farmaciaonline.model.enums.CanalCodigo;

/**
 * Envio de e-mail ou SMS. Hoje só existe o adaptador de demonstração ({@code DemoEnvioAdapter}), que não manda nada de
 * verdade. Serve para os códigos de verificação (F-05) e para o resumo do pedido (F-14).
 * O texto pode conter um código de verificação: quem implementar esta interface nunca deve gravá-lo em log.
 */
public interface EnvioDeMensagem {

    void enviar(CanalCodigo canal, String destino, String assunto, String texto);

    /**
     * O mesmo envio, dizendo de qual conta é a mensagem. O adaptador demo guarda a conta para mostrar a cada pessoa só
     * as mensagens dela; um envio real não precisa disso.
     */
    default void enviar(CanalCodigo canal, String destino, String assunto, String texto, Long contaId) {
        enviar(canal, destino, assunto, texto);
    }
}
