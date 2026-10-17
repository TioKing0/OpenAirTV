# Telares: política de lançamento multiplataforma

Toda atualização de **experiência de streaming** deve atualizar simultaneamente a configuração e as interfaces correspondentes de **Telares TV Android**, **Telares Play iOS 9** e **Telares Play Windows x86/x64**. Os produtos Telares Install devem ser recompilados no mesmo lote e entregues com versão atual, mesmo quando o recurso não se aplica à instalação de TVs. Toda build do lote precisa passar e os links devem apontar para artefatos conferidos.

Os apps móveis Android/iOS de telefone **ainda não existem** e não podem ser tratados como entregues. Um release não pode fingir compatibilidade em TVs antigas de outras famílias com apenas um widget Orsay.

## Categorias RC2

- Anime: metadado `genres: ["Anime"]`, origem de catálogo declarada 'Anime' ou endpoint Stremio `extra.genre` com opção declarada de Anime.
- Netflix / Max (HBO) / Disney+: apenas provedores ou coleções que o manifesto de addon explicitamente identifica nesses nomes. Exibir resultado vazio quando a fonte não declarou plataforma.
- Não classificar `Animation` genérica como anime. Nem popularidade, ano ou título permitem inferir serviço legal de streaming.
- Separar 'catálogo listado por addon' de 'provedor oficial disponível no Brasil' e de 'fonte reproduzível'. A primeira já funciona; a segunda requer integração de dados de provedores com licença/atribuição (ex.: TMDb/JustWatch) e a terceira depende de mídia e player.
- Em desenvolvimento comercial, sinalizar as marcas de serviços como filtros descritivos sem sugerir afiliação.

## Manifesto de lote

`shock-tv-hub/design/release.json` representa alvos e escopo. `telares-windows-play.yml`, `shock-tv-products.yml` e `shock-tv-legacy-ios.yml` devem ter gatilho para mudanças nesse manifesto. A equipe só promove uma versão à condição de final após testes de reprodução e compatibilidade reais, assinatura permanente, verificações de licença e conformidade de marca.

## RC3: preservação obrigatória de categorias

As novas categorias de plataformas e gêneros são **aditivas**. Início, Filmes, Séries, Buscar, Minha lista e todos os gêneros anteriores (Ação, Comédia, Drama, Suspense, Terror, Animação, Ficção científica, Documentário e Romance) permanecem. Também permanecem Anime, Netflix, Max/HBO e Disney+; adicionamos Prime Video, Apple TV+, Paramount+, Crunchyroll, Documentários, Terror, Comédia e Ficção científica. Sem fonte declarada por addon, mostrar estado vazio, nunca inventar catálogo. A menção a plataformas não significa parceria, licença, assinatura, disponibilidade oficial no Brasil ou garantia de reprodução.

## RC4: Canais ao vivo e manutenção dos filtros existentes

Manifests adicionados (sem redefinir personalizações):
- https://catalogo-iptv-production.up.railway.app/manifest.json
- https://plaviohd.edon.workers.dev/manifest.json
- https://c69e439124f3-tvvio.baby-beamup.club/manifest.json
- https://h-gate.site/dc/manifest.json

As seções Filmes, Séries, gêneros e filtros anteriores são preservadas. Canais ao vivo usa catálogos Stremio de tipo `tv` / `channel` e seleção automática de mídias HTTP/HLS; transmissões ao vivo não usam retomada/seek por tempo gravado. Addons com tipo personalizado (ex. DC Universe) precisam manter seu tipo original para requests `/meta` e `/stream`. Os filtros de plataforma retornam somente fontes identificadas pelos addons. Não inventar títulos onde não há catálogo. O pacote de IPTV Railway e PlavioHD depende de verificação da disponibilidade dos manifests no aparelho; Tvvio anuncia type tv. Uma compilação aprovada não confirma sinal de canal nem autorização de retransmissão.

## RC5 — simplificação solicitada

A categoria **Anime** foi retirada dos filtros de TV, iPad e Windows Play por retornar vazia. O gênero **Animação** continua disponível, assim como Filmes, Séries e todos os outros gêneros. Não apagar listas pessoais, históricos ou títulos anteriormente armazenados; apenas remover o filtro inefetivo. A classificação futura pode ser reconsiderada somente se houver fontes de metadados que alimentem a categoria de forma comprovada.

## RC6: evitar regressão de canais disfarçados de séries

`type` guarda o **tipo do protocolo** Stremio; `displayType` no Android e `_kind` no iOS/Windows guardam a **categoria da interface**. Um canal Telecine publicado em catálogo de tipo `series` deve continuar sendo consultado em `/stream/series/...`, mas aparecer **somente** em Canais ao vivo. Detectar por tipo `tv/channel` e também por proveniência de catálogo declaradamente IPTV. Menus Séries, Filmes e resultados de busca VOD nunca aceitam canais. Manter testes automatizados de regressão e não inferir direito legal de reprodução. Transmissões ao vivo não possuem retomada VOD. iPad e Windows mostram prévia real pelo player; Android TV abre o player em tela cheia a partir do guia de três painéis (prévia de capa estática). O probe HTTP de 2 KB não confirma a decodificabilidade e não deve bloquear todas as tentativas de fonte ao vivo.

## RC7: contrato de classificação e estabilidade

- **Anime** restaurado nas categorias de Android TV, iOS e Windows, além de Animação (que nunca deve ser removida).
- Filmes/Séries/Início não devem listar canais da coleção IPTV/Tvvio, mesmo que o manifesto use `series` como tipo de protocolo; `type` técnico continua separado da classificação `tv` da interface.
- O guia ao vivo mantém esquerda=grupos, centro=canais, direita=prévia. Erro de reprodução em player iOS embutido não deve executar `dismiss` sobre controller filho, mas exibir mensagem dentro da prévia.
- Canais podem rejeitar `Range`: iPad usa URLs HTTP/HLS com falha automática; VLC Windows tenta a fonte antes de filtrar pelo pré-teste; Android TV permite candidatos live diretos após probe inconclusivo.
- Nomes visíveis são Telares, mas identificadores históricos internos ShockTV devem permanecer para permitir atualização dos usuários.
- A prova final requer teste no aparelho com stream autorizado (a compilação isolada não valida codec, token expiração ou disponibilidade real do canal).

## RC9: player Ao vivo dedicado no iPad iOS 9

A interface do Ao vivo agora usa `AVPlayerLayer` embutida na prévia da Telares, com Pausar/Continuar, tentar novamente, HLS primeiro, erros de domínio/código AVFoundation e tela cheia com o controle nativo; o player VOD de filmes/séries foi preservado. O decodificador permanece AVFoundation para ARMv7: **isso não acrescenta suporte a codecs que o iOS 9 não reconhece**. Uma segunda engine baseada em MobileVLCKit foi pesquisada, mas integração ARMv7/SDK/licenças e reprodução real precisam ser verificadas antes de integrar à versão estável. Android TV, Android telefone e Windows foram apenas republicados no mesmo lote, sem alteração no motor de vídeo. Assinatura temporária e addons indisponíveis continuam limitantes.

## RC10 (2026-10-17) — transmissão ao vivo no iPad

O erro NSURLErrorDomain -1008 é NSURLErrorResourceUnavailable, não exclusivamente codec. No player iPad 9 o detector registra o status HTTP quando o AVPlayer disponibiliza erro de transporte; se a única fonte HLS falhar, tenta primeiro as variantes H.264 explicitamente listadas em uma master playlist, com prazo de acesso limitado a 8 s. Não inventar URLs, remover DRM, aceitar downgrade HTTPS→HTTP, armazenar tokens, ou inferir que um canal tem sinal sem teste de hardware. Player de vídeos sob demanda permanece no caminho anterior. Todos os artefatos são rebuilds da RC10; suporte ao streaming dos addons só pode ser confirmado em dispositivo real.

## RC11: separar identidade de vídeo e canal

- **Nenhum gênero prévio é removido**: Filmes, Séries, Anime, Ação, Comédia, Drama, Suspense, Terror, Animação, Ficção científica, Documentário, Romance, Netflix, Max/HBO, Disney+, Prime Video, Apple TV+, Paramount+ e Crunchyroll continuam como antes.
- Um `id` Stremio pode ser compartilhado entre um catálogo `series` e um de canais `series`. Para canais, incluir namespace `live|` no índice de interface, preservando o formato de dados VOD já instalado. Misturar somente por `type|id` era um risco de fonte/metadata incompatível.
- Android TV: guia de três colunas e prévia silenciosa reproduzida pelo Media3 no painel direito. Não instalar segundo player durante a reprodução em tela cheia; liberar ao sair da seção e ao pausar o app. Prévia e player em tela cheia não provam que todos os canais respondam.
- iOS 9: mantém AVPlayer nativo, HLS direto sem bloquear em checagem Range, fallback limitado e tratamento de erro sem URL exposta. `CFBundleDisplayName=Telares Play`; identificador técnico legado `br.com.shocktv.legacy` permanece para atualizações.
- Windows: `Telares Play` mantém a interface de três colunas e VLC integrado; chave de canais separada das entradas sob demanda. Os utilitários `Telares Install` são redistribuídos sem alterar sua finalidade.
- Validação física obrigatória: fonte ao vivo compatível, troca de canal, filtro Série/Filme sem canal, retorno do player à lista, 20 min de reprodução e preservação de favoritos.


## RC12: diagnóstico de transmissão ao vivo no iPad

A falha `NSURLErrorDomain -1008` indica recurso não disponível para o AVPlayer. Sozinha **não prova codec incompatível**. O cliente mostra mensagens separadas para HTTP 401/403 (necessidade de autorização), 404/410 (recurso inexistente), 5xx (falha da origem) e outros erros. Apenas o domínio do servidor é exibido, sem revelar tokens da URL. Na tentativa de identificar variantes HLS, o iPad consulta a playlist com GET normal, sem cabeçalho `Range`, que pode provocar HTTP 416 ou respostas inválidas em alguns provedores. A tentativa só procura alternativas anunciadas pela origem; não remove DRM ou cria sinais inexistentes. Canais com apenas uma fonte ausente continuarão indisponíveis. Preservadas três colunas, categorias, filmes, séries, gêneros e seleção automática VOD. Os utilitários Telares Install acompanham o lote mas não possuem reprodução.
