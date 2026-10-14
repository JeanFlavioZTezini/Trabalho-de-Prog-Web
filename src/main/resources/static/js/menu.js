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

  botao.addEventListener('click', function () {
    definirAberto(botao.getAttribute('aria-expanded') !== 'true');
  });

  document.addEventListener('keydown', function (evento) {
    if (evento.key === 'Escape' && botao.getAttribute('aria-expanded') === 'true') {
      definirAberto(false);
      botao.focus();
    }
  });
})();
