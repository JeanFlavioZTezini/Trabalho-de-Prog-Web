// CEP do formulário de endereço:
// 1. máscara: coloca o hífen sozinho enquanto a pessoa digita (00000-000);
// 2. busca: com os 8 números, consulta GET /enderecos/cep e preenche logradouro, bairro, cidade e UF.
// Sem JavaScript, o botão "Buscar CEP" faz a mesma consulta por POST.
(function () {
    var campo = document.getElementById('cep');
    if (!campo) {
        return;
    }
    var status = document.getElementById('cep-status');
    var consultado = null; // último CEP buscado, para não repetir a mesma consulta
    var pendente = null;

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

    function avisar(mensagem, erro) {
        if (!status) {
            return;
        }
        status.textContent = mensagem;
        status.classList.toggle('cep-status--erro', !!erro);
    }

    function preencher(id, valor) {
        var alvo = document.getElementById(id);
        if (alvo) {
            alvo.value = valor || '';
        }
    }

    function buscar() {
        var digitos = campo.value.replace(/\D/g, '');
        if (digitos.length !== 8 || digitos === consultado || !campo.dataset.consulta || !window.fetch) {
            return;
        }
        consultado = digitos;
        if (pendente) {
            pendente.abort();
        }
        pendente = window.AbortController ? new AbortController() : null;
        avisar('Buscando o endereço do CEP…', false);

        fetch(campo.dataset.consulta + '?cep=' + digitos, {
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin',
            signal: pendente ? pendente.signal : undefined
        }).then(function (resposta) {
            if (!resposta.ok || resposta.headers.get('Content-Type').indexOf('json') < 0) {
                throw new Error('resposta ' + resposta.status); // sessão expirada volta a página de login
            }
            return resposta.json();
        }).then(function (dados) {
            if (campo.value.replace(/\D/g, '') !== digitos) {
                return; // a pessoa já mudou o CEP enquanto a resposta vinha
            }
            if (dados.encontrado) {
                // os quatro campos pertencem ao CEP: CEP geral de cidade limpa logradouro e bairro de um CEP anterior
                preencher('logradouro', dados.logradouro);
                preencher('bairro', dados.bairro);
                preencher('cidade', dados.cidade);
                preencher('uf', dados.uf);
            }
            avisar(dados.mensagem, !dados.encontrado);
        }).catch(function (erro) {
            if (erro.name === 'AbortError') {
                return;
            }
            consultado = null; // deixa tentar de novo
            avisar('Não foi possível consultar o CEP agora. Preencha o endereço à mão.', true);
        });
    }

    campo.addEventListener('input', function () {
        aplicar();
        buscar(); // só ao digitar: abrir a edição não sobrescreve o endereço salvo
    });
    aplicar(); // endereço já salvo chega só com dígitos (79002336): mostra 79002-336
    consultado = campo.value.replace(/\D/g, '');
})();
