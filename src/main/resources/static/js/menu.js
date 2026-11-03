/*
 * Menu de categorias no mobile (disclosure acessível por teclado).
 * - O botão alterna aria-expanded e a classe is-open da lista.
 * - Esc fecha o menu e devolve o foco ao botão; clicar fora fecha o menu e os submenus.
 * - Abrir um submenu fecha os outros.
 * - Sem JavaScript, o CSS mantém a lista sempre visível (ver .js em style.css).
 */
(function () {
  var botao = document.querySelector('.menu-categorias__toggle');
  var lista = document.getElementById('menu-categorias');
  if (!botao || !lista) return;

  function definirAberto(aberto) {
    botao.setAttribute('aria-expanded', String(aberto));
    lista.classList.toggle('is-open', aberto);
  }

  var botoesSubmenu = document.querySelectorAll('.menu-categorias__submenu-toggle');

  function fecharSubmenus() {
    botoesSubmenu.forEach(function (botaoSubmenu) {
      botaoSubmenu.setAttribute('aria-expanded', 'false');
      var submenu = document.getElementById(botaoSubmenu.getAttribute('aria-controls'));
      if (submenu) submenu.classList.remove('is-open');
    });
  }

  botao.addEventListener('click', function () {
    var aberto = botao.getAttribute('aria-expanded') !== 'true';
    if (!aberto) fecharSubmenus();
    definirAberto(aberto);
  });

  botoesSubmenu.forEach(function (botaoSubmenu) {
    var submenu = document.getElementById(botaoSubmenu.getAttribute('aria-controls'));
    if (!submenu) return;

    botaoSubmenu.addEventListener('click', function () {
      var aberto = botaoSubmenu.getAttribute('aria-expanded') !== 'true';
      fecharSubmenus();
      botaoSubmenu.setAttribute('aria-expanded', String(aberto));
      submenu.classList.toggle('is-open', aberto);
    });
  });

  document.addEventListener('click', function (evento) {
    if (!evento.target.closest('.menu-categorias__item')) fecharSubmenus();
    if (!evento.target.closest('.menu-categorias')) definirAberto(false);
  });

  document.addEventListener('keydown', function (evento) {
    if (evento.key !== 'Escape') return;

    var submenuAberto = Array.from(botoesSubmenu).find(function (botaoSubmenu) {
      return botaoSubmenu.getAttribute('aria-expanded') === 'true';
    });
    if (submenuAberto) {
      fecharSubmenus();
      submenuAberto.focus();
    } else if (botao.getAttribute('aria-expanded') === 'true') {
      definirAberto(false);
      botao.focus();
    }
  });
})();

/*
 * Menu da conta no cabeçalho ("Olá, Nome ▾"): mesmo padrão de disclosure do menu de categorias.
 * - O botão alterna aria-expanded e a classe is-open do menu.
 * - Esc fecha e devolve o foco ao botão; clicar fora fecha.
 * - Sem JavaScript, o CSS deixa os links sempre visíveis (ver .js em style.css).
 */
(function () {
  var botao = document.querySelector('.usuario__toggle');
  var menu = document.getElementById('menu-usuario');
  if (!botao || !menu) return;

  function definirAberto(aberto) {
    botao.setAttribute('aria-expanded', String(aberto));
    menu.classList.toggle('is-open', aberto);
  }

  botao.addEventListener('click', function () {
    definirAberto(botao.getAttribute('aria-expanded') !== 'true');
  });

  document.addEventListener('click', function (evento) {
    if (!evento.target.closest('.usuario--logado')) definirAberto(false);
  });

  document.addEventListener('keydown', function (evento) {
    if (evento.key === 'Escape' && botao.getAttribute('aria-expanded') === 'true') {
      definirAberto(false);
      botao.focus();
    }
  });
})();
