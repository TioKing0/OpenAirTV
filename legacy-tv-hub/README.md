# Legacy TV Hub V0.1.2: Wi-Fi sem pendrive

Este projeto usa uma branch isolada `legacy-tv-hub-v0.1` do repositório OpenAirTV. Não modifica o projeto original na branch main.

## O que a versão 0.1.2 realmente faz

- Android applicationId: `br.com.legacytvhub.manager` (sem alterações)
- Samsung Orsay widget ID: `LegacyTVHub` (sem alterações)
- Android versionCode: 3, versão 0.1.2.
- O celular serve uma interface local em `http://<IP-do-celular>:8080/`.
- A Samsung J4300 pode abrir essa interface no seu **navegador Web**, estando na mesma rede Wi-Fi.
- A interface foi escrita para navegadores antigos e pode receber comandos do controle (sujeito ao comportamento do navegador da TV).
- Esse modo **não instala aplicativos nativos no Smart Hub**. A sincronização Orsay normalmente usa porta 80 e a porta 8080 não a substitui.
- Não requer root, pen-drive ou computador.
- Para manter o servidor funcionando durante o teste, deixe o aplicativo Android aberto e a tela ligada. Uma futura versão poderá usar um serviço em primeiro plano.
- O pacote widget Orsay pode continuar sendo gerado como artefato interno para desenvolver suporte futuro, mas não é distribuído nesta versão.

## Teste Wi-Fi

1. Conecte a Samsung e o Android à mesma rede Wi-Fi (sem isolamento de clientes).
2. No Android toque em **Iniciar acesso por Wi-Fi** e leia o endereço mostrado.
3. Na televisão, abra **Web Browser**, digite `http://IP:8080/` exatamente e confirme.
4. A página Legacy TV Hub deve aparecer; teste as setas e Enter do controle remoto.
5. Esse sucesso prova somente o acesso via navegador.

## Instalação nativa e atualização

Para instalar de verdade no Smart Hub via Wi-Fi, é preciso disponibilizar o widget Orsay em um servidor acessível na porta 80 (por exemplo, uma ponte externa dedicada), respeitando o protocolo Orsay. Ainda não há instalador nativo Wi-Fi que funcione só com este Android sem root.

**APK Android**: builds debug no GitHub usam chaves temporárias e poderão exigir desinstalar uma versão debug anterior. Nunca afirmar compatibilidade de atualização até a chave de assinatura permanente ser configurada. Configurar quatro secrets: `LEGACY_TV_HUB_KEYSTORE_B64`, `LEGACY_TV_HUB_STORE_PASSWORD`, `LEGACY_TV_HUB_KEY_ALIAS`, `LEGACY_TV_HUB_KEY_PASSWORD`.

## Telares Install (marca unificada)

Nome visual atualizado para Telares Install, mantendo o applicationId e widget ID LegacyTVHub. O pacote Samsung Orsay segue como **protótipo de diagnóstico**, não como serviço de streaming e não como prova de instalação/compatibilidade em todas as gerações de TV. A ferramenta Windows opera um servidor HTTP na porta 80 para sincronização no modelo Orsay compatível; requer teste na TV real. A ferramenta Android auxilia no navegador da TV por Wi-Fi, mas não realiza instalação nativa Orsay sem ponte apropriada. Não promover como instalador universal.
