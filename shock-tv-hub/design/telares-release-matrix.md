
# TELARES — Release Candidate: matriz real de produtos

**Identidade:** TELARES · Seu entretenimento, em qualquer tela. [Figma](https://www.figma.com/design/p4ZL3Fj9meVhE4lVaExxlt) e [Lovable](https://lovable.dev/projects/f8d5f5cb-c140-4f1e-b733-24ae1215d6f1). A marca definitiva ainda depende de validação jurídica e visual.

## Pacotes disponíveis no fluxo da branch telares-release-candidate

| Produto | Formato | Implementação | Limite |
|---|---|---|---|
| Telares TV | APK Android TV | Player Media3 com seleção de streams, busca, listas e D-pad preservados da Shock TV v0.3.6 | Preview com assinatura debug até instalação de keystore permanente |
| Telares Play | IPA iOS 9 ARMv7 | App UIKit/AVPlayer iPad mini 1 com seleção automática de mídias HTTP/HLS e grade | IPA sem assinatura para fluxo do iPad com jailbreak. Precisa validação física |
| Telares Install (Windows) | EXE x64 PyInstaller | Servidor local porta 80 e sincronização Smart Hub Orsay do widget | Apenas Orsay/Smart Hub, não instala em toda Smart TV nem reproduz vídeo |
| Telares Install (Android) | APK Android helper | Servidor Wi-Fi de acesso via navegador da TV | Não realiza instalação Orsay nativa sozinho sem servidor na porta 80 |
| Telares TV Legacy | widget ZIP Samsung Orsay | Diagnóstico e teclado remoto, não é player de streaming | Compatibilidade no modelo físico não confirmada |

**Não implementados e não anunciados como prontos:** Telares Desktop, um player de streaming Windows; versões celulares completas; instaladores universais para Tizen pós-Orsay, webOS e TVs proprietárias sem modo desenvolvedor. Essas plataformas precisam de motores e pacotes próprios. Nenhuma incompatibilidade de TV pode ser solucionada apenas trocando a extensão do arquivo.

**Importante:** preservados Android applicationId `br.com.shocktv`, iOS `br.com.shocktv.legacy`, widget `LegacyTVHub` e Android helper `br.com.legacytvhub.manager` para não quebrar dados ou identidade de atualização. Novo ícone/banner, textos e produto chamam Telares, os IDs técnicos mantêm o nome histórico intencionalmente.

## Bloqueadores de lançamento definitivo

1. Assinatura permanente Android TV, Android helper, iOS e verificações de atualização sem limpar dados. Nunca distribuir arquivo debug como produção.
2. Testes físicos de reprodução longa (iPad A5), D-pad/launcher Android TV, instalação Orsay na Samsung UN32J4300AGXZD, Windows EXE no Windows 32/64 bits suportado. A build do EXE aqui é x64, **não é x86 32-bit**.
3. Verificação de marca TELARES no INPI, URL/domínio e ativos; direitos e licenças dos addons e do conteúdo são responsabilidade do distribuidor e não são adquiridos automaticamente.
4. Após aceitar o design e validar os testes, cortar tag/release estável. RC não significa produto final.
