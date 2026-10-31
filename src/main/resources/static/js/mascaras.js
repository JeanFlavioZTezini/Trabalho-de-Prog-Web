// Máscaras de digitação do cadastro: CPF (000.000.000-00) e celular ((00) 00000-0000).
// Marque o campo com data-mascara="cpf" ou data-mascara="celular". É só conforto: o servidor aceita com ou sem
// pontuação e grava só os dígitos, então sem JavaScript o formulário continua funcionando.
(function () {
    var MASCARAS = {
        cpf: function (d) {
            if (d.length <= 3) { return d; }
            if (d.length <= 6) { return d.slice(0, 3) + '.' + d.slice(3); }
            if (d.length <= 9) { return d.slice(0, 3) + '.' + d.slice(3, 6) + '.' + d.slice(6); }
            return d.slice(0, 3) + '.' + d.slice(3, 6) + '.' + d.slice(6, 9) + '-' + d.slice(9);
        },
        celular: function (d) {
            if (d.length === 0) { return ''; }
            if (d.length <= 2) { return '(' + d; }
            if (d.length <= 7) { return '(' + d.slice(0, 2) + ') ' + d.slice(2); }
            return '(' + d.slice(0, 2) + ') ' + d.slice(2, 7) + '-' + d.slice(7);
        }
    };

    function aplicar(campo, formatar) {
        var antes = campo.value;
        var depois = formatar(antes.replace(/\D/g, '').slice(0, 11));
        if (depois === antes) {
            return;
        }
        var focado = document.activeElement === campo;
        var digitosAntesDoCursor = focado ? antes.slice(0, campo.selectionStart).replace(/\D/g, '').length : 0;
        campo.value = depois;
        if (focado) {
            // devolve o cursor para logo depois do mesmo dígito (editar no meio não pula para o fim)
            var posicao = 0;
            var contados = 0;
            while (posicao < depois.length && contados < digitosAntesDoCursor) {
                if (/\d/.test(depois.charAt(posicao))) {
                    contados++;
                }
                posicao++;
            }
            campo.setSelectionRange(posicao, posicao);
        }
    }

    Array.prototype.forEach.call(document.querySelectorAll('[data-mascara]'), function (campo) {
        var formatar = MASCARAS[campo.getAttribute('data-mascara')];
        if (!formatar) {
            return;
        }
        campo.addEventListener('input', function () { aplicar(campo, formatar); });
        aplicar(campo, formatar);
    });
})();
