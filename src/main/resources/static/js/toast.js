// Mensagens de sucesso e erro do admin como toast no canto da tela: some sozinho depois de alguns segundos, com pausa
// enquanto o mouse está em cima ou o foco está dentro. Sem JavaScript, o alerta fica no topo da página, como antes.
(function () {
    'use strict';

    var DURACAO = 6000;
    var alertas = document.querySelectorAll('[data-toast]');
    if (!alertas.length) {
        return;
    }

    var pilha = document.createElement('div');
    pilha.className = 'toasts';
    document.body.appendChild(pilha);

    alertas.forEach(function (alerta) {
        var restante = DURACAO;
        var inicio;
        var temporizador = null;

        alerta.classList.add('toast', alerta.classList.contains('alerta--erro') ? 'toast--erro' : 'toast--sucesso');
        var fechar = document.createElement('button');
        fechar.type = 'button';
        fechar.className = 'toast__fechar';
        fechar.setAttribute('aria-label', 'Fechar mensagem');
        fechar.textContent = '×';
        alerta.appendChild(fechar);
        pilha.appendChild(alerta);

        function sumir() {
            clearTimeout(temporizador);
            alerta.remove();
            if (!pilha.children.length) {
                pilha.remove();
            }
        }
        function contar() {
            if (temporizador === null) {
                inicio = Date.now();
                temporizador = setTimeout(sumir, restante);
            }
        }
        function pausar() {
            if (temporizador !== null) {
                clearTimeout(temporizador);
                temporizador = null;
                restante -= Date.now() - inicio;
            }
        }

        alerta.addEventListener('mouseenter', pausar);
        alerta.addEventListener('mouseleave', function () {
            if (!alerta.contains(document.activeElement)) {
                contar();
            }
        });
        alerta.addEventListener('focusin', pausar);
        alerta.addEventListener('focusout', function () {
            if (!alerta.matches(':hover')) {
                contar();
            }
        });
        fechar.addEventListener('click', sumir);
        contar();
    });
})();
