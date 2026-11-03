package com.farmacia.farmaciaonline.mensagem.demo;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.farmacia.farmaciaonline.mensagem.EnvioDeMensagem;
import com.farmacia.farmaciaonline.mensagem.MensagemEnviada;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;

/**
 * Envio simulado de e-mail e SMS: em vez de mandar, guarda as últimas mensagens na memória (somem ao reiniciar).
 * Só a página {@code /caixa-demo}, que existe apenas no perfil demo, mostra essas mensagens. Nada é gravado em log.
 * Cada mensagem guarda a conta dona, e não o destino nem quem pediu o envio: quem pede a recuperação de outra conta não
 * é o dono dela, e o celular pode se repetir entre contas.
 */
@Component
public class DemoEnvioAdapter implements EnvioDeMensagem {

    static final int LIMITE = 50;

    private final Deque<MensagemEnviada> caixa = new ArrayDeque<>();

    @Override
    public void enviar(CanalCodigo canal, String destino, String assunto, String texto) {
        enviar(canal, destino, assunto, texto, null);
    }

    @Override
    public synchronized void enviar(CanalCodigo canal, String destino, String assunto, String texto, Long contaId) {
        caixa.addFirst(new MensagemEnviada(LocalDateTime.now(), canal, destino, assunto, texto, contaId));
        while (caixa.size() > LIMITE) {
            caixa.removeLast();
        }
    }

    /** Da mais recente para a mais antiga. */
    public synchronized List<MensagemEnviada> mensagens() {
        return List.copyOf(caixa);
    }

    /** O administrador vê todas; os demais, só as das contas dadas (mensagem sem conta: só o administrador). */
    public synchronized List<MensagemEnviada> mensagensVisiveis(Set<Long> contas, boolean admin) {
        if (admin) {
            return List.copyOf(caixa);
        }
        return caixa.stream().filter(m -> m.contaId() != null && contas.contains(m.contaId())).toList();
    }
}
