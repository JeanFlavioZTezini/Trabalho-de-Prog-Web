// Confirmação numa janela da própria página, no lugar do confirm() do navegador.
// Todo <form data-confirmar="mensagem"> pergunta antes de enviar. O foco abre em "Cancelar", para Enter não
// excluir sem querer; Esc fecha; ao fechar, o foco volta ao botão que abriu a janela.
(function () {
    'use strict';

    var dialogo;
    var mensagem;
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
        dialogo.showModal();
        dialogo.querySelector('[data-acao="cancelar"]').focus();
    });
})();
