# Acessibilidade — Farma++

Guia curto para o time manter o site acessível (referência: WCAG 2.2, nível AA).
Leia o checklist antes de abrir um PR e os padrões antes de escrever HTML/CSS novo.

## Checklist para PRs

Copie para a descrição do PR e marque o que se aplica:

- [ ] **Estrutura:** usa o `fragments/layout` (que já traz `<header>`, `<nav>`, `<main>` e `<footer>`), tem um único `<h1>` e os títulos seguem a hierarquia (`h1` → `h2` → `h3`, sem pular nível).
- [ ] **Imagens:** toda `<img>` tem `alt` descritivo; imagem decorativa usa `alt=""`.
- [ ] **Formulários:** todo campo tem `<label for="...">` (ou `aria-label` quando não há texto visível); ajuda e erro estão ligados ao campo com `aria-describedby`. Use o fragment `fragments/campo`.
- [ ] **Teclado:** a página inteira funciona só com o teclado (Tab, Shift+Tab, Enter, Espaço, Esc) e o foco aparece em todo link, botão e campo.
- [ ] **Foco:** nenhum `outline: none` sem substituto equivalente.
- [ ] **Tamanhos:** fontes e espaçamentos em `rem` (não em `px`); a página continua utilizável com zoom de 200%.
- [ ] **Movimento:** toda animação/transição nova respeita `prefers-reduced-motion`.
- [ ] **Cores:** só `var(--cor-...)` do `tokens.css`; a informação não depende só da cor (ex.: erro tem texto, não só vermelho).
- [ ] **Testes:** rodei Lighthouse e axe DevTools nas páginas que mudei e anexei os resultados no PR.

## Contraste das cores

Meta (WCAG 2.1 AA): **4,5:1** para texto normal e **3:1** para texto grande (a partir de 24 px, ou 18,66 px em negrito),
componentes de interface e anel de foco. Razões calculadas com a fórmula de luminância do WCAG a partir dos valores de
`tokens.css`. Cada linha vale para o uso indicado; combinação que não está na tabela precisa ser medida antes de usar.

| Cor | Sobre | Uso | Razão | Meta | Aprovado |
|---|---|---|---|---|---|
| `--cor-texto` (`#1F2937`) | `--cor-branco` (`#FFFFFF`) | Texto do corpo, cards e painéis | 14.68:1 | 4,5:1 | sim |
| `--cor-texto` (`#1F2937`) | `--cor-fundo` (`#F5F6F8`) | Texto direto sobre o fundo da página | 13.57:1 | 4,5:1 | sim |
| `--cor-texto` (`#1F2937`) | `--cor-azul-suave` (`#DBE4EC`) | Selo "Indisponível" | 11.41:1 | 4,5:1 | sim |
| `--cor-branco` (`#FFFFFF`) | `--cor-azul` (`#0F4C81`) | Botão primário, banner, selo "Exige receita", skip-link, caixa do usuário, botão do carrinho | 8.86:1 | 4,5:1 | sim |
| `--cor-branco` (`#FFFFFF`) | `--cor-azul-hover` (`#0B3961`) | Botão primário em hover | 11.85:1 | 4,5:1 | sim |
| `--cor-azul` (`#0F4C81`) | `--cor-branco` (`#FFFFFF`) | Logo e links | 8.86:1 | 4,5:1 | sim |
| `--cor-azul` (`#0F4C81`) | `--cor-fundo` (`#F5F6F8`) | Links e logo sobre o fundo da página | 8.19:1 | 4,5:1 | sim |
| `--cor-azul` (`#0F4C81`) | `--cor-azul-suave` (`#DBE4EC`) | Faixa de diferenciais da home | 6.88:1 | 4,5:1 | sim |
| `--cor-azul-hover` (`#0B3961`) | `--cor-branco` (`#FFFFFF`) | Link do rodapé em hover | 11.85:1 | 4,5:1 | sim |
| `--cor-texto-hint` (`#526B7A`) | `--cor-branco` (`#FFFFFF`) | Placeholder, texto de ajuda e textos de apoio | 5.61:1 | 4,5:1 | sim |
| `--cor-texto-hint` (`#526B7A`) | `--cor-fundo` (`#F5F6F8`) | Texto de apoio sobre o fundo da página | 5.19:1 | 4,5:1 | sim |
| `--cor-erro` (`#B91C1C`) | `--cor-branco` (`#FFFFFF`) | Mensagem de erro do campo | 6.47:1 | 4,5:1 | sim |
| `--cor-erro` (`#B91C1C`) | `--cor-fundo` (`#F5F6F8`) | Mensagem de erro sobre o fundo da página | 5.98:1 | 4,5:1 | sim |
| `--cor-sucesso` (`#15803D`) | `--cor-branco` (`#FFFFFF`) | Texto de sucesso | 5.02:1 | 4,5:1 | sim |
| `--cor-sucesso` (`#15803D`) | `--cor-fundo` (`#F5F6F8`) | Texto de sucesso sobre o fundo da página | 4.64:1 | 4,5:1 | sim |
| `--cor-borda-controle` (`#7D8E9A`) | `--cor-branco` (`#FFFFFF`) | Borda de campo de formulário (componente de interface) | 3.38:1 | 3,0:1 | sim |
| `--cor-azul` (`#0F4C81`) | `--cor-branco` (`#FFFFFF`) | Anel de foco (contorno de 3 px) sobre branco | 8.86:1 | 3,0:1 | sim |
| `--cor-azul` (`#0F4C81`) | `--cor-fundo` (`#F5F6F8`) | Anel de foco sobre o fundo da página | 8.19:1 | 3,0:1 | sim |
| `--cor-branco` (`#FFFFFF`) | `--cor-azul` (`#0F4C81`) | Anel de foco dentro da caixa azul do usuário | 8.86:1 | 3,0:1 | sim |
| `--cor-erro` (`#B91C1C`) | `--cor-branco` (`#FFFFFF`) | Borda do campo inválido | 6.47:1 | 3,0:1 | sim |
| `--cor-erro` (`#B91C1C`) | `--cor-fundo` (`#F5F6F8`) | Faixa lateral do aviso de erro | 5.98:1 | 3,0:1 | sim |
| `--cor-sucesso` (`#15803D`) | `--cor-fundo` (`#F5F6F8`) | Faixa lateral do aviso de sucesso | 4.64:1 | 3,0:1 | sim |
| `--cor-branco` (`#FFFFFF`) | `--cor-erro` (`#B91C1C`) | Botão de perigo (`.btn-perigo`) | 6.47:1 | 4,5:1 | sim |
| `--cor-branco` (`#FFFFFF`) | `--cor-erro-hover` (`#991B1B`) | Botão de perigo em hover | 8.31:1 | 4,5:1 | sim |
| `--cor-erro` (`#B91C1C`) | `--cor-erro-fundo` (`#FEF2F2`) | Selo "Cancelado" | 5.91:1 | 4,5:1 | sim |
| `--cor-sucesso` (`#15803D`) | `--cor-sucesso-fundo` (`#F0FDF4`) | Selo "Entregue" | 4.79:1 | 4,5:1 | sim |
| `--cor-aviso` (`#92400E`) | `--cor-aviso-fundo` (`#FFFBEB`) | Selo "Aguardando pagamento" | 6.84:1 | 4,5:1 | sim |
| `--cor-azul` (`#0F4C81`) | `--cor-azul-suave` (`#DBE4EC`) | Selos de pedido em andamento (aprovado, separação, enviado, pronto) | 6.88:1 | 4,5:1 | sim |

**Tokens corrigidos nesta auditoria** (antes reprovavam em algum fundo):

| Token | Antes | Depois | Sobre branco (antes → depois) | Sobre o fundo da página (antes → depois) |
|---|---|---|---|---|
| `--cor-erro` | `#DC2626` | `#B91C1C` | 4.83 → 6.47 | 4.47 → 5.98 |
| `--cor-sucesso` | `#16A34A` | `#15803D` | 3.30 → 5.02 | 3.05 → 4.64 |

- O `--cor-erro` antigo ficava abaixo de 4,5:1 sobre o fundo da página (`--cor-fundo`), e o `--cor-sucesso` antigo
  só servia para borda: como texto reprovava.
- O teste `ContrasteTokensTest` confere os pares acima a cada `./mvnw verify`. Se alguém trocar uma cor do `tokens.css`
  (por exemplo o azul) e algum par cair abaixo da meta, o teste falha e mostra qual.
- Informação nunca só por cor: erro tem texto e borda, selos têm texto, e o campo inválido tem mensagem ligada por
  `aria-describedby`.

## Padrões do projeto

### Foco visível

`style.css` já define `:focus-visible` para `a`, `button`, `input`, `select`, `textarea`, `summary` e qualquer elemento com `tabindex` (exceto `-1`), como a `.tabela-rolavel` (contorno de 3px azul; branco sobre fundo azul). Elemento novo que receba foco deve herdar isso; se o fundo for escuro, troque só a `outline-color`.

Nunca escreva `outline: none` (ou `outline: 0`) sem colocar outro indicador de foco no lugar.
A única exceção é `#conteudo:focus`: o `<main>` só recebe foco por código, quando se usa o link "Pular para o conteúdo", e não é um controle.

### Tamanhos em rem e zoom de 200%

- Use `rem` para fonte, padding, margin, gap, largura e altura. `1rem` = fonte do navegador (16px por padrão), então quem aumenta a fonte nas configurações também é atendido.
- `px` só para bordas e contornos finos (1–3px) e para os breakpoints de `@media`.
- Não defina `font-size` fixo em `html` ou `body` e não bloqueie zoom no `<meta viewport>`.

### Redução de movimento

No fim do `style.css` há um bloco `@media (prefers-reduced-motion: reduce)` que desliga animações e transições para quem pediu isso no sistema. Ele vale para qualquer `animation`/`transition` nova, então não precisa repetir a regra em cada componente. Movimento que seja essencial para entender o conteúdo precisa de alternativa sem movimento.

### Campo de formulário

Use o fragment `templates/fragments/campo.html`. Ele gera o `<label for>`, o texto de ajuda e a mensagem de erro, e liga tudo ao campo:

```html
<div th:replace="~{fragments/campo :: campo(id='email', nome='email', rotulo='E-mail',
         tipo='email', ajuda='Ex.: nome@dominio.com', erro=${erroEmail}, autocomplete='email')}"></div>
```

Passe todos os parâmetros; use `null` quando não houver ajuda, erro ou autocomplete. Resultado quando há erro:

```html
<div class="campo">
    <label for="email">E-mail</label>
    <p class="campo__ajuda" id="email-ajuda">Ex.: nome@dominio.com</p>
    <input type="email" id="email" name="email" autocomplete="email" aria-describedby="email-ajuda email-erro" aria-invalid="true">
    <p class="campo__erro" role="alert" id="email-erro">Informe um e-mail válido.</p>
</div>
```

- `aria-describedby` faz o leitor de tela ler a ajuda e o erro junto com o campo.
- `aria-invalid="true"` só aparece quando há erro.
- `role="alert"` faz o leitor de tela anunciar o erro assim que ele surge.
- `autocomplete` (`username`, `current-password`, `email`, `tel`...) deixa o navegador e os leitores preencherem dados pessoais (WCAG 1.3.5).
- As classes (`campo`, `campo__ajuda`, `campo__erro`) estão no `style.css`.
- Por enquanto o fragment cobre `<input>`. Se precisar de `<select>` ou `<textarea>`, siga o mesmo padrão de ids e atributos.

## Guia de componentes

Classes prontas no `style.css` (seção "Componentes"). Reaproveite antes de criar outra. Cor, espaçamento, raio, fonte,
sombra e camada vêm de `tokens.css` (`--cor-*`, `--esp-1` a `--esp-6`, `--raio*`, `--fonte-*`, `--sombra-*`, `--z-*`).
O `EstiloSemCorSoltaTest` falha se o `style.css` tiver cor em hexadecimal, `rgb()` ou `z-index` com número.

### Botões

| Classe | Quando usar | Exemplo |
|---|---|---|
| `.btn-primario` | A ação da tela. Um por formulário, e o **primeiro** no DOM (o Enter aciona ele). | Salvar, Entrar, Confirmar pedido |
| `.btn-secundario` | Alternativa ou saída. | Cancelar, Voltar para a lista, Limpar filtros, Exportar CSV |
| `.btn-perigo` | Apaga ou desfaz algo. Só depois de uma tela de confirmação. | Excluir, Remover, Esvaziar carrinho |
| `.btn-link` | Ação discreta com cara de link (em geral um `<button>` de POST numa linha de tabela). | Tornar principal |
| `a.link-perigo` | Link que **leva** à confirmação de uma ação destrutiva. | Excluir minha conta |

- As quatro variantes de botão têm a mesma altura mínima (2.75rem, alvo de toque) e os mesmos estados de `:hover`,
  `:active`, `:disabled` e foco. Servem para `<button>` e para `<a>`.
- `.btn-primario--bloco` ocupa a largura toda (telas estreitas como login).
- "Cancelar" nunca é um link solto: é `.btn-secundario`.

### Grupo de ações

```html
<div class="acoes">
    <button type="submit" class="btn-primario">Salvar</button>
    <a class="btn-secundario" th:href="@{/conta}">Cancelar</a>
</div>
```

Principal primeiro, depois o secundário. O grupo quebra de linha no celular.

### Confirmação de ação destrutiva

Padrão do projeto: uma **página de confirmação** (GET) que diz o que vai acontecer, com `.btn-perigo` para confirmar
(POST com CSRF) e `.btn-secundario` para voltar. O recurso de outra pessoa responde 404. Exemplos: remover item do
carrinho, esvaziar o carrinho, remover cartão salvo, excluir endereço, excluir categoria. Exceção: a exclusão de produto no admin usa `confirm()` do navegador.

### Selos

`<span class="selo selo--...">texto</span>`. O texto sempre diz a situação; a cor só reforça.

- Tons: `selo--neutro`, `selo--destaque`, `selo--info`, `selo--aviso`, `selo--sucesso`, `selo--erro`.
- Situação do pedido: `<span th:replace="~{fragments/componentes :: situacao(${pedido.status})}"></span>`, que gera
  `selo selo--status-<STATUS>` com o rótulo. O tom de cada `StatusPedido` está no
  CSS (aguardando = aviso; aprovado, em separação, enviado e pronto = info; entregue = sucesso; cancelado = erro).

### Tabelas

`.tabela-admin` (listas) ou `.carrinho__tabela`, sempre dentro de
`<div class="tabela-rolavel" role="region" aria-label="..." tabindex="0">`. Valores e quantidades com `class="num"`
(alinhados à direita). Ação por linha com o nome do item em `.visually-hidden` ("Editar <span class="visually-hidden">Analgésicos</span>"),
lado a lado em `<div class="acoes-linha">` (`.btn-link` para editar, `a.link-perigo` para a confirmação de excluir).

### Filtros, paginação e estado vazio

- Filtros de lista: `<form class="filtros">` (grade que quebra sozinha) com os botões em `.filtros__acoes`.
- Paginação: `<nav class="paginacao" aria-label="Paginação">`.
- Lista sem itens: `<div class="estado-vazio">` com o que aconteceu e a próxima ação (`.acoes`).

### Navegação

- Migalhas (breadcrumb):
  `<nav class="migalhas" aria-label="Você está em"><ol><li><a>Início</a></li>…<li aria-current="page">Produto</li></ol></nav>`.
- Etapas (checkout): `<ol class="etapas">` com `<span class="etapas__numero">` e `<span class="etapas__rotulo">` em cada
  `<li>`; a atual leva `aria-current="step"` e as anteriores `class="etapas__feita"`. No celular só o rótulo da atual
  aparece. Nas telas da compra use `<ol th:replace="~{fragments/componentes :: etapas(n)}"></ol>` (1 Carrinho,
  2 Entrega e pagamento, 3 Resumo, 4 Pedido); se a etapa for condicional, ponha o `th:if` num `th:block` em volta,
  porque o `th:replace` roda antes do `th:if` no mesmo elemento.

### Blocos

- `.secao-cartao`: seção com borda e título (`h2`, ou `h3` quando está dentro de outra seção), para agrupar (Minha
  conta, detalhe do pedido, "Ambiente de demonstração" do pedido).
- `.cartoes-painel`: grade de atalhos em cartão (painel do admin), com `.cartoes-painel__titulo` e
  `.cartoes-painel__descricao`.
- `.nivel-1` a `.nivel-4`: recuo da árvore de categorias (no lugar de `style` inline).

### Rótulos

| Situação | Use | Evite |
|---|---|---|
| Gravar um formulário | Salvar | Gravar, Enviar |
| Sair sem gravar | Cancelar | Voltar (num formulário) |
| Apagar de vez | Excluir | Deletar, Apagar |
| Tirar de uma lista (carrinho, cartões) | Remover | Excluir |
| Status do pedido | Situação | Status |
| Abrir o detalhe | Ver | Detalhe, Detalhes |
| Voltar a uma página | Voltar para &lt;lugar&gt; | Voltar ao, Voltar à |

## Como testar

Teste a página rodando localmente (`./mvnw spring-boot:run` e abrir http://localhost:8080). Ferramentas automáticas pegam só parte dos problemas: sempre faça também o teste de teclado.

### 1. Teclado

1. Clique na barra de endereço, depois use **Tab** para andar pela página, **Shift+Tab** para voltar.
2. O primeiro Tab deve mostrar "Pular para o conteúdo"; Enter nele leva ao conteúdo.
3. Confira que a ordem do foco segue a ordem visual e que **todo** link, botão e campo mostra o contorno de foco.
4. **Enter** ativa links e botões; **Espaço** ativa botões; **Esc** fecha o submenu de categorias aberto e, em seguida, o menu (no mobile); clicar fora também fecha. No menu de categorias, o link da categoria e o botão da setinha são dois itens do Tab.
5. Não pode haver "armadilha" de teclado: sempre dá para sair de qualquer elemento só com o teclado.

### 2. Zoom de 200%

1. No navegador, **Ctrl** + **+** (ou Cmd + **+**) até 200%.
2. Confira que não há texto cortado, sobreposto ou rolagem horizontal, e que todo conteúdo e função continuam acessíveis.
3. Opcional: nas configurações do navegador, aumente o "tamanho da fonte" e repita.

### 3. Movimento reduzido

- **Windows:** Configurações → Acessibilidade → Efeitos visuais → desligar "Efeitos de animação".
- **macOS:** Ajustes do Sistema → Acessibilidade → Tela → "Reduzir movimento".
- Ou, no Chrome: DevTools → `Ctrl+Shift+P` → "Show Rendering" → "Emulate CSS media feature prefers-reduced-motion".

### 4. Lighthouse (já vem no Chrome)

1. Abra a página, **F12** → aba **Lighthouse**.
2. Marque só **Accessibility**, dispositivo **Desktop** (repita em **Mobile**) e clique em **Analyze page load**.
3. Anote a nota e leia a lista "Failed audits"; trate o que for de navegação, semântica e formulários.
4. Para anexar: menu **⋮** do relatório → **Save as HTML** (ou tire print do relatório).

### 5. axe DevTools

1. Instale a extensão **axe DevTools** (Deque) no Chrome ou Firefox.
2. **F12** → aba **axe DevTools** → **Scan ALL of my page**.
3. Para cada problema, leia a explicação e o trecho de HTML indicado; dá para marcar o elemento na página.
4. Para anexar: print da lista de problemas (ou **Export** → JSON/CSV, se disponível na sua versão).

### 6. WAVE

1. Instale a extensão **WAVE Evaluation Tool** (WebAIM) ou use https://wave.webaim.org (a página precisa estar acessível pela internet; para localhost, use a extensão).
2. Clique no ícone da extensão. Erros (vermelho) e alertas (amarelo) aparecem na própria página.
3. Veja também a aba **Structure** para conferir títulos e landmarks.

### 7. Leitor de tela

**NVDA (Windows, gratuito — https://www.nvaccess.org):**

1. Abra o NVDA (**Ctrl+Alt+N**). A tecla NVDA é **Insert**.
2. Navegue por Tab e também por atalhos: **H** (títulos), **D** (landmarks), **F** (campos de formulário), **K** (links), **B** (botões).
3. **NVDA+F7** lista títulos, links e landmarks da página.
4. Para parar de falar: **Ctrl**. Para fechar o NVDA: **Insert+Q**.

**VoiceOver (macOS, já instalado):**

1. Liga/desliga com **Cmd+F5**. A tecla VO é **Ctrl+Option**.
2. Navegue por **VO+setas** (direita/esquerda) ou Tab.
3. **VO+U** abre o rotor (títulos, links, landmarks, campos de formulário).

O que verificar: o rótulo de cada campo é lido, a ajuda e o erro são lidos junto com o campo, o erro é anunciado ao aparecer, botões só com ícone têm nome (ex.: "Buscar") e as imagens têm texto alternativo (ou são ignoradas, se decorativas).

## Como registrar no PR

1. Rode Lighthouse e axe DevTools na página inicial (e nas outras que o PR mudar).
2. Anexe no PR os relatórios/prints e escreva em 2–3 linhas o que foi encontrado e corrigido.
3. Problemas que não fazem parte do PR: abra uma issue e cite no PR.
