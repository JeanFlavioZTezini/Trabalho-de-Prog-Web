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

## Padrões do projeto

### Foco visível

`style.css` já define `:focus-visible` para `a`, `button`, `input`, `select` e `textarea` (contorno de 3px azul; branco sobre fundo azul). Elemento novo que receba foco deve herdar isso; se o fundo for escuro, troque só a `outline-color`.

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
         tipo='email', ajuda='Ex.: nome@dominio.com', erro=${erroEmail})}"></div>
```

Passe todos os parâmetros; use `null` quando não houver ajuda ou erro. Resultado quando há erro:

```html
<div class="campo">
    <label for="email">E-mail</label>
    <p class="campo__ajuda" id="email-ajuda">Ex.: nome@dominio.com</p>
    <input type="email" id="email" name="email" aria-describedby="email-ajuda email-erro" aria-invalid="true">
    <p class="campo__erro" role="alert" id="email-erro">Informe um e-mail válido.</p>
</div>
```

- `aria-describedby` faz o leitor de tela ler a ajuda e o erro junto com o campo.
- `aria-invalid="true"` só aparece quando há erro.
- `role="alert"` faz o leitor de tela anunciar o erro assim que ele surge.
- As classes (`campo`, `campo__ajuda`, `campo__erro`) estão no `style.css`.
- Por enquanto o fragment cobre `<input>`. Se precisar de `<select>` ou `<textarea>`, siga o mesmo padrão de ids e atributos.

## Como testar

Teste a página rodando localmente (`./mvnw spring-boot:run` e abrir http://localhost:8080). Ferramentas automáticas pegam só parte dos problemas: sempre faça também o teste de teclado.

### 1. Teclado

1. Clique na barra de endereço, depois use **Tab** para andar pela página, **Shift+Tab** para voltar.
2. O primeiro Tab deve mostrar "Pular para o conteúdo"; Enter nele leva ao conteúdo.
3. Confira que a ordem do foco segue a ordem visual e que **todo** link, botão e campo mostra o contorno de foco.
4. **Enter** ativa links e botões; **Espaço** ativa botões; **Esc** fecha o menu de categorias (no mobile).
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
