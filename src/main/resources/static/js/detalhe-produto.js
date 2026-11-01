document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("[data-atualizar-apresentacao]").forEach((seletor) => {
    seletor.addEventListener("change", () => seletor.form.requestSubmit());
  });
});
