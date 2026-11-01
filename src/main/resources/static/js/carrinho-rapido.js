/*
 * "Adicionar ao carrinho" sem sair da página (melhoria progressiva).
 * - Intercepta o envio de form.card-carrinho, manda o mesmo POST com Accept: application/json e mostra um painel
 *   pequeno com o item. Atualiza o número do cabeçalho.
 * - Qualquer falha (rede, resposta que não é JSON, 403 do CSRF) deixa o formulário seguir o caminho normal, que
 *   redireciona para /carrinho. Sem JavaScript, nada disto existe.
 * - O painel não some sozinho: fecha com "Continuar comprando" ou Esc e devolve o foco ao botão clicado.
 */
(function () {
  var painel = null;
  var origem = null;
  var enviando = false;

  var moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

  function el(tag, classe, texto) {
    var e = document.createElement(tag);
    if (classe) e.className = classe;
    if (texto != null) e.textContent = texto;
    return e;
  }

  function criarPainel() {
    painel = el('div', 'carrinho-painel');
    painel.id = 'carrinho-painel';
    painel.hidden = true;
    painel.setAttribute('aria-label', 'Carrinho');

    var status = el('p', 'carrinho-painel__status');
    status.setAttribute('role', 'status');
    status.setAttribute('aria-live', 'polite');
    var erro = el('p', 'carrinho-painel__erro');
    erro.setAttribute('role', 'alert');

    var item = el('div', 'carrinho-painel__item');
    var imagem = el('img');
    imagem.alt = '';
    imagem.width = 56;
    imagem.height = 56;
    var dados = el('div');
    dados.appendChild(el('p', 'carrinho-painel__nome'));
    dados.appendChild(el('p', 'carrinho-painel__detalhe'));
    dados.appendChild(el('p', 'carrinho-painel__detalhe'));
    dados.appendChild(el('p'));
    item.appendChild(imagem);
    item.appendChild(dados);

    var avisos = el('ul', 'carrinho-painel__avisos');

    var acoes = el('div', 'carrinho-painel__acoes');
    var continuar = el('button', 'btn-secundario', 'Continuar comprando');
    continuar.type = 'button';
    continuar.addEventListener('click', fechar);
    var ver = el('a', null, 'Ver carrinho');
    acoes.appendChild(continuar);
    acoes.appendChild(ver);

    painel.appendChild(status);
    painel.appendChild(erro);
    painel.appendChild(item);
    painel.appendChild(avisos);
    painel.appendChild(acoes);
    document.body.appendChild(painel);
  }

  function partes() {
    return {
      status: painel.querySelector('.carrinho-painel__status'),
      erro: painel.querySelector('.carrinho-painel__erro'),
      item: painel.querySelector('.carrinho-painel__item'),
      imagem: painel.querySelector('.carrinho-painel__item img'),
      textos: painel.querySelectorAll('.carrinho-painel__item p'),
      avisos: painel.querySelector('.carrinho-painel__avisos'),
      continuar: painel.querySelector('button'),
      ver: painel.querySelector('a')
    };
  }

  function fechar() {
    if (!painel || painel.hidden) return;
    painel.hidden = true;
    var p = partes();
    p.status.textContent = '';
    p.erro.textContent = '';
    if (origem) origem.focus();
  }

  function abrir(formulario, aoPreencher) {
    if (!painel) criarPainel();
    var p = partes();
    p.status.textContent = '';
    p.erro.textContent = '';
    p.avisos.textContent = '';
    p.item.hidden = true;
    p.ver.href = formulario.getAttribute('action').replace(/\/itens$/, '');
    painel.classList.remove('carrinho-painel--erro');
    painel.hidden = false;
    // O texto entra depois de o painel aparecer: leitor de tela só anuncia mudança em região já visível.
    setTimeout(function () {
      aoPreencher(p);
      p.continuar.focus();
    }, 50);
  }

  function mostrarSucesso(formulario, dados) {
    abrir(formulario, function (p) {
      p.status.textContent = dados.mensagem;
      if (dados.item) {
        p.item.hidden = false;
        if (dados.item.imagem) p.imagem.src = dados.item.imagem;
        p.textos[0].textContent = dados.item.nome;
        p.textos[1].textContent = dados.item.descricao;
        p.textos[2].textContent = 'Quantidade no carrinho: ' + dados.item.quantidade;
        p.textos[3].textContent = moeda.format(dados.item.precoUnitario) + ' cada, subtotal ' +
          moeda.format(dados.item.subtotal);
      }
      (dados.avisos || []).forEach(function (aviso) {
        p.avisos.appendChild(el('li', null, aviso));
      });
    });
    atualizarContagem(dados.totalUnidades);
  }

  function mostrarErro(formulario, mensagem) {
    abrir(formulario, function (p) {
      painel.classList.add('carrinho-painel--erro');
      p.erro.textContent = mensagem;
    });
  }

  function atualizarContagem(total) {
    var alvo = document.getElementById('carrinho-contagem');
    if (!alvo) return;
    alvo.textContent = '';
    if (!(total > 0)) return;
    alvo.appendChild(document.createTextNode('(' + total + ')'));
    alvo.appendChild(el('span', 'visually-hidden', ' itens no carrinho'));
  }

  document.addEventListener('submit', function (e) {
    var formulario = e.target;
    if (!(formulario instanceof HTMLFormElement) || !formulario.classList.contains('card-carrinho')) return;
    if (!window.fetch || !window.FormData || !window.URLSearchParams) return;

    e.preventDefault();
    if (enviando) return;
    enviando = true;
    origem = formulario.querySelector('button[type="submit"]');

    fetch(formulario.action, {
      method: 'POST',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(new FormData(formulario)),
      credentials: 'same-origin'
    }).then(function (resposta) {
      var ehJson = (resposta.headers.get('Content-Type') || '').indexOf('application/json') !== -1;
      if (!ehJson) throw new Error('resposta sem JSON');
      return resposta.json().then(function (dados) {
        if (resposta.ok) mostrarSucesso(formulario, dados);
        else mostrarErro(formulario, dados.erro || 'Não foi possível adicionar ao carrinho.');
      });
    }).catch(function () {
      formulario.submit();
    }).then(function () {
      enviando = false;
    });
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') fechar();
  });
})();
