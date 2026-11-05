// Confirmação numa janela da própria página, no lugar do confirm() do navegador.
// Todo <form data-confirmar="mensagem"> pergunta antes de enviar. O foco abre em "Cancelar", para Enter não
// excluir sem querer; Esc fecha; ao fechar, o foco volta ao botão que abriu a janela.
// Opcionais: data-confirmar-titulo, data-confirmar-botao e data-confirmar-voltar trocam "Confirmar exclusão",
// "Excluir" e "Cancelar";
// data-confirmar-se="campo=valor" só pergunta quando o campo do formulário tem aquele valor.
(function () {
    'use strict';

    var dialogo;
    var mensagem;
    var titulo;
    var botaoConfirmar;
    var botaoVoltar;
    var formulario;
    var origem;

    function criar() {
        dialogo = document.createElement('dialog');
        dialogo.className = 'dialogo';
        dialogo.setAttribute('aria-labelledby', 'dialogo-titulo');
        dialogo.setAttribute('aria-describedby', 'dialogo-mensagem');
        dialogo.innerHTML = '<h2 id="dialogo-titulo" class="dialogo__titulo">Confirmar exclusão</h2>'
            + '<p id="dialogo-mensagem"></p>'
            + '<div class="dialogo__acoes">'
            + '<button type="button" class="btn-secundario" data-acao="cancelar">Cancelar</button>'
            + '<button type="button" class="btn-perigo" data-acao="confirmar">Excluir</button>'
            + '</div>';
        mensagem = dialogo.querySelector('#dialogo-mensagem');
        titulo = dialogo.querySelector('#dialogo-titulo');
        botaoConfirmar = dialogo.querySelector('[data-acao="confirmar"]');
        botaoVoltar = dialogo.querySelector('[data-acao="cancelar"]');

        dialogo.addEventListener('click', function (evento) {
            var botao = evento.target.closest('[data-acao]');
            if (botao && botao.dataset.acao === 'confirmar') {
                var enviar = formulario;
                dialogo.close();
                enviar.submit(); // submit() não dispara o evento de novo: não reabre a janela
            } else if (botao || evento.target === dialogo) {
                dialogo.close(); // "Cancelar" ou clique fora da janela
            }
        });
        dialogo.addEventListener('close', function () {
            if (origem) {
                origem.focus();
            }
            formulario = null;
        });
        document.body.appendChild(dialogo);
    }

    document.addEventListener('submit', function (evento) {
        var form = evento.target;
        if (!form.matches('form[data-confirmar]')) {
            return;
        }
        var condicao = form.dataset.confirmarSe;
        if (condicao) {
            var partes = condicao.split('=');
            var campo = form.elements[partes[0]];
            if (!campo || campo.value !== partes[1]) {
                return; // outra escolha: envia sem perguntar
            }
        }
        evento.preventDefault();
        if (typeof HTMLDialogElement === 'undefined') {
            if (window.confirm(form.dataset.confirmar)) { // navegador sem <dialog>: o confirm() de antes
                form.submit();
            }
            return;
        }
        if (!dialogo) {
            criar();
        }
        formulario = form;
        origem = evento.submitter || form.querySelector('[type="submit"]');
        mensagem.textContent = form.dataset.confirmar;
        titulo.textContent = form.dataset.confirmarTitulo || 'Confirmar exclusão';
        botaoConfirmar.textContent = form.dataset.confirmarBotao || 'Excluir';
        botaoVoltar.textContent = form.dataset.confirmarVoltar || 'Cancelar';
        dialogo.showModal();
        dialogo.querySelector('[data-acao="cancelar"]').focus();
    });
})();
