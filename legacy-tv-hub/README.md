# Legacy TV Hub V0.1.1 (Orsay / Samsung J4300)

Android `br.com.legacytvhub.manager` + Samsung widget `LegacyTVHub`. Mantemos o MESMO identificador para atualizar; não existe um aplicativo separado de diagnóstico.

## Correção do erro EACCES do Android

A sincronização IP do Smart Hub Orsay tradicional exige a porta HTTP **80**. Android sem root não permite normalmente criar um servidor local nessa porta. O app agora inicia o diagnóstico na porta **8080** sem root e deixa explícito que isso **não instala o widget pelo Smart Hub**.

## Instalação por USB (a validar na J4300)

A opção **Exportar pacote Orsay para USB** gera um ZIP contendo uma única pasta chamada `LegacyTVHub`, que contém `config.xml`, `index.html`, `widget.info` e arquivos auxiliares. Extraia a pasta `LegacyTVHub` para a raiz de um pendrive FAT32 (não a deixe dentro de outra pasta, nem troque para o formato de pacotes Tizen `userwidget`). Conecte o pendrive à TV e procure o widget no Smart Hub.

Essa alternativa é documentada para algumas TVs Orsay, mas seu sucesso, a persistência após remover o pendrive e a atualização sobre a instalação anterior **ainda exigem teste físico no modelo UN32J4300AGXZD**.

O Android pode exportar o arquivo para o próprio armazenamento ou para um pendrive OTG (quando disponível). Não formata pendrives, não faz root e não usa servidores externos.

## Sobre atualização do APK

Versão Android: `0.1.1`, `versionCode=2`. Para um APK substituir outro, são necessários o **mesmo ID e a mesma chave de assinatura**. A compilação debug do GitHub normalmente gera uma chave temporária; portanto a versão debug V0.1.1 poderá exigir desinstalar o debug V0.1.0 que já estava no celular.

A solução definitiva é configurar os segredos de assinatura permanentes no repositório:
`LEGACY_TV_HUB_KEYSTORE_B64`, `LEGACY_TV_HUB_STORE_PASSWORD`, `LEGACY_TV_HUB_KEY_ALIAS`, `LEGACY_TV_HUB_KEY_PASSWORD`. **Nunca envie ou armazene a chave privada no código público.** A partir do primeiro APK release assinado com essa chave e IDs fixos, atualizações subsequentes podem ser instaladas sem criar aplicativos duplicados.

Branch: `legacy-tv-hub-v0.1` do repositório OpenAirTV. O projeto OpenAirTV em `main` permanece intacto.
