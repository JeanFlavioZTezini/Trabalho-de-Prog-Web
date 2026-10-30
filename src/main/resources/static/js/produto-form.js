// UC12: avisa antes de enviar quando uma foto passa de 2 MB (o servidor valida de novo).
(function () {
    var entrada = document.getElementById('novasImagens');
    var aviso = document.getElementById('aviso-imagem');
    if (!entrada || !aviso) {
        return;
    }
    var MAXIMO = 2 * 1024 * 1024;
    entrada.addEventListener('change', function () {
        var grandes = Array.prototype.filter.call(entrada.files, function (f) {
            return f.size > MAXIMO;
        });
        if (grandes.length > 0) {
            aviso.textContent = 'Cada foto pode ter no máximo 2 MB. Escolha arquivos menores.';
            entrada.value = '';
        } else {
            aviso.textContent = '';
        }
    });
})();
