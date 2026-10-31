// Máscara do CEP: coloca o hífen sozinho enquanto a pessoa digita (00000-000)
(function () {
    var campo = document.getElementById('cep');
    if (!campo) {
        return;
    }

    function formatar(texto) {
        var digitos = texto.replace(/\D/g, '').slice(0, 8);
        return digitos.length > 5 ? digitos.slice(0, 5) + '-' + digitos.slice(5) : digitos;
    }

    function aplicar() {
        var antes = campo.value;
        var depois = formatar(antes);
        if (depois === antes) {
            return;
        }
        var focado = document.activeElement === campo;
        var digitosAntesDoCursor = focado ? antes.slice(0, campo.selectionStart).replace(/\D/g, '').length : 0;
        campo.value = depois;
        if (focado) {
            // devolve o cursor para logo depois do mesmo dígito (editar no meio do CEP não pula para o fim)
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

    campo.addEventListener('input', aplicar);
    aplicar(); // endereço já salvo chega só com dígitos (01310100): mostra 01310-100
})();
