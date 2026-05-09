package com.example.waspbrowser

object BeeJs {
    val app = """
        const statusEl = document.getElementById("status");
        const logEl = document.getElementById("log");

        function log(msg) {
            console.log(msg);
            logEl.textContent += "\n" + msg;
        }

        function setStatus(msg) {
            statusEl.textContent = msg;
            log("STATUS: " + msg);
        }

        function beeTest() {
            setStatus("JS externo funcionando");
            log("Botão Testar JS clicado com sucesso");
        }

        function beePrepare() {
            setStatus("Estrutura Bee preparada");
            log("Próximo passo: plugar bee_sdk.js");
            log("Depois: plugar WASM");
            log("Depois: integrar wallet/autorização");
        }

        function beeAndroid() {
            if (window.AndroidBee) {
                AndroidBee.toast("Bridge Android funcionando");
                AndroidBee.log("Mensagem enviada do Bee JS externo");
                log("Bridge Android respondeu com sucesso");
            } else {
                log("Bridge Android não encontrada");
            }
        }

        window.addEventListener("load", function () {
            log("BeeActivity carregada");
            log("window.location = " + window.location.href);
            log("JS externo pronto");
        });
    """.trimIndent()
}