# Telares Play Windows

## RC14 · 0.2.11

Motor BitTorrent nativo empacotado, sem servidor/aplicativo externo. OpenSubtitles e suas telas de configuração foram removidos, inclusive na migração dos dados. Legendas internas continuam disponíveis. Consulte `shock-tv-hub/design/RC14.md`; os registros abaixo são históricos.


## RC13 · v0.2.10

Rebuild RC13 com VLC, seleção automática HTTP/HLS/DASH, legendas OpenSubtitles Pro configuráveis, preferência PT-BR e sincronização. Em Fontes, configure o OpenSubtitles no navegador e adicione o manifesto personalizado retornado. BitTorrent usa servidor Stremio HTTP configurado em Fontes ou cliente externo instalado para magnet/.torrent. Não há motor BitTorrent embutido. As seções abaixo descrevem versões históricas; consulte `shock-tv-hub/design/RC13.md` para o escopo atual e limitações.

Aplicativo de streaming separado do Telares Install. Catálogos, busca, episódios e fontes diretas de addons Stremio; reprodução VLC para mídia HTTP/HLS compatível; favoritos, histórico e retomada persistidos em AppData. Builds x86 e x64 usam libVLC 3.0.21 empacotado. Extraia o ZIP inteiro antes de abrir TelaresPlay.exe. Fontes BitTorrent com infoHash, DRM e requisições com cabeçalhos privados não são suportadas. Requer validação funcional em Windows real. A redistribuição de VLC está sujeita às licenças VideoLAN aplicáveis e aos avisos correspondentes.

## Novas categorias V0.2.0 (candidata)

Categorias Anime, Netflix, Max/HBO e Disney+ via metadados declarados dos addons. Gêneros Anime e catálogos Anime que anunciam gênero são consultados remotamente; títulos de plataforma só aparecem quando o addon identifica catálogo/provedor correspondente. Isso é classificação pelo addon, **não** confirmação da assinatura ou disponibilidade oficial no país. Catálogos inexistentes exibem vazio. A identificação oficial de provedores por país requer integrar uma fonte de disponibilidade autorizada, como TMDb (watch_region=BR e com atribuição JustWatch) com chave configurada.

## Categorias RC3

Os menus Filmes, Séries e todos os menus anteriores foram preservados. Categorias complementares: Prime Video, Apple TV+, Paramount+, Crunchyroll, Documentários, Terror, Comédia, Ficção científica, junto de Anime, Netflix, Max/HBO e Disney+. Filtros de gênero usam declarações e metadados dos addons Stremio; categorias por plataforma usam identificação de origem declarada pelo addon. Os dados não atestam assinaturas, direitos de streaming ou disponibilidade regional.

## V0.2.2: canais ao vivo e quatro manifestos

Seção Canais ao vivo na navegação desktop (mouse e teclado). O catálogo reconhece tipos `tv`, `channel`, além dos tipos personalizados mapeados para visualização; URLs originais da especificação Stremio são preservadas na busca dos streams. O VLC reproduz HLS/HTTP quando a fonte responde e oferece alternativas com fallback. Fontes personalizadas, favoritos, histórico e categorias preexistentes são mantidos. A categoria ao vivo não utiliza checkpoints VOD e não permite avançar/retroceder artificialmente. Algumas fontes podem exigir assinatura ou falhar por DRM e codecs.

## RC5

Filtro Anime removido da navegação, sem apagar o gênero Animação e sem modificar a busca normal de filmes e séries. Outras categorias mantidas.

## v0.2.4: novo guia para canais ao vivo

Categorias à esquerda, lista de canais no meio, reprodução VLC embutida à direita. Filmes e Séries permanecem disponíveis e canais IPTV declarados tecnicamente `series` são exibidos exclusivamente no guia de canais. Fontes HLS/HTTP não verificadas por Range podem ser testadas pelo player com fallback. Isso não garante que fontes de terceiros estejam disponíveis.

