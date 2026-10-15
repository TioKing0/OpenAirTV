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
