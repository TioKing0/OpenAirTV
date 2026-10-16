# Shock TV Legacy • iPad mini 1 / iOS 9

Cliente nativo para o iPad mini de primeira geração, iOS 9.3.5 e ARMv7.
Não é um APK convertido em IPA: a lógica de addons foi adaptada usando UIKit, NSURLSession e AVPlayerViewController.

Funções iniciais: catálogos reais dos manifests Stremio, busca remota nos addons com extra.search,
detalhes e episódios via meta, fontes diretas via stream, AVPlayer para MP4/HLS compatíveis,
favoritos, assistir mais tarde, retomada e fontes personalizadas HTTPS.

Limitações: não inclui motor BitTorrent, infoHash, DRM, cabeçalhos privados de mídia, transcodificação,
nem oferece paridade de recursos com o player Android. Depende de serviços online e disponibilidade das fontes.
Pode ser instalado somente através do método de assinatura/instalação já usado no iPad com jailbreak.

Build Theos: ARCHS=armv7, TARGET=iphone:clang:9.2:9.0, app iPad-only.
Workflow gera um pacote IPA ARMv7 sem assinatura para validação real no dispositivo.

## V0.2.0 — interface exclusiva para iPad mini 1

- Catálogo redesenhado com `UICollectionView` nativo em grade, pôsteres maiores e tipografia legível em 1024×768.
- Destaque inicial alimentado por conteúdo real do addon, sem vídeos de demonstração.
- Navegação de Início, Filmes, Séries e Minha lista em botões de toque de altura 48 pontos. Pesquisa acessível em botão próprio, com teclado nativo e consulta aos manifestos.
- Nova tela de detalhes nativa com pôster, sinopse, Assistir, Curtir e Assistir mais tarde, adaptada à rotação.
- Redes de imagens com cache limitado a 70 capas, redimensionamento para preservar memória do A5 e reaproveitamento de células.
- Mantidos os mecanismos de addons Stremio, busca, episódios, AVPlayer, listas e progresso da V0.1.0.
- ID inalterado `br.com.shocktv.legacy`; `CFBundleVersion` atualizado para 2. O IPA continua sem assinatura, sujeito ao método de instalação no iPad com jailbreak.

O IPA deve ser testado no aparelho físico. iOS 9/AVPlayer não reproduz hashes BitTorrent nem todos os formatos dos addons Android.
