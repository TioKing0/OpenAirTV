# Shock TV Hub Studio — Windows + Android TV

## Produtos independentes
- **Shock TV Android TV**: catálogo Stremio real, navegação remota, filmes/séries e reprodutor Media3 MP4/HLS/DASH. Nenhum item de teste nem vídeo fictício. Usuário configura seus addons.
- **Shock TV Hub Studio para Windows**: servidor HTTP local na porta 80 com instalador de widgets Samsung Orsay. Inclui o widget LegacyTVHub que estamos desenvolvendo. A J4300 exige modo de desenvolvedor e execução de sincronização no controle remoto.

## Limitações reais
O instalador Orsay **não é** instalador universal de Tizen, LG ou Android TV. Essas plataformas requerem adaptadores separados. Não instala apps sem autorização ou explora vulnerabilidades. Os plugins Stremio podem ser fontes de metadados sem vídeos. Só é mostrado como reproduzível um stream HTTP(S) fornecido pelo addon; não são processados torrents, páginas externas ou fontes que exijam cabeçalhos privados.

### APK atualizável
O APK de teste do GitHub pode ser assinado por chaves debug diferentes. Para versões distribuídas comercialmente é necessário um único keystore seguro, ID estável `br.com.shocktv` e versionCode crescente. Não tratar builds debug como updates de produção.

### Executável Windows
Na branch `shock-tv-hub-studio`, o workflow `shock-tv-products.yml` compila o Android TV e cria `ShockTVHubStudio.exe` para Windows com PyInstaller. Ambos são publicados nos artefatos da build.

### Como instalar na Samsung antiga
1. PC e TV no mesmo Wi-Fi; execute `ShockTVHubStudio.exe`.
2. Escolha o IP local correto e clique **Iniciar servidor porta 80**. Permita acesso pela rede privada no firewall do Windows.
3. Abra `http://IP-DO-PC/widgetlist.xml` em outro aparelho na rede para confirmar que está respondendo.
4. Na TV, no Smart Hub, ative a conta de desenvolvimento `develop`, configure o IP do servidor e use **Start User App Sync**.
5. Confirme diretamente na TV a instalação e o funcionamento. O gerenciador não assume sucesso só porque o download ocorreu.

### Android TV
Instale o APK na TV Android. Abra a tela inicial e explore catálogos reais retornados pelo Cinemeta. Para reproduzir, selecione um título e conecte um addon que disponibilize `stream` HTTP(S) de mídia à qual você tenha acesso autorizado, ou use **Reproduzir URL** para uma fonte direta que tenha direito de assistir. Séries consultam seus episódios antes dos streams.

O sistema não disponibiliza nem recomenda catálogos de streams pirateados para um produto comercial. Cada catálogo e addon tem termos e direitos próprios. É preciso licença de redistribuição ou autorização para usar comercialmente seus nomes, ícones e dados.

### Segurança
URLs remotas de addons são código/dados externos não confiáveis. O app impõe timeout e limite por resposta, mas a versão comercial exigirá mais controles, inclusive bloqueio a DNS/IP internos, limites de imagens e cachê.


## Android TV 0.2.0: uso diário

O Shock TV guarda em armazenamento local privado, sem conta obrigatória: Continuar assistindo, último ponto por filme ou episódio, lista Assistir mais tarde, Curtidos com fila contínua para streams já reproduzidos, histórico, busca nos catálogos carregados, remoção de itens e limpeza de histórico. O player salva checkpoints a cada 10 segundos e ao pausar ou fechar, com opção de retomar ou recomeçar. Os dados permanecem após reiniciar a TV e numa atualização do mesmo aplicativo com a mesma assinatura.

A versão 0.2.0 não inclui filmes de demonstração ou itens fictícios. Catálogos de metadados não garantem fontes reproduzíveis. A fila de curtidos só inclui streams HTTP(S) já descobertos, que podem expirar.

## Assinatura para atualizações permanentes

O workflow de Android TV publica um APK debug de avaliação. Para produzir APKs comerciais que atualizam por cima da instalação, configure uma única chave keystore e os secrets SHOCK_TV_KEYSTORE_B64, SHOCK_TV_STORE_PASSWORD, SHOCK_TV_KEY_ALIAS e SHOCK_TV_KEY_PASSWORD. Não envie o arquivo JKS para o GitHub. O workflow publicará então um artefato SIGNED-RELEASE com ID de pacote br.com.shocktv e versionCode crescente. APKs debug não garantem atualização sem reinstalação.


## V0.3.0 — Interface para TV (revisão do vídeo de 15/10)
- Menu lateral com Início, Filmes, Séries, Minha lista, Busca e Fontes.
- Destaque cinematográfico alimentado exclusivamente por metadados reais retornados pelos addons; nenhuma imagem ou vídeo fictício.
- Catálogos normalizados por tipo/ID, removendo duplicações repetidas do Cinemeta e agrupando filmes e séries com seções de gênero quando existe metadado.
- Cards maiores, com borda e ampliação de foco para o controle remoto e páginas de detalhes com ações úteis de reprodução, curtidos, histórico e assistir mais tarde.
- Sem filas vazias no início; as áreas pessoais surgem quando o usuário realmente tem dados.
- Posters carregados com amostragem reduzida, limite por arquivo e cache LRU de memória para TVs com pouca RAM.
- Identificador br.com.shocktv, dados locais e lógica do player preservados. Testes de navegação em TV real ainda necessários.

Referências conceituais: https://github.com/halilozel1903/compose-tv-rails , https://github.com/azad25/homeflix-tv-app e https://developer.android.com/training/tv/playback/compose/lists . Nenhuma marca, asset ou código proprietário da Netflix foi incorporado.

## V0.3.1 — Manifests Stremio predefinidos

Fontes registradas por padrão, com controles de ativação/desativação persistentes no menu Fontes:
- Cinemeta — metadados `https://v3-cinemeta.strem.io/manifest.json`
- FenixFlix — `https://fenixflix.fenixhub.online/manifest.json`
- Torrentio — `https://torrentio.strem.fun/manifest.json`
- Netflix Catalog comunitário — `https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json`
- ThePirateBay+ — `https://thepiratebay-plus.strem.fun/manifest.json`

Esses endereços são armazenados como referências remotas, não incluem filmes, séries ou arquivos de mídia. Eles não implicam disponibilidade ou autorização para reprodução. O aplicativo só reproduz fontes HTTP(S) compatíveis fornecidas pelos addons ou URLs próprias; **não contém motor BitTorrent e não reproduz diretamente torrents**. Verificação de rede ocorre em runtime; addons offline são ignorados no carregamento do catálogo. O usuário pode desativar cada fonte, e suas fontes personalizadas continuam salvas. Para distribuição comercial, verificar licenças e direitos de uso.


## V0.3.2 — Metadados Stremio e episódios

Corrige a navegação de séries reportada após V0.3.1. No protocolo Stremio, uma fonte de `catalog` não é obrigada a oferecer `meta`. O novo resolvedor procura a lista de episódios em todos os addons ativados que declaram `meta` para o tipo e identificador, dando prioridade ao Cinemeta para IDs IMDb. Ele aplica `types` e `idPrefixes` do resource quando definidos, preserva os valores globais como fallback e usa `meta.videos` para determinar os episódios reais (sem conteúdo demonstrativo ou estimado). Uma fonte quebrada deixa de impedir as outras consultas. Mostra uma tela por temporadas em vez de um seletor genérico.

As respostas são verificadas por status HTTP, protocolo HTTPS e formato JSON. Páginas HTML e erros de CDN passam a ser tratados como fonte indisponível, não como dados de episódios. O aplicativo continua sem motor BitTorrent: addons com apenas `infoHash` não disponibilizam mídia reproduzível em Media3. A resposta HTTP(S) de um addon não prova que sua mídia está disponível ou pode ser redistribuída comercialmente.

Referência de protocolo: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md . Também será necessário validar manualmente o fluxo na Android TV física.


## V0.3.3 – Tratamento de falhas HTTP na reprodução

Correção motivada pelo erro real `ERROR_CODE_IO_BAD_HTTP_STATUS` relatado no Android TV.

- O player Media3 inspeciona a exceção HTTP real e informa o status do servidor (por exemplo 401/403/404/416/429/503), distinguindo URL expirada, acesso restrito, arquivo removido e instabilidade.
- Quando ocorre erro, apresenta opções de tentar novamente ou retornar para selecionar outra fonte, em vez de permanecer em tela preta.
- Entradas `streams[].url` são candidatos, não confirmação de vídeo reproduzível. O app diferencia opções BitTorrent `infoHash`, links externos e URLs HTTP(S), evitando enviar hashes ao player.
- Respeita, nas URLs de mídia, os cabeçalhos seguros de requisição declarados em `behaviorHints.proxyHeaders.request`; só envia tokens Authorization/Cookie usando HTTPS. Nenhum desses dados é salvo na biblioteca.
- Força reconhecimento de MIME HLS para URLs `.m3u8` e DASH para `.mpd` quando informado pela extensão.
- Só registra uma reprodução no histórico depois que o Media3 alcança o estado READY. Uma tentativa falha não apaga um progresso anterior.
- Não implementa BitTorrent, burlas de DRM, autorização ou licenças de conteúdo. Nenhuma URL é considerada funcional sem retorno real do servidor.

Nota: compilar não garante funcionamento de URLs de terceiros. O próximo teste na TV deve registrar qual status HTTP aparece quando ocorrer uma falha.

## Android TV v0.3.4 — Seleção inteligente de mídia

- Descoberta das URLs diretas fornecidas pelos addons; recursos com `infoHash` sem URL HTTP(S) continuam não reproduzíveis sem motor BitTorrent.
- Verificador `StreamProbe` testa no máximo 24 opções, com até 4 GETs simultâneos limitados a 2 KB, timeouts curtos e até 5 redirecionamentos. Suporta redirecionamentos HTTP 301/302/303/307/308 para HTTPS, sem rebaixar conexões TLS nem repassar credenciais para outro host.
- HTTP 200 ou 206 com resposta compatível é indício de disponibilidade, **não comprova que codecs, playlists e todos os segmentos serão reproduzidos**. Páginas HTML e erros HTTP são filtrados.
- Escolha automática da melhor opção preliminar priorizando qualidade até 1080p e metadados de português, respeitando a preferência do usuário. As categorias de resolução e idioma derivam dos rótulos enviados pelo addon e podem estar incompletas.
- Dentro do player, menu **Qualidade**, **Idioma** e **Fontes** oferece somente opções aprovadas no teste HTTP. A seleção de faixas de áudio internas usa as faixas identificadas pelo Media3, quando disponíveis.
- Troca de fontes sem retornar à tela do filme, preservando o tempo de reprodução, com tentativa automática de outras fontes ao ocorrer erro.
- `DefaultHttpDataSource` permite redirecionamentos de protocolo quando não há cabeçalhos confidenciais. Cabeçalhos sensíveis nunca são reenviados para domínios de destino diferentes durante a validação e não são persistidos na biblioteca pessoal.
- Não inclui conteúdo de demonstração, não realiza transcodificação de vídeo e não contorna autenticação/DRM. Requer validação de disponibilidade na TV real.

Documentação: https://developer.android.com/reference/androidx/media3/datasource/DefaultHttpDataSource.Factory ; https://stremio.github.io/stremio-addon-sdk/api/responses/stream.html .


## Android TV 0.3.5 — atalho na tela inicial

O app agora possui um banner real 320×180 px com o nome Shock TV e ícone 192×192, gerados pelo Gradle via Java2D e incluídos automaticamente no APK. O AndroidManifest declara `android.software.leanback`, touchscreen e gamepad opcionais, e `MAIN/LEANBACK_LAUNCHER` na atividade que será aberta pelo launcher. O banner e o ícone foram atribuídos tanto no nível da aplicação quanto na atividade.

O GitHub Actions verifica a presença do banner gerado e usa `aapt dump badging` para confirmar que o pacote contém uma atividade lançável pela Android TV. Isso corrige a identificação do APK no menu **Apps**; por decisão do launcher, o usuário ainda pode precisar adicionar o app à fileira de favoritos/home manualmente. Não é possível obrigar launchers de terceiros a fixar um app automaticamente.

Assinatura: este APK ainda é uma compilação de avaliação, e trocas da assinatura debug podem impedir atualizações in-place. Para evitar perda das preferências de fontes, histórico e curtidos, é essencial preparar a assinatura de lançamento estável antes de distribuir a versão comercial.

## Android TV V0.3.6 — Busca integrada e player limpo

Esta versão contém **as duas melhorias combinadas**, sem alterar o motor inteligente de streams da V0.3.5.

- Nova aba Buscar nativa da TV: entrada por teclado remoto, submissão via botão "Pesquisar" e atualização automática após pausa ao digitar.
- Pesquisa `/catalog/{type}/{id}/search={query}.json` em cada addon ativo que anuncia `extra.search`, respeitando as capacidades do manifesto Stremio. Busca títulos além da primeira página dos catálogos exibidos na Home.
- Exibição de resultados reais, capas e sinopses, deduplicados por `type|id`, ordenados por semelhança. Filtros Tudo/Filmes/Séries sem nova requisição. Respostas inválidas não quebram a navegação.
- Proteção contra buscas obsoletas, execução separada da fila de capas e limite de até cinco catálogos por addon em cada pesquisa.
- No player, os botões Qualidade, Idioma e Fontes **desaparecem após 5 segundos**, assim como os controles padrão do Media3. Com o controle remoto, seta para cima abre as opções e coloca o foco no primeiro botão; Menu/Info faz o mesmo.
- OK quando não há menu visível controla play/pause, esquerda/direita buscam 10 segundos quando a interface de controles está oculta. Voltar esconde os controles primeiro e sai do player numa segunda ação.
- Os menus de qualidade, idioma e fontes mantêm os controles acessíveis enquanto estão abertos. A seleção de fontes, histórico, addons e preferências existentes permanece.

A pesquisa retorna somente os conteúdos disponíveis nas fontes conectadas. A existência de um resultado no catálogo não garante que haja stream reproduzível. Como esta é uma build de avaliação, é necessário validar a experiência na Android TV real antes de tratá-la como versão estável.

Protocolo de pesquisa: https://stremio.github.io/stremio-addon-sdk/protocol.html
