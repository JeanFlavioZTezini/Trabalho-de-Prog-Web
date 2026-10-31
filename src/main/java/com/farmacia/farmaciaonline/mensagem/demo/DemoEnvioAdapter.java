package com.farmacia.farmaciaonline.mensagem.demo;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

import com.farmacia.farmaciaonline.mensagem.EnvioDeMensagem;
import com.farmacia.farmaciaonline.mensagem.MensagemEnviada;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;

/**
 * Envio simulado de e-mail e SMS: em vez de mandar, guarda as últimas mensagens na memória (somem ao reiniciar).
 * Só a página {@code /caixa-demo}, que existe apenas no perfil demo, mostra essas mensagens. Nada é gravado em log.
 */
@Component
public class DemoEnvioAdapter implements EnvioDeMensagem {

    static final int LIMITE = 50;

    private final Deque<MensagemEnviada> caixa = new ArrayDeque<>();

    @Override
    public synchronized void enviar(CanalCodigo canal, String destino, String assunto, String texto) {
        caixa.addFirst(new MensagemEnviada(LocalDateTime.now(), canal, destino, assunto, texto));
        while (caixa.size() > LIMITE) {
            caixa.removeLast();
        }
    }

    /** Da mais recente para a mais antiga. */
    public synchronized List<MensagemEnviada> mensagens() {
        return List.copyOf(caixa);
    }
}
