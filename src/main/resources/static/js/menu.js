/*
 * Menu de categorias no mobile (disclosure acessível por teclado).
 * - O botão alterna aria-expanded e a classe is-open da lista.
 * - Esc fecha o menu e devolve o foco ao botão.
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
      botaoSubmenu.setAttribute('aria-expanded', String(aberto));
      submenu.classList.toggle('is-open', aberto);
    });
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
