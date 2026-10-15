# Legacy TV Hub V0.1

Código isolado na branch **legacy-tv-hub-v0.1** do repositório OpenAirTV. A branch principal e o código do OpenAirTV não são modificados.

## Um aplicativo, atualizações sucessivas

- Identificador permanente Android: `br.com.legacytvhub.manager`
- Identificador permanente Samsung Orsay: `LegacyTVHub`
- Versão inicial `0.1.0`, `androidVersionCode: 1`.
- O diagnóstico de controles integra o próprio widget. Não existe aplicativo separado de teste.
- Novas releases precisam manter o `applicationId`, a **mesma assinatura Android** e aumentar o `androidVersionCode`.
- A Samsung Orsay pode manter o widget pelo mesmo ID, mas substituição e preservação de dados dependem de testes na TV real.

## GitHub Actions

O workflow `.github/workflows/legacy-tv-hub.yml` é separado do workflow original do OpenAirTV. Pushes nesta branch compilam o código Android e validam o pacote Orsay. **Não são distribuídos APKs de debug** porque uma assinatura temporária não garante atualização sobre a mesma instalação.

O workflow gera APK de release instalável quando o GitHub tiver a chave permanente via quatro repository secrets:

- `LEGACY_TV_HUB_KEYSTORE_B64`: chave `.jks` codificada com base64.
- `LEGACY_TV_HUB_STORE_PASSWORD`: senha do keystore.
- `LEGACY_TV_HUB_KEY_ALIAS`: alias.
- `LEGACY_TV_HUB_KEY_PASSWORD`: senha da chave.

Crie uma chave **uma única vez** e guarde cópia segura fora do GitHub:
`keytool -genkeypair -keystore legacy-tv-hub-release.jks -alias legacyhub -keyalg RSA -keysize 3072 -validity 12000`

Não coloque o keystore, senhas ou outros segredos no repositório. Depois de configurar os secrets, uma nova atualização desta branch disparará a release assinada.

## Instalação Samsung J4300

O Android oferece `/widgetlist.xml` e `/Widget/LegacyTVHub_<versão>.zip` na LAN para o Smart Hub Orsay sincronizar. Essa sincronização geralmente requer HTTP na **porta 80**, que pode estar inacessível ao Android sem privilégios. O aplicativo informa a falha sem simular uma instalação concluída.

**Ainda não validado na Samsung UN32J4300AGXZD**: precisamos testar descoberta manual, sincronização, primeiro lançamento, uso do controle, atualização e retenção de dados. Apenas dispositivos do próprio usuário ou com autorização do proprietário.
