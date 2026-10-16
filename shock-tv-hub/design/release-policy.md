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
