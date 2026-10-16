# Shock TV — Contrato visual e de experiência, multiplataforma

**Uma marca, quatro métodos de entrada.** Manter a mesma hierarquia de conteúdo,
vocabulário, capa, ícones, estados do player e paleta em todos os clientes.
Adaptar somente a entrada (controle remoto, toque, mouse/teclado) e o espaço disponível.

## Identidade canônica

- Produto: **SHOCK TV**; marca visual SHOCK (branco) / TV (ciano).
- Fundo principal: `#091420`. Superfície: `#14283A`. Superfície elevada `#1B3447`.
- Destaque / foco ativo: `#68D8E9`. Texto principal: `#F3FAFF`.
- Tipografia: sem serifa do sistema; escala proporcional à distância visual.
- Capas reais 2:3; destaque real com fundo azul-noturno, nunca pôster falso.
- Seções sempre nesta ordem quando houver conteúdo: **Continuar assistindo**,
  **Filmes para descobrir**, **Séries**, **Minha lista**; ocultar trilhos vazios.
- Navegação principal: **Início | Filmes | Séries | Buscar | Minha lista | Fontes**.
- Detalhes: pôster, título, tipo, sinopse, ação primária Assistir, favoritos,
  Assistir mais tarde, temporada/episódio quando aplicável.
- Player: tela limpa, informações e menus sob demanda, Qualidade, Idioma,
  Fontes *somente quando houver alternativas funcionais*. Nunca exibir "player"
  distinto para cada URL encontrada por addon.
- Fonte: descobrir > classificar > verificar HTTP em faixa limitada >
  selecionar automaticamente > iniciar reprodução > tentar alternativa se falhar.
  Sucesso HTTP não comprova decodificação. Não anunciar torrents como mídia
  direta sem um motor adequado.

## Adaptação por dispositivo

| Plataforma | Navegação | Conteúdo | Player |
|---|---|---|---|
| Android TV / Google TV | D-pad, foco ampliado, botão Voltar | trilhos horizontais e painel lateral | controles desaparecem automaticamente |
| iPad iOS 9 ARMv7 | toque, grade adaptativa, orientação | topo com marca + mesmos destinos, pôsteres grandes | AVPlayer nativo, overlay de fontes sob demanda |
| Celular Android/iOS | toque, navegação inferior | mesmos destinos, grade reduzida | controles acessíveis pelo toque |
| Windows (futuro cliente streaming) | mouse, teclado, foco | mesmo painel e grade com layout largo | seleção via mouse/teclado |

**Estado real atual:** Android TV v0.3.6 implementa motor Media3 e interface para TV;
iPad v0.2.1 compartilha paleta/hierarquia e usa AVPlayer para formatos compatíveis.
O projeto Windows atual **é um instalador/sincronizador de widgets Samsung Orsay,
não um cliente de consumo de streaming**. Um cliente de streaming Windows/Android
mobile ainda não foi implementado. Não anunciar paridade inexistente.

## Limitações técnicas do iPad mini 1

- iOS 9.3.5, ARMv7, Apple A5, 512 MB RAM: usar `UICollectionView` reutilizável,
  miniaturas com ImageIO de baixa resolução antes do decode, NSCache com limite.
- Vídeo AVPlayer compatível (MP4/HLS conforme codecs reais). Descartar candidatos
  com infoHash isolado, Matroska, DASH e necessidades de headers privados.
- Conferir sempre status, redirecionamentos, KVO/NSNotification e limpeza do
  player e seus observadores; salvar checkpoint somente após início real.
- O Chrome antigo usa WKWebView/WebKit, não um motor proprietário de codecs no
  iOS 9. Puffin usa processamento remoto; isso não se obtém copiando o IPA.
- Para reproduzir formatos fora do AVPlayer, investigar um pipeline próprio
  licenciado de decodificação/transcodificação e medir uso real no aparelho.
  Não depender de serviços cloud sem arquitetura de privacidade, custo e direitos.

## Qualidade de entrega

- Uma única identidade e terminologia; persistência mantida em upgrade pelo mesmo ID.
- Build iOS: Theos 9.2, deployment target iOS 9.0, armv7; verificar Mach-O e
  `Payload/ShockTV.app` no IPA, sem confundir compilação com teste físico.
- Fluxos para testar manualmente: abrir > buscar > escolher título > encontrar
  episódio > seleção automática > tocar 20 min > fechar > retomar > troca
  de idioma/fonte > voltar; medir jetsam e coletar crash logs se necessário.
