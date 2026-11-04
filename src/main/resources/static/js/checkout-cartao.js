// Confere o formato do cartão antes de enviar (mesmas regras do CartaoForm). Assim um erro de digitação não faz a
// página recarregar e apagar o número e o código de segurança, que o servidor nunca devolve. O servidor confere de novo.
document.addEventListener("DOMContentLoaded", () => {
  const regras = {
    numeroCartao: [(v) => /^\d{13,19}$/.test(v.replace(/[\s-]/g, "")), "Informe o número do cartão, com 13 a 19 números."],
    validadeCartao: [(v) => /^(0[1-9]|1[0-2])\/\d{2}$/.test(v.trim()), "Informe a validade do cartão no formato MM/AA."],
    cvv: [(v) => /^\d{3,4}$/.test(v.trim()), "Informe o código de segurança do cartão, com 3 ou 4 números."],
    nomeImpresso: [(v) => v.trim() !== "", "Informe o nome impresso no cartão."],
  };

  function marcar(campo, mensagem) {
    const idErro = campo.id + "-erro";
    const ligados = (campo.getAttribute("aria-describedby") || "").split(" ").filter((id) => id && id !== idErro);
    let erro = document.getElementById(idErro);
    if (mensagem) {
      if (!erro) {
        erro = document.createElement("p");
        erro.id = idErro;
        erro.className = "campo__erro";
        erro.setAttribute("role", "alert");
        campo.insertAdjacentElement("afterend", erro);
      }
      erro.textContent = mensagem;
      campo.setAttribute("aria-invalid", "true");
      ligados.push(idErro);
    } else {
      if (erro) erro.remove();
      campo.removeAttribute("aria-invalid");
    }
    if (ligados.length) campo.setAttribute("aria-describedby", ligados.join(" "));
    else campo.removeAttribute("aria-describedby");
  }

  document.querySelectorAll("form").forEach((form) => {
    if (!form.querySelector("#numeroCartao")) return;
    form.addEventListener("submit", (evento) => {
      // Na tela do pedido a forma de pagamento é escolhida no próprio formulário; no resumo, o bloco só existe para cartão.
      const metodo = form.querySelector('input[name="metodo"]:checked');
      const pagaComCartao = !form.querySelector('input[name="metodo"]') || (metodo && metodo.value.startsWith("CARTAO_"));
      const salvo = form.querySelector('input[name="cartaoSalvoId"]:checked');
      const usaSalvo = salvo && salvo.value !== "";
      let primeiro = null;
      Object.entries(regras).forEach(([nome, [valido, mensagem]]) => {
        const campo = form.querySelector("#" + nome);
        const exigido = pagaComCartao && (!usaSalvo || nome === "cvv");
        const errado = exigido && !valido(campo.value);
        marcar(campo, errado ? mensagem : null);
        if (errado && !primeiro) primeiro = campo;
      });
      if (primeiro) {
        evento.preventDefault();
        primeiro.focus();
      }
    });
  });
});
