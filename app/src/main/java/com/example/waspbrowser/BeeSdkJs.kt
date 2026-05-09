package com.example.waspbrowser

object BeeSdkJs {
    val loader = """
        window.BeeSdkState = {
            loaded: false,
            version: "mock-1",
            ready: false
        };

        function beeLoadSdk() {
            try {
                log("Iniciando carregamento do Bee SDK...");
                
                window.BeeSdkState.loaded = true;
                window.BeeSdkState.ready = true;

                setStatus("Bee SDK mock carregado");
                log("Bee SDK mock carregado com sucesso");
                log("Versão: " + window.BeeSdkState.version);

                if (window.AndroidBee) {
                    AndroidBee.log("Bee SDK mock carregado no WebView");
                }

            } catch (e) {
                setStatus("Erro ao carregar Bee SDK");
                log("ERRO SDK: " + e);
            }
        }

        function beeCheckSdk() {
            if (window.BeeSdkState && window.BeeSdkState.loaded) {
                log("SDK presente e carregado");
                log("ready = " + window.BeeSdkState.ready);
                log("version = " + window.BeeSdkState.version);
                setStatus("SDK validado");
            } else {
                log("SDK ainda não carregado");
                setStatus("SDK ausente");
            }
        }
    """.trimIndent()
}