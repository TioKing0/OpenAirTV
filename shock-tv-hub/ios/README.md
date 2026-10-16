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