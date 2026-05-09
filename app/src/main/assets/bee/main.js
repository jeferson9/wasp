(function () {
  const statusEl = document.getElementById("status");
  const logEl = document.getElementById("log");
  const btnTest = document.getElementById("btnTest");

  function log(msg) {
    console.log(msg);
    logEl.textContent += msg + "\n";
  }

  function setStatus(msg) {
    statusEl.textContent = msg;
    log("STATUS: " + msg);
  }

  window.addEventListener("load", () => {
    setStatus("HTML local carregado com sucesso");
    log("window.location = " + window.location.href);
    log("JS local ativo");
  });

  btnTest.addEventListener("click", () => {
    log("Botão Testar JS clicado");
    setStatus("JS funcionando dentro do app");
  });
})();