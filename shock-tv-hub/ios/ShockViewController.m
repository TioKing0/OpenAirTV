#import "ShockViewController.h"
#import "ShockDetailViewController.h"
#import "ShockStreamChecker.h"
#import <ImageIO/ImageIO.h>
#import <math.h>

static void *kShockPlayerItemStatusContext = &kShockPlayerItemStatusContext;
#import <AVFoundation/AVFoundation.h>
#import <AVKit/AVKit.h>
#import <CoreMedia/CoreMedia.h>

static NSString * const kDefaultCinemeta = @"https://v3-cinemeta.strem.io/manifest.json";
static NSString * const kDefaultFenix = @"https://fenixflix.fenixhub.online/manifest.json";
static NSString * const kDefaultTorrentio = @"https://torrentio.strem.fun/manifest.json";
static NSString * const kDefaultNetflix = @"https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json";
static NSString * const kDefaultPirate = @"https://thepiratebay-plus.strem.fun/manifest.json";
static NSString * const kDefaultIPTV = @"https://catalogo-iptv-production.up.railway.app/manifest.json";
static NSString * const kDefaultPlavio = @"https://plaviohd.edon.workers.dev/manifest.json";
static NSString * const kDefaultTvvio = @"https://c69e439124f3-tvvio.baby-beamup.club/manifest.json";
static NSString * const kDefaultDC = @"https://h-gate.site/dc/manifest.json";


/** Lightweight reusable portrait card designed specifically for the 1024x768 legacy iPad. */
@interface ShockPosterCell : UICollectionViewCell
@property(nonatomic,strong) UIImageView *art;
@property(nonatomic,strong) UILabel *caption;
@property(nonatomic,strong) UILabel *typeTag;
@property(nonatomic,copy) NSString *imageURL;
@end

@implementation ShockPosterCell
- (instancetype)initWithFrame:(CGRect)frame {
    if((self=[super initWithFrame:frame])){
        self.backgroundColor=[UIColor colorWithRed:.07 green:.13 blue:.20 alpha:1];
        self.layer.cornerRadius=10;
        self.layer.masksToBounds=YES;
        self.art=[[UIImageView alloc]initWithFrame:CGRectZero];
        self.art.contentMode=UIViewContentModeScaleAspectFill;
        self.art.clipsToBounds=YES;
        self.art.backgroundColor=[UIColor colorWithRed:.10 green:.20 blue:.29 alpha:1];
        [self.contentView addSubview:self.art];
        self.caption=[[UILabel alloc]initWithFrame:CGRectZero];
        self.caption.textColor=[UIColor colorWithWhite:.97 alpha:1];
        self.caption.font=[UIFont boldSystemFontOfSize:15];
        self.caption.numberOfLines=2;
        [self.contentView addSubview:self.caption];
        self.typeTag=[[UILabel alloc]initWithFrame:CGRectZero];
        self.typeTag.font=[UIFont systemFontOfSize:11];
        self.typeTag.textColor=[UIColor colorWithRed:.55 green:.78 blue:.87 alpha:1];
        [self.contentView addSubview:self.typeTag];
    }
    return self;
}
- (void)layoutSubviews {
    [super layoutSubviews];
    CGFloat w=self.bounds.size.width,h=self.bounds.size.height;
    CGFloat poster=h-53;
    self.art.frame=CGRectMake(0,0,w,poster);
    self.caption.frame=CGRectMake(9,poster+5,w-18,27);
    self.typeTag.frame=CGRectMake(9,h-19,w-18,14);
}
- (void)prepareForReuse {
    [super prepareForReuse];
    self.art.image=nil;
    self.imageURL=nil;
    self.caption.text=@"";
    self.typeTag.text=@"";
}
@end

@interface ShockViewController () <UITableViewDelegate, UITableViewDataSource, UISearchBarDelegate, UICollectionViewDelegate, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout>
@property(nonatomic,strong) UITableView *table;
@property(nonatomic,strong) UISearchBar *search;
@property(nonatomic,strong) UISegmentedControl *sections;
@property(nonatomic,strong) UILabel *status;
@property(nonatomic,strong) UICollectionView *posterGrid;
@property(nonatomic,strong) UIView *hero;
@property(nonatomic,strong) UIImageView *heroArt;
@property(nonatomic,strong) UILabel *heroTitle;
@property(nonatomic,strong) UILabel *heroSummary;
@property(nonatomic,strong) UIView *navigationStrip;
@property(nonatomic,strong) UIButton *searchToggle;
@property(nonatomic,strong) NSCache *imageCache;
@property(nonatomic,strong) NSMutableSet *pendingImages;
@property(nonatomic,strong) NSDictionary *featuredItem;
@property(nonatomic,strong) NSDictionary *detailItem;
@property(nonatomic,strong) UIViewController *detailController;
@property(nonatomic,assign) BOOL searching;

@property(nonatomic,strong) NSMutableArray *sources;
@property(nonatomic,strong) NSMutableDictionary *manifests;
@property(nonatomic,strong) NSMutableArray *catalogItems;
@property(nonatomic,strong) NSMutableArray *results;
@property(nonatomic,strong) NSMutableDictionary *posterCache;
@property(nonatomic,strong) NSMutableSet *knownIDs;
@property(nonatomic,copy) NSString *filter;
@property(nonatomic,copy) NSString *categoryFilter;
@property(nonatomic,copy) NSString *mode;
@property(nonatomic,copy) NSString *currentTerm;
@property(nonatomic,strong) NSDictionary *activeSeries;
@property(nonatomic,strong) NSArray *allSeriesEpisodes;
@property(nonatomic,strong) NSArray *episodeSeasonOptions;
@property(nonatomic,strong) NSNumber *selectedEpisodeSeason;
@property(nonatomic,strong) AVPlayer *moviePlayer;
@property(nonatomic,strong) AVPlayerViewController *playerController;
@property(nonatomic,strong) id timeObserver;
@property(nonatomic,strong) NSMutableDictionary *activePlayback;
@property(nonatomic,assign) NSInteger taskGeneration;
@property(nonatomic,assign) NSInteger pending;
@property(nonatomic,strong) ShockStreamChecker *streamChecker;
@property(nonatomic,strong) NSArray *workingStreams;
@property(nonatomic,strong) NSDictionary *selectedMedia;
@property(nonatomic,strong) AVPlayerItem *observedPlayerItem;
@property(nonatomic,strong) UIButton *playerSourceButton;
@property(nonatomic,assign) NSInteger playingSourceIndex;
@property(nonatomic,assign) NSUInteger streamRequestGeneration;
@property(nonatomic,assign) BOOL liveVariantRecoveryAttempted;
@property(nonatomic,assign) BOOL liveVariantRecoveryPending;
@property(nonatomic,assign) BOOL hasPlayerObservation;
@property(nonatomic,assign) BOOL playbackStarted;
@property(nonatomic,assign) BOOL closingPlayer;
@property(nonatomic,assign) double seekAfterReady;
@property(nonatomic,assign) double lastPlaybackPosition;
@property(nonatomic,strong) UIView *liveGuide;
@property(nonatomic,strong) UITableView *liveGroups;
@property(nonatomic,strong) UITableView *liveChannels;
@property(nonatomic,strong) UIView *liveScreen;
@property(nonatomic,strong) UILabel *liveTitle;
@property(nonatomic,strong) UILabel *liveMessage;
@property(nonatomic,strong) UIButton *liveFullscreen;
@property(nonatomic,strong) UIButton *liveRetryButton;
@property(nonatomic,strong) UIButton *livePauseButton;
@property(nonatomic,strong) AVPlayerLayer *liveVideoLayer;
@property(nonatomic,copy) NSString *liveLastFailure;
@property(nonatomic,assign) NSUInteger liveAttemptCount;
@property(nonatomic,copy) NSString *liveGroup;
@property(nonatomic,strong) NSArray *liveGroupNames;
@property(nonatomic,strong) NSArray *liveFiltered;
@property(nonatomic,strong) NSDictionary *liveSelection;
@property(nonatomic,strong) AVPlayerViewController *liveFullscreenController;
@end

@implementation ShockViewController

- (UIColor *)dark { return [UIColor colorWithRed:0.037 green:0.071 blue:0.12 alpha:1]; }
- (UIColor *)accent { return [UIColor colorWithRed:0.39 green:0.84 blue:0.91 alpha:1]; }
- (NSArray *)defaultSources {
    return @[kDefaultCinemeta,kDefaultFenix,kDefaultNetflix,kDefaultTorrentio,kDefaultPirate,kDefaultIPTV,kDefaultPlavio,kDefaultTvvio,kDefaultDC];
}
- (NSString *)str:(id)value {
    return [value isKindOfClass:[NSString class]] ? value : @"";
}
- (NSArray *)arr:(id)value {
    return [value isKindOfClass:[NSArray class]] ? value : @[];
}
- (NSDictionary *)dict:(id)value {
    return [value isKindOfClass:[NSDictionary class]] ? value : @{};
}
- (void)viewDidLoad {
    [super viewDidLoad];
    self.title = @"TELARES";
    self.view.backgroundColor = [self dark];
    self.navigationController.navigationBar.barStyle = UIBarStyleBlack;
    self.navigationController.navigationBar.barTintColor = [self dark];
    self.navigationController.navigationBar.tintColor = [self accent];
    self.navigationController.navigationBar.translucent = NO;
    self.mode = @"catalog";
    self.filter = @"Início";
    self.currentTerm = @"";
    self.manifests = [NSMutableDictionary dictionary];
    self.posterCache = [NSMutableDictionary dictionary];
    self.catalogItems = [NSMutableArray array];
    self.results = [NSMutableArray array];
    self.knownIDs = [NSMutableSet set];
    self.sources = [NSMutableArray arrayWithArray:[self defaultSources]];
    NSArray *saved = [[NSUserDefaults standardUserDefaults] arrayForKey:@"shock_sources_v1"];
    if (saved) {
        [self.sources removeAllObjects];
        for (id item in saved) {
            NSString *url = [self str:item];
            if ([url hasPrefix:@"https://"] && [url hasSuffix:@"/manifest.json"] &&
                ![self.sources containsObject:url] && self.sources.count < 30)
                [self.sources addObject:url];
        }
    }
    // Preserve all custom sources while registering new bundled addons on upgrades.
    for(NSString *item in [self defaultSources]){
        if(![self.sources containsObject:item]&&self.sources.count<30)
            [self.sources addObject:item];
    }
    // Addon management remains implemented but is not a visible toolbar button.
    // During playback the automatic stream selector is the default experience.
    self.navigationItem.rightBarButtonItem=nil;

    self.imageCache=[[NSCache alloc]init];
    self.imageCache.countLimit=38;
    self.imageCache.totalCostLimit=7*1024*1024;
    self.pendingImages=[NSMutableSet set];

    self.navigationStrip=[[UIView alloc]initWithFrame:CGRectZero];
    self.navigationStrip.backgroundColor=[UIColor colorWithRed:.057 green:.107 blue:.167 alpha:1];
    self.navigationStrip.layer.cornerRadius=12;
    [self.view addSubview:self.navigationStrip];
    NSArray *tabs=@[@"Início",@"Filmes",@"Séries",@"Minha lista",@"Categorias",@"Ao vivo"];
    for(NSUInteger index=0;index<tabs.count;index++){
        UIButton *button=[UIButton buttonWithType:UIButtonTypeCustom];
        button.tag=100+(NSInteger)index;
        [button setTitle:tabs[index] forState:UIControlStateNormal];
        button.titleLabel.font=[UIFont boldSystemFontOfSize:15];
        [button setTitleColor:[UIColor colorWithWhite:.77 alpha:1] forState:UIControlStateNormal];
        [button addTarget:self action:@selector(tapTab:) forControlEvents:UIControlEventTouchUpInside];
        button.layer.cornerRadius=9;
        [self.navigationStrip addSubview:button];
    }
    self.searchToggle=[UIButton buttonWithType:UIButtonTypeCustom];
    [self.searchToggle setTitle:@"⌕  Buscar" forState:UIControlStateNormal];
    [self.searchToggle setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    self.searchToggle.titleLabel.font=[UIFont boldSystemFontOfSize:15];
    self.searchToggle.backgroundColor=[UIColor colorWithRed:.09 green:.31 blue:.39 alpha:1];
    self.searchToggle.layer.cornerRadius=10;
    [self.searchToggle addTarget:self action:@selector(toggleSearch) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:self.searchToggle];

    self.search=[[UISearchBar alloc]initWithFrame:CGRectZero];
    self.search.delegate=self;
    self.search.barTintColor=[self dark];
    self.search.tintColor=[self accent];
    self.search.placeholder=@"Qual filme ou série você procura?";
    self.search.autocapitalizationType=UITextAutocapitalizationTypeNone;
    self.search.returnKeyType=UIReturnKeySearch;
    self.search.hidden=YES;
    [self.view addSubview:self.search];

    self.hero=[[UIView alloc]initWithFrame:CGRectZero];
    self.hero.backgroundColor=[UIColor colorWithRed:.075 green:.18 blue:.26 alpha:1];
    self.hero.layer.cornerRadius=14;
    self.hero.clipsToBounds=YES;
    UITapGestureRecognizer *tap=[[UITapGestureRecognizer alloc]initWithTarget:self action:@selector(openFeatured)];
    [self.hero addGestureRecognizer:tap];
    [self.view addSubview:self.hero];

    self.heroArt=[[UIImageView alloc]initWithFrame:CGRectZero];
    self.heroArt.backgroundColor=[UIColor colorWithRed:.10 green:.23 blue:.31 alpha:1];
    self.heroArt.clipsToBounds=YES;
    self.heroArt.contentMode=UIViewContentModeScaleAspectFill;
    [self.hero addSubview:self.heroArt];
    self.heroTitle=[[UILabel alloc]initWithFrame:CGRectZero];
    self.heroTitle.font=[UIFont boldSystemFontOfSize:26];
    self.heroTitle.textColor=[UIColor whiteColor];
    self.heroTitle.numberOfLines=2;
    [self.hero addSubview:self.heroTitle];
    self.heroSummary=[[UILabel alloc]initWithFrame:CGRectZero];
    self.heroSummary.font=[UIFont systemFontOfSize:14];
    self.heroSummary.textColor=[UIColor colorWithRed:.70 green:.83 blue:.90 alpha:1];
    self.heroSummary.numberOfLines=3;
    [self.hero addSubview:self.heroSummary];

    UICollectionViewFlowLayout *layout=[[UICollectionViewFlowLayout alloc]init];
    layout.minimumInteritemSpacing=14;
    layout.minimumLineSpacing=17;
    layout.sectionInset=UIEdgeInsetsMake(10,19,20,19);
    self.posterGrid=[[UICollectionView alloc]initWithFrame:CGRectZero collectionViewLayout:layout];
    self.posterGrid.delegate=self;
    self.posterGrid.dataSource=self;
    self.posterGrid.backgroundColor=[self dark];
    self.posterGrid.alwaysBounceVertical=YES;
    [self.posterGrid registerClass:[ShockPosterCell class] forCellWithReuseIdentifier:@"shock.card"];
    [self.view addSubview:self.posterGrid];

    // Lists for series episodes and sources remain native, readable touch lists.
    self.table=[[UITableView alloc]initWithFrame:CGRectZero style:UITableViewStylePlain];
    self.table.delegate=self;
    self.table.dataSource=self;
    self.table.rowHeight=76;
    self.table.hidden=YES;
    self.table.backgroundColor=[self dark];
    self.table.separatorColor=[UIColor colorWithWhite:.22 alpha:1];
    self.table.tableFooterView=[[UIView alloc]initWithFrame:CGRectZero];
    [self.view addSubview:self.table];


    // Three-pane channel guide exists only on Ao vivo, never in movie/series screens.
    self.liveGuide=[[UIView alloc]initWithFrame:CGRectZero];
    self.liveGuide.backgroundColor=[self dark];self.liveGuide.hidden=YES;
    [self.view addSubview:self.liveGuide];
    self.liveGroups=[[UITableView alloc]initWithFrame:CGRectZero style:UITableViewStylePlain];
    self.liveChannels=[[UITableView alloc]initWithFrame:CGRectZero style:UITableViewStylePlain];
    for(UITableView *list in @[self.liveGroups,self.liveChannels]){
        list.delegate=self;list.dataSource=self;list.rowHeight=61;
        list.backgroundColor=[UIColor colorWithRed:.08 green:.15 blue:.22 alpha:1];
        list.separatorColor=[UIColor colorWithWhite:.22 alpha:1];
        list.tableFooterView=[[UIView alloc]initWithFrame:CGRectZero];
        list.layer.cornerRadius=10;
        [self.liveGuide addSubview:list];
    }
    self.liveScreen=[[UIView alloc]initWithFrame:CGRectZero];
    self.liveScreen.backgroundColor=[UIColor blackColor];
    self.liveScreen.layer.cornerRadius=10;self.liveScreen.clipsToBounds=YES;
    [self.liveGuide addSubview:self.liveScreen];
    self.liveTitle=[[UILabel alloc]initWithFrame:CGRectZero];
    self.liveTitle.font=[UIFont boldSystemFontOfSize:16];
    self.liveTitle.textColor=[self accent];
    self.liveTitle.text=@"AO VIVO · PRÉVIA";self.liveTitle.numberOfLines=2;
    [self.liveGuide addSubview:self.liveTitle];
    self.liveMessage=[[UILabel alloc]initWithFrame:CGRectZero];
    self.liveMessage.text=@"Selecione uma categoria e depois um canal.";
    self.liveMessage.textColor=[UIColor colorWithWhite:.80 alpha:1];
    self.liveMessage.font=[UIFont systemFontOfSize:14];
    self.liveMessage.numberOfLines=4;
    [self.liveGuide addSubview:self.liveMessage];
    self.liveFullscreen=[UIButton buttonWithType:UIButtonTypeCustom];
    [self.liveFullscreen setTitle:@"Tela cheia" forState:UIControlStateNormal];
    self.liveFullscreen.titleLabel.font=[UIFont boldSystemFontOfSize:15];
    self.liveFullscreen.backgroundColor=[UIColor colorWithRed:.12 green:.36 blue:.46 alpha:1];
    self.liveFullscreen.layer.cornerRadius=9;
    [self.liveFullscreen addTarget:self action:@selector(openLiveFullscreen)
       forControlEvents:UIControlEventTouchUpInside];
    self.liveFullscreen.hidden=YES;
    [self.liveGuide addSubview:self.liveFullscreen];
    self.livePauseButton=[UIButton buttonWithType:UIButtonTypeCustom];
    [self.livePauseButton setTitle:@"Pausar" forState:UIControlStateNormal];
    self.livePauseButton.titleLabel.font=[UIFont boldSystemFontOfSize:14];
    self.livePauseButton.backgroundColor=[UIColor colorWithRed:.12 green:.36 blue:.46 alpha:1];
    self.livePauseButton.layer.cornerRadius=9;
    [self.livePauseButton addTarget:self action:@selector(toggleLivePlayback)
        forControlEvents:UIControlEventTouchUpInside];
    self.livePauseButton.hidden=YES;
    [self.liveGuide addSubview:self.livePauseButton];
    self.liveRetryButton=[UIButton buttonWithType:UIButtonTypeCustom];
    [self.liveRetryButton setTitle:@"Tentar novamente" forState:UIControlStateNormal];
    self.liveRetryButton.titleLabel.font=[UIFont boldSystemFontOfSize:14];
    self.liveRetryButton.backgroundColor=[UIColor colorWithRed:.12 green:.36 blue:.46 alpha:1];
    self.liveRetryButton.layer.cornerRadius=9;
    [self.liveRetryButton addTarget:self action:@selector(retryLiveSelectedChannel)
        forControlEvents:UIControlEventTouchUpInside];
    self.liveRetryButton.hidden=YES;
    [self.liveGuide addSubview:self.liveRetryButton];
    self.liveGroupNames=@[@"Todos os canais"];self.liveFiltered=@[];
    self.status=[[UILabel alloc]initWithFrame:CGRectZero];
    self.status.font=[UIFont systemFontOfSize:13];
    self.status.textColor=[UIColor colorWithWhite:.73 alpha:1];
    self.status.textAlignment=NSTextAlignmentLeft;
    self.status.text=@"Consultando catálogos reais...";
    [self.view addSubview:self.status];
    [self setSelectedTab];
    [self loadCatalogs];
}
- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    CGFloat width=self.view.bounds.size.width,height=self.view.bounds.size.height;
    BOOL landscape=width>height;
    CGFloat margin=landscape?23:17;
    CGFloat navTop=12,navHeight=48,searchW=landscape?142:106;
    self.searchToggle.frame=CGRectMake(width-searchW-margin,navTop,searchW,navHeight);
    self.navigationStrip.frame=CGRectMake(margin,navTop,
        width-margin*3-searchW,navHeight);
    CGFloat tabW=self.navigationStrip.bounds.size.width/6.0;
    for(UIView *control in self.navigationStrip.subviews)
        if([control isKindOfClass:[UIButton class]])
            control.frame=CGRectMake((control.tag-100)*tabW+3,3,tabW-6,navHeight-6);
    CGFloat offset=navTop+navHeight+13;
    self.search.frame=CGRectMake(margin-9,offset,width-margin*2+18,53);
    if(self.searching)offset+=57;
    BOOL isSpecial=[self.mode isEqualToString:@"episodes"]||[self.mode isEqualToString:@"streams"];
    BOOL showHero=!isSpecial&&!self.searching&&[self.filter isEqualToString:@"Início"]&&self.results.count>0;
    self.hero.hidden=!showHero;
    if(showHero){
        CGFloat heroHeight=landscape?174:150;
        self.hero.frame=CGRectMake(margin,offset,width-margin*2,heroHeight);
        CGFloat artW=landscape?144:116;
        self.heroArt.frame=CGRectMake(0,0,artW,heroHeight);
        self.heroTitle.frame=CGRectMake(artW+20,18,width-artW-margin*2-42,landscape?73:55);
        self.heroSummary.frame=CGRectMake(artW+20,landscape?97:75,width-artW-margin*2-42,64);
        offset+=heroHeight+13;
    }
    CGFloat bottom=height-39;
    CGRect body=CGRectMake(0,offset,width,MAX(80,bottom-offset));
    BOOL live=[self showingLiveGuide];
    self.posterGrid.frame=body;
    self.table.frame=body;
    self.table.hidden=!isSpecial;
    self.posterGrid.hidden=isSpecial||live;
    self.liveGuide.hidden=!live;
    if(live){
        self.liveGuide.frame=CGRectMake(margin,offset,width-2*margin,MAX(80,bottom-offset));
        CGFloat w=self.liveGuide.bounds.size.width,h=self.liveGuide.bounds.size.height,gap=10;
        CGFloat left=landscape?MIN(185,w*.22):MIN(140,w*.22);
        CGFloat mid=landscape?MIN(280,w*.31):MIN(186,w*.31);
        CGFloat previewX=left+mid+gap*2,previewW=MAX(110,w-previewX);
        self.liveGroups.frame=CGRectMake(0,0,left,h);
        self.liveChannels.frame=CGRectMake(left+gap,0,mid,h);
        self.liveTitle.frame=CGRectMake(previewX+5,2,previewW-10,44);
        CGFloat screenH=MAX(100,MIN(previewW*9.0/16.0,h*.49));
        self.liveScreen.frame=CGRectMake(previewX,52,previewW,screenH);
        if(self.playerController.parentViewController==self)
            self.playerController.view.frame=self.liveScreen.bounds;
        self.liveMessage.frame=CGRectMake(previewX+5,screenH+64,previewW-10,83);
        self.liveFullscreen.frame=CGRectMake(previewX+5,screenH+150,MIN(140,(previewW-15)/2),43);
        self.livePauseButton.frame=CGRectMake(previewX+12+MIN(140,(previewW-15)/2),
            screenH+150,MIN(100,(previewW-15)/2),43);
        self.liveRetryButton.frame=CGRectMake(previewX+5,screenH+150,MIN(200,previewW-10),43);
        self.liveVideoLayer.frame=self.liveScreen.bounds;
    }
    self.status.frame=CGRectMake(margin,height-37,width-margin*2,26);
    [self.posterGrid.collectionViewLayout invalidateLayout];
}

- (BOOL)showingLiveGuide {
    return [self.mode isEqualToString:@"catalog"]&&!self.searching&&
           [self.filter isEqualToString:@"Ao vivo"];
}
- (NSString *)liveGroupOf:(NSDictionary *)item {
    NSString *title=[self normalizedCategory:[self str:item[@"name"]]];
    NSString *origin=[self normalizedCategory:[self str:item[@"_group"]]];
    if([title containsString:@"telecine"])return @"Telecine";
    if([title containsString:@"hbo"]||[title containsString:@"cinemax"])return @"HBO / Max";
    if([title containsString:@"sportv"]||[title containsString:@"espn"]||
       [title containsString:@"premiere"]||[title containsString:@"tnt sports"]||
       [origin containsString:@"esporte"])return @"Esportes";
    if([origin containsString:@"aberto"]||[title containsString:@"globo"]||
       [title containsString:@"record"]||[title containsString:@"sbt"]||
       [title containsString:@"rede tv"]||[title containsString:@"tv cultura"])
        return @"Canais abertos";
    if([origin containsString:@"noticia"]||[title containsString:@"news"]||
       [title containsString:@"cnn"])return @"Notícias";
    if([origin containsString:@"infantil"]||[title containsString:@"cartoon"])return @"Infantil";
    if([origin containsString:@"religioso"])return @"Religiosos";
    if([origin containsString:@"filmes"]||[origin containsString:@"series"])return @"Filmes e séries";
    if([origin containsString:@"entretenimento"])return @"Entretenimento";
    return @"Outros canais";
}
- (void)updateLiveGuide {
    if(![self showingLiveGuide])return;
    NSMutableArray *groups=[NSMutableArray arrayWithObject:@"Todos os canais"];
    for(NSDictionary *item in self.results){
        NSString *group=[self liveGroupOf:item];
        if(![groups containsObject:group])[groups addObject:group];
    }
    self.liveGroupNames=groups;
    if(!self.liveGroup||![groups containsObject:self.liveGroup])self.liveGroup=@"Todos os canais";
    NSMutableArray *filtered=[NSMutableArray array];
    for(NSDictionary *item in self.results)
        if([self.liveGroup isEqualToString:@"Todos os canais"]||
           [[self liveGroupOf:item] isEqualToString:self.liveGroup])
            [filtered addObject:item];
    self.liveFiltered=filtered;
    [self.liveGroups reloadData];[self.liveChannels reloadData];
    self.status.text=[NSString stringWithFormat:@"%lu canais ao vivo · %@",
        (unsigned long)self.results.count,self.liveGroup];
}
- (void)stopLivePreview {
    self.streamRequestGeneration++;
    self.liveVariantRecoveryPending=NO;
    [self.streamChecker cancel];self.streamChecker=nil;
    [self cleanCurrentPlayer];
    if(self.playerController.parentViewController==self){
        [self.playerController willMoveToParentViewController:nil];
        [self.playerController.view removeFromSuperview];
        [self.playerController removeFromParentViewController];
    }
    self.playerController=nil;self.workingStreams=nil;self.activePlayback=nil;
    self.liveSelection=nil;self.liveFullscreen.hidden=YES;
    self.liveFullscreenController=nil;
    self.liveRetryButton.hidden=YES;self.livePauseButton.hidden=YES;
    self.liveLastFailure=nil;self.liveAttemptCount=0;
    self.liveVariantRecoveryAttempted=NO;
    self.liveVariantRecoveryPending=NO;
    self.liveMessage.text=@"Selecione um canal para iniciar a prévia.";
}
- (void)openLiveFullscreen {
    if(!self.moviePlayer||self.presentedViewController)return;
    AVPlayerViewController *full=[[AVPlayerViewController alloc]init];
    full.player=self.moviePlayer;full.showsPlaybackControls=YES;
    self.liveFullscreenController=full;
    // Avoid competing preview/fullscreen renderers on iOS 9.
    self.liveVideoLayer.player=nil;
    [self presentViewController:full animated:YES completion:nil];
}
- (void)retryLiveSelectedChannel {
    if(self.liveSelection)[self playLiveSelection:self.liveSelection];
}
- (void)toggleLivePlayback {
    if(!self.moviePlayer)return;
    if(self.moviePlayer.rate>0){
        [self.moviePlayer pause];
        [self.livePauseButton setTitle:@"Continuar" forState:UIControlStateNormal];
    }else{
        [self.moviePlayer play];
        [self.livePauseButton setTitle:@"Pausar" forState:UIControlStateNormal];
    }
}
- (void)playLiveSelection:(NSDictionary *)item {
    if(!item)return;
    [self.streamChecker cancel];
    if(self.moviePlayer)[self cleanCurrentPlayer];
    self.liveSelection=item;
    self.liveTitle.text=[NSString stringWithFormat:@"AO VIVO · %@",[self str:item[@"name"]]];
    self.liveMessage.text=@"Procurando transmissão...";
    self.liveLastFailure=nil;self.liveAttemptCount=0;
    self.liveFullscreen.hidden=YES;self.liveRetryButton.hidden=YES;
    self.livePauseButton.hidden=YES;
    [self resolveStreams:item];
}
- (void)setSelectedTab {
    NSArray *names=@[@"Início",@"Filmes",@"Séries",@"Minha lista",@"Categorias",@"Ao vivo"];
    for(UIView *view in self.navigationStrip.subviews){
        if(![view isKindOfClass:[UIButton class]])continue;
        UIButton *button=(UIButton *)view;
        NSInteger index=button.tag-100;
        if(index<0||index>=names.count)continue;
        BOOL chosen=(!self.searching&&([self.filter isEqualToString:names[(NSUInteger)index]]||
           (index==4&&self.categoryFilter.length>0&&![self.filter isEqualToString:@"Ao vivo"])));
        button.backgroundColor=chosen?
            [UIColor colorWithRed:.14 green:.39 blue:.49 alpha:1]:[UIColor clearColor];
        [button setTitleColor:chosen?[UIColor whiteColor]:
            [UIColor colorWithWhite:.75 alpha:1] forState:UIControlStateNormal];
    }
}
- (void)tapTab:(UIButton *)button {
    NSArray *names=@[@"Início",@"Filmes",@"Séries",@"Minha lista",@"Categorias",@"Ao vivo"];
    NSInteger idx=button.tag-100;
    if(idx<0||idx>=names.count)return;
    if([self showingLiveGuide]&&idx!=5)[self stopLivePreview];
    if(idx==4){[self showCategoryChooser:button];return;}
    if(idx==5){
        self.searching=NO;self.search.hidden=YES;[self.search resignFirstResponder];
        self.currentTerm=@"";self.search.text=@"";
        self.categoryFilter=@"Canais ao vivo";self.filter=@"Ao vivo";
        [self setSelectedTab];[self loadCategory:@"Canais ao vivo"];return;
    }
    self.searching=NO;
    self.search.hidden=YES;
    [self.search resignFirstResponder];
    self.currentTerm=@"";
    self.search.text=@"";
    self.filter=names[(NSUInteger)idx];
    if(self.categoryFilter.length){
        self.categoryFilter=nil;
        [self setSelectedTab];
        [self loadCatalogs];
    }else{
        [self setSelectedTab];
        [self refreshList];
    }
}
- (NSString *)normalizedCategory:(NSString *)source {
    NSString *folded=[[self str:source] stringByFoldingWithOptions:NSDiacriticInsensitiveSearch
        locale:[NSLocale localeWithLocaleIdentifier:@"pt_BR"]];
    folded=folded.lowercaseString;
    return [[folded stringByReplacingOccurrencesOfString:@"-" withString:@" "]
        stringByReplacingOccurrencesOfString:@"_" withString:@" "];
}
- (NSArray *)categoryAliases:(NSString *)tag {
    if([tag isEqualToString:@"Canais ao vivo"])return @[@"tv",@"channel",@"ao vivo",@"live"];
    if([tag isEqualToString:@"Anime"])return @[@"anime",@"japanese animation",@"animacao japonesa"];
    if([tag isEqualToString:@"Documentários"])return @[@"documentario",@"documentary",@"documentaries",@"documental"];
    if([tag isEqualToString:@"Terror"])return @[@"terror",@"horror"];
    if([tag isEqualToString:@"Comédia"])return @[@"comedia",@"comedy"];
    if([tag isEqualToString:@"Ficção científica"])return @[@"ficcao cientifica",@"science fiction",@"sci fi",@"scifi"];
    if([tag isEqualToString:@"Netflix"])return @[@"netflix"];
    if([tag isEqualToString:@"Max / HBO"])return @[@"hbo",@"hbo max",@"hbomax",@"max originals",@"max catalog",@"max tv"];
    if([tag isEqualToString:@"Disney+"])return @[@"disney+",@"disney plus",@"disneyplus"];
    if([tag isEqualToString:@"Prime Video"])return @[@"prime video",@"primevideo",@"amazon prime"];
    if([tag isEqualToString:@"Apple TV+"])return @[@"apple tv+",@"apple tv plus",@"appletv+",@"apple originals"];
    if([tag isEqualToString:@"Paramount+"])return @[@"paramount+",@"paramount plus",@"paramountplus"];
    if([tag isEqualToString:@"Crunchyroll"])return @[@"crunchyroll"];
    return @[];
}
- (BOOL)genreCategory:(NSString *)tag {
    return [@[@"Anime",@"Documentários",@"Terror",@"Comédia",@"Ficção científica"] containsObject:tag];
}
- (BOOL)category:(NSString *)tag fromProvider:(NSString *)provider catalog:(NSString *)catalog
    catalogId:(NSString *)ident genres:(NSArray *)genres {
    NSString *origin=[self normalizedCategory:[NSString stringWithFormat:@"%@ %@ %@",
        provider?:@"",catalog?:@"",ident?:@""]];
    for(NSString *keyword in [self categoryAliases:tag]){
        NSString *word=[self normalizedCategory:keyword];
        if([origin containsString:word])return YES;
        if([self genreCategory:tag]){
            for(id name in genres)if([[self normalizedCategory:[self str:name]] containsString:word])
                return YES;
        }
    }
    return NO;
}
- (NSString *)genreExtra:(NSDictionary *)catalog forCategory:(NSString *)tag {
    if(![self genreCategory:tag])return @"";
    for(NSDictionary *entry in [self arr:catalog[@"extra"]]){
        if(![[self str:entry[@"name"]] isEqualToString:@"genre"])continue;
        for(id option in [self arr:entry[@"options"]]){
            NSString *value=[self str:option],*normalized=[self normalizedCategory:value];
            for(NSString *word in [self categoryAliases:tag]){
                if([normalized containsString:[self normalizedCategory:word]])
                    return [@"/genre=" stringByAppendingString:[self pathEncode:value]];
            }
        }
    }
    return @"";
}
- (void)showCategoryChooser:(UIButton *)button {
    UIAlertController *dialog=[UIAlertController alertControllerWithTitle:@"Categorias Telares"
        message:@"Catálogos declarados pelos addons. Não comprovam disponibilidade oficial ou assinatura de streaming."
        preferredStyle:UIAlertControllerStyleActionSheet];
    for(NSString *tag in @[@"Canais ao vivo",@"Anime",@"Netflix",@"Max / HBO",@"Disney+",@"Prime Video",@"Apple TV+",@"Paramount+",@"Crunchyroll", @"Documentários",@"Terror",@"Comédia",@"Ficção científica"]){
        [dialog addAction:[UIAlertAction actionWithTitle:tag
            style:UIAlertActionStyleDefault handler:^(UIAlertAction *action){
                self.searching=NO;
                self.search.hidden=YES;
                [self.search resignFirstResponder];
                self.search.text=@"";
                self.currentTerm=@"";
                self.categoryFilter=tag;
                self.filter=[tag isEqualToString:@"Canais ao vivo"]?@"Ao vivo":@"Categorias";
                self.title=@"TELARES";
                [self setSelectedTab];
                [self loadCategory:tag];
            }]];
    }
    [dialog addAction:[UIAlertAction actionWithTitle:@"Cancelar"
        style:UIAlertActionStyleCancel handler:nil]];
    UIPopoverPresentationController *popover=dialog.popoverPresentationController;
    if(popover){popover.sourceView=button;popover.sourceRect=button.bounds;}
    [self presentViewController:dialog animated:YES completion:nil];
}
/** A broadcast can be published as protocol type "series". Keep UI kind separate. */
- (BOOL)isBroadcastCatalog:(NSDictionary *)cat manifest:(NSDictionary *)manifest source:(NSString *)source {
    NSString *kind=[self normalizedCategory:[self str:cat[@"type"]]];
    if([kind isEqualToString:@"tv"]||[kind isEqualToString:@"channel"])return YES;
    NSString *origin=[self normalizedCategory:[NSString stringWithFormat:@"%@ %@ %@",
        [self str:manifest[@"id"]],[self str:manifest[@"name"]],source]];
    if([origin containsString:@"catalogo iptv"]||[origin containsString:@"tvvio"]||
       [origin containsString:@"iptv"])return YES;
    NSString *label=[self normalizedCategory:[NSString stringWithFormat:@"%@ %@",
        [self str:cat[@"name"]],[self str:cat[@"id"]]]];
    if([label containsString:@"canais ao vivo"]||[label containsString:@"live channels"]||
       [label containsString:@"tv channels"])return YES;
    NSString *description=[self normalizedCategory:[self str:manifest[@"description"]]];
    return ([description containsString:@"canais ao vivo"]||
            [description containsString:@"live tv"])&&
           ([label containsString:@"canais"]||[label containsString:@"channel"]||
            [label containsString:@"ao vivo"]||[label containsString:@"live"]);
}
- (void)loadCategory:(NSString *)tag {
    self.mode=@"catalog";
    NSInteger generation=++self.taskGeneration;
    if(![tag isEqualToString:@"Canais ao vivo"]&&self.liveSelection)[self stopLivePreview];
    [self.catalogItems removeAllObjects];
    [self refreshList];
    self.status.text=[NSString stringWithFormat:@"Buscando %@ nos addons...",tag];
    for(NSString *source in [self.sources copy]){
        [self manifest:source done:^(NSDictionary *manifest){
            if(generation!=self.taskGeneration||!manifest)return;
            NSString *base=[source substringToIndex:source.length-@"manifest.json".length];
            NSString *provider=[self str:manifest[@"name"]];
            NSUInteger loaded=0;
            for(NSDictionary *cat in [self arr:manifest[@"catalogs"]]){
                if(loaded>=22)break;
                NSString *kind=[self str:cat[@"type"]],*ident=[self str:cat[@"id"]];
                BOOL live=[tag isEqualToString:@"Canais ao vivo"];
                BOOL channel=[self isBroadcastCatalog:cat manifest:manifest source:source];
                BOOL custom=![kind isEqualToString:@"movie"]&&![kind isEqualToString:@"series"]&&!channel;
                if(live&&!channel)continue;
                if(!live&&channel)continue;
                if(custom&&!([[self str:cat[@"name"]].lowercaseString containsString:@"movie"]||
                             [[self str:cat[@"name"]].lowercaseString containsString:@"series"]||
                             [[self str:cat[@"id"]].lowercaseString containsString:@"movie"]||
                             [[self str:cat[@"id"]].lowercaseString containsString:@"series"]))continue;
                if(!ident.length||ident.length>100)continue;
                NSCharacterSet *safe=[NSCharacterSet characterSetWithCharactersInString:
                    @"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.:-"];
                if([ident rangeOfCharacterFromSet:[safe invertedSet]].location!=NSNotFound)continue;
                NSString *catName=[self str:cat[@"name"]];
                BOOL providerCatalog=[self category:tag fromProvider:provider catalog:catName catalogId:ident genres:@[]];
                NSString *genre=[self genreExtra:cat forCategory:tag];
                if(!live&&!providerCatalog&&!genre.length&&![self genreCategory:tag])continue;
                loaded++;
                NSString *url=[NSString stringWithFormat:@"%@catalog/%@/%@%@.json",
                    base,[self pathEncode:kind],[self pathEncode:ident],genre];
                [self requestJSON:url complete:^(NSDictionary *response,NSError *error){
                    if(generation!=self.taskGeneration||error)return;
                    NSMutableArray *items=[NSMutableArray array];
                    for(NSDictionary *raw in [self arr:response[@"metas"]]){
                        BOOL belongs=live||providerCatalog||genre.length>0||
                           [self category:tag fromProvider:provider catalog:catName
                             catalogId:ident genres:[self arr:raw[@"genres"]]];
                        if(!belongs)continue;
                        NSDictionary *media=[self mediaFrom:raw type:kind base:base];
                        if(media){
                            NSMutableDictionary *display=[media mutableCopy];
                            if(live){display[@"_kind"]=@"tv";display[@"type"]=kind;display[@"_group"]=catName;}
                            else if(custom){
                                display[@"type"]=kind; // Use manifest type for /stream and /meta URLs.
                                display[@"_kind"]=[[self str:cat[@"id"]].lowercaseString containsString:@"series"]?@"series":@"movie";
                            }
                            [items addObject:display];
                        }
                        if(items.count>=35)break;
                    }
                    [self appendItems:items to:self.catalogItems];
                    [self refreshList];
                }];
            }
        }];
    }
}
- (void)toggleSearch {
    if([self.mode isEqualToString:@"episodes"]||[self.mode isEqualToString:@"streams"])
        [self exitSpecialList];
    self.searching=!self.searching;
    self.search.hidden=!self.searching;
    [self.searchToggle setTitle:self.searching?@"✕  Fechar":@"⌕  Buscar"
        forState:UIControlStateNormal];
    if(self.searching){[self.search becomeFirstResponder];}
    else {
        [self.search resignFirstResponder];
        self.currentTerm=@"";
        self.search.text=@"";
        [self loadCatalogs];
    }
    [self setSelectedTab];
    [self refreshList];
}
- (void)refreshHero {
    BOOL display=self.results.count>0&&!self.searching&&[self.filter isEqualToString:@"Início"]&&
        [self.mode isEqualToString:@"catalog"];
    self.featuredItem=display?self.results.firstObject:nil;
    if(display){
        self.heroTitle.text=[self str:self.featuredItem[@"name"]];
        self.heroSummary.text=[self str:self.featuredItem[@"description"]];
        self.heroArt.image=nil;
        [self loadPoster:[self str:self.featuredItem[@"poster"]] into:self.heroArt];
    }
    [self.view setNeedsLayout];
}
- (void)openFeatured {
    if(self.featuredItem)[self showDetails:self.featuredItem];
}
- (void)loadPoster:(NSString *)url into:(UIImageView *)view {
    if(![url hasPrefix:@"https://"])return;
    UIImage *cached=[self.imageCache objectForKey:url];
    if(cached){view.image=cached;return;}
    if([self.pendingImages containsObject:url])return;
    [self.pendingImages addObject:url];
    __weak ShockViewController *weakSelf=self;
    [[[NSURLSession sharedSession] dataTaskWithURL:[NSURL URLWithString:url]
        completionHandler:^(NSData *data,NSURLResponse *response,NSError *error){
            UIImage *thumbnail=nil;
            if(!error&&data.length>=100&&data.length<1200000){
                // ImageIO creates a thumbnail without decoding the full-resolution JPEG.
                CGImageSourceRef source=CGImageSourceCreateWithData((__bridge CFDataRef)data,NULL);
                if(source){
                    NSDictionary *options=@{
                        (id)kCGImageSourceCreateThumbnailFromImageAlways:@YES,
                        (id)kCGImageSourceCreateThumbnailWithTransform:@YES,
                        (id)kCGImageSourceShouldCache:@NO,
                        (id)kCGImageSourceThumbnailMaxPixelSize:@320
                    };
                    CGImageRef reduced=CGImageSourceCreateThumbnailAtIndex(source,0,
                        (__bridge CFDictionaryRef)options);
                    if(reduced){
                        thumbnail=[UIImage imageWithCGImage:reduced];
                        CGImageRelease(reduced);
                    }
                    CFRelease(source);
                }
            }
            dispatch_async(dispatch_get_main_queue(),^{
                ShockViewController *strong=weakSelf;
                if(!strong)return;
                [strong.pendingImages removeObject:url];
                if(!thumbnail)return;
                NSUInteger cost=(NSUInteger)(thumbnail.size.width*thumbnail.size.height*4.0);
                [strong.imageCache setObject:thumbnail forKey:url cost:cost];
                if([strong.heroArt isEqual:view]&&
                    [[strong str:strong.featuredItem[@"poster"]] isEqualToString:url])
                    strong.heroArt.image=thumbnail;
                for(ShockPosterCell *cell in [strong.posterGrid visibleCells])
                    if([cell.imageURL isEqualToString:url])cell.art.image=thumbnail;
            });
        }] resume];
}
- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    [self.imageCache removeAllObjects];
    [self.posterCache removeAllObjects];
}
- (NSString *)keyFor:(NSDictionary *)item {
    NSString *type = [self str:item[@"type"]];
    NSString *ident = [self str:item[@"id"]];
    NSString *episode = [self str:item[@"_episode"]];
    if([self isLiveItem:item]){
        // Keep live and VOD records separate even when an addon uses type=series
        // with the same ID. Do not change the existing saved VOD key format.
        return [NSString stringWithFormat:@"live|%@|%@|%@|%@",type,ident,
            [self str:item[@"_base"]],episode];
    }
    return [NSString stringWithFormat:@"%@|%@|%@",type,ident,episode];
}
- (NSString *)pathEncode:(NSString *)value {
    NSCharacterSet *safe = [NSCharacterSet characterSetWithCharactersInString:
        @"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"];
    return [value stringByAddingPercentEncodingWithAllowedCharacters:safe] ?: @"";
}
- (void)requestJSON:(NSString *)endpoint complete:(void (^)(NSDictionary *, NSError *))finish {
    NSURL *url = [NSURL URLWithString:endpoint];
    if (!url || ![url.scheme.lowercaseString isEqualToString:@"https"]) {
        finish(nil,[NSError errorWithDomain:@"Telares.Play" code:-1
            userInfo:@{NSLocalizedDescriptionKey:@"O addon precisa utilizar HTTPS."}]);
        return;
    }
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url
        cachePolicy:NSURLRequestReloadIgnoringLocalCacheData timeoutInterval:9.0];
    [request setValue:@"application/json" forHTTPHeaderField:@"Accept"];
    NSURLSessionDataTask *task = [[NSURLSession sharedSession]
        dataTaskWithRequest:request completionHandler:^(NSData *data,NSURLResponse *response,NSError *error) {
            NSHTTPURLResponse *http = [response isKindOfClass:[NSHTTPURLResponse class]] ?
                (NSHTTPURLResponse *)response : nil;
            if (!error && (http.statusCode < 200 || http.statusCode >= 300))
                error = [NSError errorWithDomain:@"Telares.Play.HTTP" code:http.statusCode
                    userInfo:@{NSLocalizedDescriptionKey:@"O servidor não respondeu com sucesso."}];
            if (!error && (data.length == 0 || data.length > 2000000))
                error = [NSError errorWithDomain:@"Telares.Play.JSON" code:1
                    userInfo:@{NSLocalizedDescriptionKey:@"Resposta vazia ou grande demais."}];
            NSDictionary *json = nil;
            if (!error) {
                id parsed = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
                if ([parsed isKindOfClass:[NSDictionary class]]) json = parsed;
                else if (!error) error = [NSError errorWithDomain:@"ShockTV.JSON" code:2
                   userInfo:@{NSLocalizedDescriptionKey:@"A resposta não é um objeto JSON."}];
            }
            dispatch_async(dispatch_get_main_queue(),^{ finish(json,error); });
        }];
    [task resume];
}
- (void)manifest:(NSString *)url done:(void (^)(NSDictionary *))done {
    NSDictionary *cached = [self dict:self.manifests[url]];
    if (cached.count) { done(cached); return; }
    [self requestJSON:url complete:^(NSDictionary *json,NSError *error) {
        if (!error && json[@"resources"] && json[@"id"]) self.manifests[url] = json;
        done(error ? nil : json);
    }];
}
- (BOOL)manifest:(NSDictionary *)manifest resource:(NSString *)resource type:(NSString *)type identifier:(NSString *)ident {
    for (id item in [self arr:manifest[@"resources"]]) {
        NSDictionary *spec = [self dict:item];
        NSString *name = [item isKindOfClass:[NSString class]] ? item : [self str:spec[@"name"]];
        if (![name isEqualToString:resource]) continue;
        NSArray *types = [self arr:spec[@"types"] ?: manifest[@"types"]];
        if (types.count && ![types containsObject:type]) continue;
        NSArray *prefixes = [self arr:spec[@"idPrefixes"] ?: manifest[@"idPrefixes"]];
        if (prefixes.count) {
            BOOL supported = NO;
            for (NSString *p in prefixes) {
                if ([ident hasPrefix:[self str:p]]) { supported = YES; break; }
            }
            if (!supported) continue;
        }
        return YES;
    }
    return NO;
}
- (NSDictionary *)mediaFrom:(NSDictionary *)raw type:(NSString *)type base:(NSString *)base {
    NSMutableDictionary *item = [[self dict:raw] mutableCopy];
    NSString *ident = [self str:item[@"id"]];
    NSString *name = [self str:item[@"name"]];
    if (!ident.length || !name.length) return nil;
    if (![item[@"type"] isKindOfClass:[NSString class]])item[@"type"] = type;
    item[@"_base"] = base ?: @"";
    return item;
}
- (void)appendItems:(NSArray *)input to:(NSMutableArray *)target {
    NSMutableSet *ids = [NSMutableSet set];
    for (NSDictionary *item in target) [ids addObject:[self keyFor:item]];
    for (NSDictionary *item in input) {
        NSString *key = [self keyFor:item];
        if (!key.length || [ids containsObject:key])continue;
        [ids addObject:key];
        [target addObject:item];
    }
}
- (void)loadCatalogs {
    self.mode = @"catalog";
    NSInteger generation = ++self.taskGeneration;
    [self.catalogItems removeAllObjects];
    [self.table reloadData];
    self.status.text = @"Carregando títulos reais dos addons...";
    for (NSString *url in [self.sources copy]) {
        [self manifest:url done:^(NSDictionary *manifest) {
            if (generation != self.taskGeneration || !manifest) return;
            NSString *base = [url substringToIndex:url.length - @"manifest.json".length];
            NSUInteger fetched = 0;
            for (NSDictionary *catalog in [self arr:manifest[@"catalogs"]]) {
                if (fetched >= 8) break;
                NSString *type = [self str:catalog[@"type"]];
                NSString *ident = [self str:catalog[@"id"]];
                if([self isBroadcastCatalog:catalog manifest:manifest source:url])continue;
                BOOL native=[type isEqualToString:@"movie"]||[type isEqualToString:@"series"];
                NSString *lower=[[self str:catalog[@"name"]] stringByAppendingFormat:@" %@",ident].lowercaseString;
                BOOL movieCustom=[lower containsString:@"movie"]||[lower containsString:@"film"];
                BOOL seriesCustom=[lower containsString:@"series"]||[lower containsString:@"shows"];
                if(!native&&!movieCustom&&!seriesCustom)continue;
                if(!ident.length)continue;
                fetched++;
                NSString *endpoint = [NSString stringWithFormat:@"%@catalog/%@/%@.json",
                    base,[self pathEncode:type],[self pathEncode:ident]];
                [self requestJSON:endpoint complete:^(NSDictionary *payload,NSError *error) {
                    if (generation != self.taskGeneration || error) return;
                    NSMutableArray *found = [NSMutableArray array];
                    for (NSDictionary *raw in [self arr:payload[@"metas"]]) {
                        NSDictionary *media = [self mediaFrom:raw type:type base:base];
                        if(media){
                            NSMutableDictionary *entry=[media mutableCopy];
                            if(!native){entry[@"type"]=type;entry[@"_kind"]=seriesCustom?@"series":@"movie";}
                            [found addObject:entry];
                        }
                        if(found.count >= 28)break;
                    }
                    [self appendItems:found to:self.catalogItems];
                    [self refreshList];
                }];
            }
        }];
    }
}
- (void)changeSection {
    self.filter = [@[@"Início",@"Filmes",@"Séries",@"Minha lista",@"Categorias",@"Ao vivo"] objectAtIndex:
        (NSUInteger)MAX(0,self.sections.selectedSegmentIndex)];
    [self refreshList];
}
- (NSArray *)savedEntries {
    return [self arr:[[NSUserDefaults standardUserDefaults] objectForKey:@"shock_items_v1"]];
}
- (void)saveEntry:(NSDictionary *)item update:(NSString *)state {
    NSMutableArray *all = [[self savedEntries] mutableCopy];
    NSString *key = [self keyFor:item];
    NSUInteger idx = NSNotFound;
    for (NSUInteger i=0;i<all.count;i++) {
        if ([[self keyFor:all[i]] isEqualToString:key]){idx=i;break;}
    }
    NSMutableDictionary *next = idx == NSNotFound ? [NSMutableDictionary dictionary] :
        [[self dict:all[idx]] mutableCopy];
    for (NSString *name in @[@"id",@"name",@"type",@"_kind",@"poster",@"description",@"_base",@"_episode"])
        if (item[name] && [item[name] isKindOfClass:[NSString class]])next[name] = item[name];
    if (state.length)next[state] = @(![next[state] boolValue]);
    if (idx != NSNotFound)[all removeObjectAtIndex:idx];
    [all insertObject:next atIndex:0];
    while(all.count>150)[all removeLastObject];
    [[NSUserDefaults standardUserDefaults] setObject:all forKey:@"shock_items_v1"];
    [[NSUserDefaults standardUserDefaults] synchronize];
    [self refreshList];
}
- (BOOL)isMarked:(NSDictionary *)item field:(NSString *)name {
    for (NSDictionary *saved in [self savedEntries])
        if ([[self keyFor:saved] isEqualToString:[self keyFor:item]])
            return [saved[name] boolValue];
    return NO;
}
- (void)refreshList {
    if ([self.mode isEqualToString:@"episodes"] || [self.mode isEqualToString:@"streams"]){
        [self.table reloadData];[self.view setNeedsLayout];return;
    }
    [self.results removeAllObjects];
    NSArray *items = self.currentTerm.length>=2 ? self.catalogItems : self.catalogItems;
    if ([self.filter isEqualToString:@"Minha lista"]) {
        for(NSDictionary *item in [self savedEntries])
            if ([item[@"liked"] boolValue]||[item[@"later"] boolValue]||[item[@"watched"] boolValue])
                [self.results addObject:item];
    } else {
        for (NSDictionary *item in items) {
            // Use the same live classifier everywhere, including entries from old versions.
            NSString *type=[self isLiveItem:item]?@"tv":[self str:item[@"_kind"]];
            if(!type.length)type=[self str:item[@"type"]];
            if(([self.filter isEqualToString:@"Início"]||
                [self.filter isEqualToString:@"Filmes"]||
                [self.filter isEqualToString:@"Séries"])&&[type isEqualToString:@"tv"])continue;
            if([self.filter isEqualToString:@"Ao vivo"]&&![type isEqualToString:@"tv"])continue;
            if([self.filter isEqualToString:@"Filmes"]&&![type isEqualToString:@"movie"])continue;
            if([self.filter isEqualToString:@"Séries"]&&![type isEqualToString:@"series"])continue;
            if (self.currentTerm.length>=2 &&
                [[self str:item[@"name"]].lowercaseString rangeOfString:self.currentTerm.lowercaseString].location
                    == NSNotFound)continue;
            [self.results addObject:item];
        }
    }
    self.status.text = [NSString stringWithFormat:@"%lu título(s) • %@ • iOS 9",
                        (unsigned long)self.results.count,self.categoryFilter?:@"Catálogos"];
    [self.table reloadData];
    [self.posterGrid reloadData];
    [self refreshHero];
    [self updateLiveGuide];
}
- (void)searchBar:(UISearchBar *)bar textDidChange:(NSString *)text {
    self.currentTerm = [text stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]];
    [NSObject cancelPreviousPerformRequestsWithTarget:self selector:@selector(beginSearch) object:nil];
    if (self.currentTerm.length>=2)
        [self performSelector:@selector(beginSearch) withObject:nil afterDelay:0.7];
    else [self loadCatalogs];
    [self refreshList];
}
- (void)searchBarSearchButtonClicked:(UISearchBar *)bar {
    [bar resignFirstResponder];
    [NSObject cancelPreviousPerformRequestsWithTarget:self selector:@selector(beginSearch) object:nil];
    [self beginSearch];
}
- (void)beginSearch {
    if(self.currentTerm.length<2){[self loadCatalogs];return;}
    NSInteger generation=++self.taskGeneration;
    [self.catalogItems removeAllObjects];
    [self refreshList];
    NSString *query = self.currentTerm;
    self.status.text = [NSString stringWithFormat:@"Buscando “%@”...",query];
    for (NSString *url in [self.sources copy]) {
        [self manifest:url done:^(NSDictionary *manifest) {
            if (generation!=self.taskGeneration||!manifest)return;
            NSString *base=[url substringToIndex:url.length-@"manifest.json".length];
            NSUInteger attempts=0;
            for (NSDictionary *cat in [self arr:manifest[@"catalogs"]]) {
                if(attempts>=6)break;
                NSString *type=[self str:cat[@"type"]],*ident=[self str:cat[@"id"]];
                if(![type isEqualToString:@"movie"]&&![type isEqualToString:@"series"])continue;
                if([self isBroadcastCatalog:cat manifest:manifest source:url])continue;
                BOOL canSearch=NO;
                for(id extra in [self arr:cat[@"extra"]]){
                    if([extra isKindOfClass:[NSDictionary class]]
                      && [[self str:extra[@"name"]] isEqualToString:@"search"])canSearch=YES;
                    if([extra isKindOfClass:[NSString class]]
                      && [extra isEqualToString:@"search"])canSearch=YES;
                }
                if(!canSearch || !ident.length)continue;
                attempts++;
                NSString *endpoint=[NSString stringWithFormat:@"%@catalog/%@/%@/search=%@.json",
                    base,[self pathEncode:type],[self pathEncode:ident],[self pathEncode:query]];
                [self requestJSON:endpoint complete:^(NSDictionary *payload,NSError *error) {
                    if (generation!=self.taskGeneration || error)return;
                    NSMutableArray *found=[NSMutableArray array];
                    for(NSDictionary *raw in [self arr:payload[@"metas"]]){
                        NSDictionary *media=[self mediaFrom:raw type:type base:base];
                        if(media)[found addObject:media];
                        if(found.count>=45)break;
                    }
                    [self appendItems:found to:self.catalogItems];
                    [self refreshList];
                }];
            }
        }];
    }
}
- (void)showSources {
    UIAlertController *alert=[UIAlertController alertControllerWithTitle:@"Fontes Stremio"
        message:@"Gerencie os manifestos HTTPS. Catálogos e reproduções dependem da disponibilidade dos provedores."
        preferredStyle:UIAlertControllerStyleAlert];
    for (NSString *source in [self.sources copy]) {
        NSString *host=[NSURL URLWithString:source].host?:source;
        [alert addAction:[UIAlertAction actionWithTitle:
            [NSString stringWithFormat:@"Remover: %@",host] style:UIAlertActionStyleDefault
            handler:^(UIAlertAction *action){
                if ([self.sources containsObject:source]) {
                    [self.sources removeObject:source];
                    [[NSUserDefaults standardUserDefaults] setObject:self.sources forKey:@"shock_sources_v1"];
                    [self.manifests removeAllObjects];
                    [self loadCatalogs];
                }
            }]];
    }
    [alert addTextFieldWithConfigurationHandler:^(UITextField *field){
        field.placeholder=@"https://servidor/manifest.json";
        field.keyboardType=UIKeyboardTypeURL;
        field.autocapitalizationType=UITextAutocapitalizationTypeNone;
    }];
    [alert addAction:[UIAlertAction actionWithTitle:@"Adicionar" style:UIAlertActionStyleDefault
        handler:^(UIAlertAction *action){
            NSString *value=[self str:alert.textFields.firstObject.text];
            if([value hasPrefix:@"https://"]&&[value hasSuffix:@"/manifest.json"]&&
               ![self.sources containsObject:value]&&self.sources.count<30) {
                [self manifest:value done:^(NSDictionary *manifest) {
                    if (manifest[@"id"] && manifest[@"resources"]) {
                        [self.sources addObject:value];
                        [[NSUserDefaults standardUserDefaults] setObject:self.sources forKey:@"shock_sources_v1"];
                        [self loadCatalogs];
                    }else [self notice:@"Manifesto inválido ou servidor indisponível."];
                }];
            } else [self notice:@"Informe uma URL HTTPS válida e não duplicada."];
        }]];
    [alert addAction:[UIAlertAction actionWithTitle:@"Fechar" style:UIAlertActionStyleCancel handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}
- (void)notice:(NSString *)message {
    UIAlertController *alert=[UIAlertController alertControllerWithTitle:@"Telares Play" message:message
        preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}

#pragma mark - Native iPad poster grid

- (NSInteger)collectionView:(UICollectionView *)collectionView numberOfItemsInSection:(NSInteger)section {
    if(![self.mode isEqualToString:@"catalog"])return 0;
    NSUInteger total=self.results.count;
    BOOL hasHero=[self.filter isEqualToString:@"Início"]&&!self.searching&&total>0;
    return (NSInteger)(hasHero?total-1:total);
}
- (NSDictionary *)mediaAtIndex:(NSInteger)index {
    NSInteger offset=[self.filter isEqualToString:@"Início"]&&!self.searching&&self.results.count>0?1:0;
    NSInteger selected=index+offset;
    if(selected<0||selected>=self.results.count)return nil;
    return [self dict:self.results[(NSUInteger)selected]];
}
- (CGSize)collectionView:(UICollectionView *)collectionView layout:(UICollectionViewLayout *)layout
    sizeForItemAtIndexPath:(NSIndexPath *)indexPath {
    CGFloat available=self.view.bounds.size.width-38;
    NSInteger cols=self.view.bounds.size.width>self.view.bounds.size.height?5:3;
    CGFloat gap=14;
    CGFloat width=floor((available-(cols-1)*gap-22)/cols);
    return CGSizeMake(width,MIN(260,width*1.31+48));
}
- (UICollectionViewCell *)collectionView:(UICollectionView *)collectionView
    cellForItemAtIndexPath:(NSIndexPath *)indexPath {
    ShockPosterCell *cell=[collectionView dequeueReusableCellWithReuseIdentifier:@"shock.card"
        forIndexPath:indexPath];
    NSDictionary *item=[self mediaAtIndex:indexPath.item];
    cell.caption.text=[self str:item[@"name"]];
    NSString *kind=[self str:item[@"_kind"]].length?[self str:item[@"_kind"]]:[self str:item[@"type"]];
    cell.typeTag.text=([kind isEqualToString:@"tv"]||[kind isEqualToString:@"channel"])?@"AO VIVO":
        [kind isEqualToString:@"series"]?@"SÉRIE":@"FILME";
    cell.imageURL=[self str:item[@"poster"]];
    cell.art.image=[self.imageCache objectForKey:cell.imageURL];
    if(!cell.art.image)[self loadPoster:cell.imageURL into:cell.art];
    return cell;
}
- (void)collectionView:(UICollectionView *)collectionView didSelectItemAtIndexPath:(NSIndexPath *)indexPath {
    NSDictionary *item=[self mediaAtIndex:indexPath.item];
    if(item)[self showDetails:item];
}

- (NSInteger)tableView:(UITableView *)table numberOfRowsInSection:(NSInteger)section {
    if(table==self.liveGroups)return (NSInteger)self.liveGroupNames.count;
    if(table==self.liveChannels)return (NSInteger)self.liveFiltered.count;
    return (NSInteger)self.results.count;
}
- (UITableViewCell *)tableView:(UITableView *)table cellForRowAtIndexPath:(NSIndexPath *)path {
    if(table==self.liveGroups||table==self.liveChannels){
        BOOL group=(table==self.liveGroups);
        static NSString *identifier=@"telares.guide";
        UITableViewCell *cell=[table dequeueReusableCellWithIdentifier:identifier];
        if(!cell)cell=[[UITableViewCell alloc]initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:identifier];
        NSDictionary *channel=group?nil:self.liveFiltered[(NSUInteger)path.row];
        NSString *name=group?self.liveGroupNames[(NSUInteger)path.row]:[self str:channel[@"name"]];
        cell.textLabel.text=name;
        cell.textLabel.textColor=[UIColor whiteColor];
        cell.textLabel.font=[UIFont boldSystemFontOfSize:15];
        cell.detailTextLabel.text=group?@"":[self str:channel[@"_group"]];
        cell.detailTextLabel.textColor=[UIColor colorWithWhite:.72 alpha:1];
        BOOL selected=group?[name isEqualToString:self.liveGroup]:
             [[self keyFor:channel] isEqualToString:[self keyFor:self.liveSelection]];
        cell.backgroundColor=selected?[UIColor colorWithRed:.13 green:.37 blue:.48 alpha:1]:
             [UIColor colorWithRed:.08 green:.15 blue:.22 alpha:1];
        cell.selectionStyle=UITableViewCellSelectionStyleNone;
        return cell;
    }
    static NSString *identifier=@"shock.media";
    UITableViewCell *cell=[table dequeueReusableCellWithIdentifier:identifier];
    if(!cell)cell=[[UITableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle
                                         reuseIdentifier:identifier];
    NSDictionary *item=[self dict:self.results[(NSUInteger)path.row]];
    cell.textLabel.text=[self str:item[@"name"]];
    cell.textLabel.textColor=[UIColor whiteColor];
    cell.textLabel.font=[UIFont boldSystemFontOfSize:17];
    if ([self.mode isEqualToString:@"streams"]) {
        cell.detailTextLabel.text=[self str:item[@"_label"]];
    }else if ([self.mode isEqualToString:@"episodes"]){
        cell.detailTextLabel.text=[NSString stringWithFormat:@"Temporada %@ · Episódio %@",
            item[@"season"]?:@0,item[@"episode"]?:@0];
    }else {
        NSString *kind=[[self str:item[@"type"]] isEqualToString:@"series"]?@"Série":@"Filme";
        cell.detailTextLabel.text=[NSString stringWithFormat:@"%@  •  %@",
             kind,[[self str:item[@"description"]] length]? [self str:item[@"description"]]:@"Toque para ver detalhes"];
    }
    cell.detailTextLabel.textColor=[UIColor colorWithWhite:0.66 alpha:1];
    cell.detailTextLabel.numberOfLines=2;
    cell.backgroundColor=[self dark];
    cell.accessoryType=UITableViewCellAccessoryDisclosureIndicator;
    // Text-only series and source lists avoid decoding large poster JPEGs.\n    cell.imageView.image=nil;
    return cell;
}
- (void)tableView:(UITableView *)table didSelectRowAtIndexPath:(NSIndexPath *)path {
    [table deselectRowAtIndexPath:path animated:YES];
    if(table==self.liveGroups){
        self.liveGroup=self.liveGroupNames[(NSUInteger)path.row];[self updateLiveGuide];return;
    }
    if(table==self.liveChannels){
        NSDictionary *channel=self.liveFiltered[(NSUInteger)path.row];
        [self playLiveSelection:channel];[self.liveChannels reloadData];return;
    }
    NSDictionary *item=[self dict:self.results[(NSUInteger)path.row]];
    if([self.mode isEqualToString:@"streams"]){[self playStream:item];return;}
    if([self.mode isEqualToString:@"episodes"]){
        NSMutableDictionary *episode=[self.activeSeries mutableCopy];
        episode[@"_episode"]=[self str:item[@"id"]];
        episode[@"name"]=[NSString stringWithFormat:@"%@ • %@",[self str:self.activeSeries[@"name"]],[self str:item[@"title"]]];
        [self resolveStreams:episode];
        return;
    }
    [self showDetails:item];
}
- (void)showDetails:(NSDictionary *)item {
    if(!item)return;
    self.detailItem=item;
    ShockDetailViewController *detail=[[ShockDetailViewController alloc]initWithMedia:item
        liked:[self isMarked:item field:@"liked"] later:[self isMarked:item field:@"later"]];
    self.detailController=detail;
    __weak ShockViewController *weakSelf=self;
    __weak ShockDetailViewController *weakDetail=detail;
    detail.onPlay=^{
        ShockViewController *strong=weakSelf;
        NSDictionary *chosen=strong.detailItem;
        [weakDetail dismissViewControllerAnimated:YES completion:^{
            strong.detailController=nil;
            if(([[strong str:chosen[@"type"]] isEqualToString:@"series"]||
                 [[strong str:chosen[@"_kind"]] isEqualToString:@"series"])&&
                ![strong str:chosen[@"_episode"]].length)
                [strong resolveEpisodes:chosen];
            else [strong resolveStreams:chosen];
        }];
    };
    detail.onLike=^{
        [weakSelf saveEntry:item update:@"liked"];
    };
    detail.onLater=^{
        [weakSelf saveEntry:item update:@"later"];
    };
    UINavigationController *navigation=[[UINavigationController alloc]initWithRootViewController:detail];
    navigation.modalPresentationStyle=UIModalPresentationFullScreen;
    [self presentViewController:navigation animated:YES completion:nil];
}
- (void)showSeasonSelector {
    if(![self.mode isEqualToString:@"episodes"]||!self.episodeSeasonOptions.count)return;
    UIAlertController *chooser=[UIAlertController alertControllerWithTitle:@"Escolher temporada"
       message:@"Selecione uma temporada para visualizar somente seus episódios."
       preferredStyle:UIAlertControllerStyleActionSheet];
    for(NSNumber *number in self.episodeSeasonOptions){
        NSInteger season=[number integerValue];
        NSString *name=season<0?@"Episódios sem temporada":
                       season==0?@"Especiais": [NSString stringWithFormat:@"Temporada %ld",(long)season];
        [chooser addAction:[UIAlertAction actionWithTitle:name style:UIAlertActionStyleDefault
          handler:^(UIAlertAction *action){[self selectSeason:number];}]];
    }
    [chooser addAction:[UIAlertAction actionWithTitle:@"Cancelar" style:UIAlertActionStyleCancel handler:nil]];
    UIPopoverPresentationController *popover=chooser.popoverPresentationController;
    if(popover){popover.barButtonItem=self.navigationItem.rightBarButtonItem;}
    [self presentViewController:chooser animated:YES completion:nil];
}
- (void)selectSeason:(NSNumber *)season {
    self.selectedEpisodeSeason=season;
    [self.results removeAllObjects];
    for(NSDictionary *ep in self.allSeriesEpisodes){
        NSInteger value=ep[@"season"]&&ep[@"season"]!=[NSNull null]?
            [ep[@"season"] integerValue]:-1;
        if(value==[season integerValue])[self.results addObject:ep];
    }
    [self.table reloadData];
    NSString *label=[season integerValue]<0?@"Episódios":
        [season integerValue]==0?@"Especiais":
        [NSString stringWithFormat:@"Temporada %@",season];
    self.title=label;
    self.status.text=[NSString stringWithFormat:@"%@ · %lu episódios",
        label,(unsigned long)self.results.count];
    [self.table setContentOffset:CGPointZero animated:NO];
}
- (void)resolveEpisodes:(NSDictionary *)series {
    self.activeSeries=series;
    self.status.text=@"Consultando metadados dos episódios...";
    NSString *type=[self str:series[@"type"]],*ident=[self str:series[@"id"]];
    [self checkEpisodeSources:[self.sources copy] index:0 series:series type:type identifier:ident];
}
- (void)checkEpisodeSources:(NSArray *)all index:(NSUInteger)index series:(NSDictionary *)series
                       type:(NSString *)type identifier:(NSString *)ident {
    if(index>=all.count){[self notice:@"Nenhum addon ativo forneceu episódios para esta série."];return;}
    NSString *source=[self str:all[index]];
    [self manifest:source done:^(NSDictionary *manifest) {
        if(![self manifest:manifest resource:@"meta" type:type identifier:ident]){
            [self checkEpisodeSources:all index:index+1 series:series type:type identifier:ident];
            return;
        }
        NSString *base=[source substringToIndex:source.length-@"manifest.json".length];
        NSString *endpoint=[NSString stringWithFormat:@"%@meta/%@/%@.json",
            base,[self pathEncode:type],[self pathEncode:ident]];
        [self requestJSON:endpoint complete:^(NSDictionary *payload,NSError *error) {
            NSArray *videos=[self arr:[self dict:payload[@"meta"]][@"videos"]];
            if(error||!videos.count){
                [self checkEpisodeSources:all index:index+1 series:series type:type identifier:ident];
                return;
            }
            [self.results removeAllObjects];
            for(NSDictionary *ep in videos)if([self str:ep[@"id"]].length)
                [self.results addObject:ep];
            [self.results sortUsingComparator:^NSComparisonResult(NSDictionary *a,NSDictionary *b){
                NSInteger sa=[a[@"season"] integerValue],sb=[b[@"season"] integerValue];
                if(sa!=sb)return sa<sb?NSOrderedAscending:NSOrderedDescending;
                NSInteger ea=[a[@"episode"] integerValue],eb=[b[@"episode"] integerValue];
                return ea<eb?NSOrderedAscending:ea>eb?NSOrderedDescending:NSOrderedSame;
            }];
            // Keep the full episode list, but render only one season at a time.
            self.allSeriesEpisodes=[self.results copy];
            NSMutableSet *distinct=[NSMutableSet set];
            for(NSDictionary *episode in self.allSeriesEpisodes){
                NSInteger n=(episode[@"season"]&&episode[@"season"]!=[NSNull null])?
                   [episode[@"season"] integerValue]:-1;
                [distinct addObject:@(n)];
            }
            self.episodeSeasonOptions=[[distinct allObjects] sortedArrayUsingSelector:@selector(compare:)];
            self.mode=@"episodes";
            [self.view setNeedsLayout];
            self.navigationItem.leftBarButtonItem=[[UIBarButtonItem alloc] initWithTitle:@"Voltar"
                style:UIBarButtonItemStylePlain target:self action:@selector(exitSpecialList)];
            self.navigationItem.rightBarButtonItem=self.episodeSeasonOptions.count>1?
                [[UIBarButtonItem alloc]initWithTitle:@"Temporadas"
                style:UIBarButtonItemStylePlain target:self action:@selector(showSeasonSelector)]:nil;
            NSNumber *defaultSeason=nil;
            for(NSNumber *n in self.episodeSeasonOptions)
                if([n integerValue]>0){defaultSeason=n;break;}
            if(!defaultSeason)defaultSeason=[self.episodeSeasonOptions firstObject];
            if(defaultSeason)[self selectSeason:defaultSeason];
        }];
    }];
}
- (void)resolveStreams:(NSDictionary *)item {
    NSString *type=[self str:item[@"type"]];
    NSString *ident=[self str:item[@"_episode"]];
    if(!ident.length)ident=[self str:item[@"id"]];
    self.status.text=@"Consultando streams dos addons...";
    self.streamRequestGeneration++;
    [self.streamChecker cancel];
    self.streamChecker=nil;
    self.workingStreams=nil;
    self.selectedMedia=item;
    NSMutableArray *collected=[NSMutableArray array];
    [self checkStreamSources:[self.sources copy] index:0 item:item
                 type:type identifier:ident found:collected];
}
- (void)checkStreamSources:(NSArray *)all index:(NSUInteger)index item:(NSDictionary *)item
                      type:(NSString *)type identifier:(NSString *)ident found:(NSMutableArray *)found {
    if(item!=self.selectedMedia)return;
    if(index>=all.count){
        if(!found.count){
            if([self isLiveItem:item]){
                self.liveMessage.text=@"Este canal não retornou um endereço HTTP/HLS compatível.";
                self.liveFullscreen.hidden=YES;
            }else{
                [self notice:@"Nenhuma fonte HTTP/HLS compatível foi encontrada neste título. Opções BitTorrent não são reproduzidas diretamente no iPad."];
            }
            return;
        }
        [self chooseAutomaticallyFrom:found item:item];
        return;
    }
    NSString *source=[self str:all[index]];
    [self manifest:source done:^(NSDictionary *manifest){
        if(item!=self.selectedMedia)return;
        if(![self manifest:manifest resource:@"stream" type:type identifier:ident]){
            [self checkStreamSources:all index:index+1 item:item type:type identifier:ident found:found];
            return;
        }
        NSString *base=[source substringToIndex:source.length-@"manifest.json".length];
        NSString *endpoint=[NSString stringWithFormat:@"%@stream/%@/%@.json",
            base,[self pathEncode:type],[self pathEncode:ident]];
        [self requestJSON:endpoint complete:^(NSDictionary *payload,NSError *error){
            if(item!=self.selectedMedia)return;
            if(!error)for(NSDictionary *stream in [self arr:payload[@"streams"]]){
                NSString *link=[self str:stream[@"url"]];
                NSURL *url=[NSURL URLWithString:link];
                if(!url.host||(![url.scheme.lowercaseString isEqualToString:@"https"]&&
                    ![url.scheme.lowercaseString isEqualToString:@"http"]))continue;
                BOOL exists=NO;
                for(NSDictionary *other in found)
                    if([other[@"_url"] isEqualToString:link]){exists=YES;break;}
                if(exists)continue;
                NSMutableDictionary *data=[item mutableCopy];
                data[@"_url"]=link;
                NSString *label=[NSString stringWithFormat:@"%@  •  %@",
                    [self str:manifest[@"name"]],[self str:stream[@"name"]]];
                NSString *description=[self str:stream[@"title"]];
                data[@"_label"]=[label stringByAppendingFormat:@" %@",description];
                // The legacy AVPlayer cannot inject private addon headers.
                NSDictionary *headers=[self dict:[self dict:[self dict:stream[@"behaviorHints"]][@"proxyHeaders"]][@"request"]];
                if(headers.count)continue;
                [found addObject:data];
                if(found.count>=28)break;
            }
            [self checkStreamSources:all index:index+1 item:item type:type identifier:ident found:found];
        }];
    }];
}
- (void)exitSpecialList {
    self.title=@"TELARES";
    self.mode=@"catalog";
    [self.view setNeedsLayout];
    self.navigationItem.leftBarButtonItem=nil;
    self.activeSeries=nil;
    self.allSeriesEpisodes=nil;
    self.episodeSeasonOptions=nil;
    self.selectedEpisodeSeason=nil;
    self.navigationItem.rightBarButtonItem=nil;
    if(self.currentTerm.length>=2)[self beginSearch];
    else [self loadCatalogs];
}
- (BOOL)isLiveItem:(NSDictionary *)item {
    NSString *kind=[self str:item[@"_kind"]];
    NSString *type=[self str:item[@"type"]];
    NSString *origin=[self normalizedCategory:[self str:item[@"_base"]]];
    return [kind isEqualToString:@"tv"]||[type isEqualToString:@"tv"]||
           [type isEqualToString:@"channel"]||
           [origin containsString:@"catalogo iptv production"]||
           [origin containsString:@"tvvio.baby beamup"];
}
- (void)chooseAutomaticallyFrom:(NSArray *)candidates item:(NSDictionary *)item {
    NSUInteger generation=self.streamRequestGeneration;
    BOOL live=[self isLiveItem:item];
    self.status.text=[NSString stringWithFormat:@"Analisando %lu fonte(s) %@...",
        (unsigned long)MIN(candidates.count,16),live?@"ao vivo":@"de vídeo"];
    ShockStreamChecker *checker=[[ShockStreamChecker alloc]init];
    self.streamChecker=checker;
    __weak ShockViewController *weakSelf=self;

    if(live){
        // Live endpoints can reject Range even when a native HLS player can open
        // the playlist. Avoid blocking a playable channel behind a 2 KB probe.
        NSMutableArray *hls=[NSMutableArray array],*other=[NSMutableArray array];
        NSMutableSet *seen=[NSMutableSet set];
        for(NSDictionary *candidate in candidates){
            if(hls.count+other.count>=16)break;
            NSURL *url=[NSURL URLWithString:[self str:candidate[@"_url"]]];
            if(!url.host)continue;
            NSString *path=url.path.lowercaseString;
            if([path hasSuffix:@".mpd"]||[path hasSuffix:@".mkv"]||
                [path hasSuffix:@".webm"])continue;
            NSString *link=[self str:candidate[@"_url"]];
            if([seen containsObject:link])continue;
            [seen addObject:link];
            NSString *label=[self str:candidate[@"_label"]].lowercaseString;
            if([path hasSuffix:@".m3u8"]||[label containsString:@"m3u8"]||
               [label containsString:@"hls"])[hls addObject:candidate];
            else [other addObject:candidate];
        }
        NSArray *direct=[hls arrayByAddingObjectsFromArray:other];
        self.streamChecker=nil;
        if(!direct.count){
            self.liveMessage.text=@"Nenhuma URL HTTP/HLS foi retornada para este canal.";
            self.liveRetryButton.hidden=NO;return;
        }
        self.workingStreams=direct;
        self.selectedMedia=item;
        self.playingSourceIndex=0;
        self.lastPlaybackPosition=0;
        self.closingPlayer=NO;
        [self playStream:direct[0]];
        return;
    }
    [checker verify:candidates completion:^(NSArray *available,NSUInteger checked) {
        ShockViewController *strong=weakSelf;
        if(!strong||generation!=strong.streamRequestGeneration)return;
        strong.streamChecker=nil;
        NSMutableArray *order=[available mutableCopy];
        if(live){
            // Live HLS endpoints often reject Range requests or return MIME types
            // not recognized by a 2KB probe. Probe failure is not decoder failure.
            // Give the native AVPlayer its own bounded chance, verified URLs first.
            for(NSDictionary *candidate in [candidates subarrayWithRange:
                    NSMakeRange(0,MIN(candidates.count,16))]){
                BOOL present=NO;
                for(NSDictionary *tested in order)
                    if([[strong str:tested[@"_url"]] isEqualToString:
                            [strong str:candidate[@"_url"]]]){present=YES;break;}
                if(!present)[order addObject:candidate];
            }
        }
        if(!order.count){
            strong.status.text=@"Nenhuma fonte compatível respondeu corretamente.";
            [strong notice:[NSString stringWithFormat:
                @"Nenhuma das %lu fontes verificadas ofereceu uma resposta de mídia válida.",
                (unsigned long)checked]];
            return;
        }
        strong.workingStreams=order;
        strong.selectedMedia=item;
        strong.playingSourceIndex=0;
        strong.lastPlaybackPosition=0;
        strong.closingPlayer=NO;
        strong.status.text=[NSString stringWithFormat:
            @"Tentando fonte %lu de %lu%@",(unsigned long)1,(unsigned long)order.count,
            live?@" • transmissão ao vivo":@""];
        [strong playStream:order[0]];
    }];
}
- (void)cleanCurrentPlayer {
    [self removeTimeMonitor];
    if(self.hasPlayerObservation&&self.observedPlayerItem){
        @try {[self.observedPlayerItem removeObserver:self forKeyPath:@"status"
            context:kShockPlayerItemStatusContext];}
        @catch(NSException *ignored){}
        self.hasPlayerObservation=NO;
    }
    if(self.observedPlayerItem)
        [[NSNotificationCenter defaultCenter] removeObserver:self name:nil object:self.observedPlayerItem];
    self.observedPlayerItem=nil;
    self.liveVideoLayer.player=nil;
    [self.liveVideoLayer removeFromSuperlayer];
    self.liveVideoLayer=nil;
    [self.moviePlayer pause];
    if(self.playerController)self.playerController.player=nil;
    if(self.liveFullscreenController)self.liveFullscreenController.player=nil;
    self.moviePlayer=nil;
    self.playbackStarted=NO;
}
- (void)playStream:(NSDictionary *)item {
    NSURL *url=[NSURL URLWithString:[self str:item[@"_url"]]];
    if(!url.host)return;
    [self cleanCurrentPlayer];
    self.activePlayback=[item mutableCopy];
    self.playbackStarted=NO;
    BOOL live=[self isLiveItem:item];
    self.seekAfterReady=live?0:self.lastPlaybackPosition;
    if(!live&&self.seekAfterReady<=0){
        NSString *entryKey=[self keyFor:item];
        for(NSDictionary *entry in [self savedEntries]){
            if([[self keyFor:entry] isEqualToString:entryKey]&&[entry[@"position"] doubleValue]>5){
                self.seekAfterReady=[entry[@"position"] doubleValue];break;
            }
        }
    }
    AVPlayerItem *playerItem=[AVPlayerItem playerItemWithURL:url];
    self.observedPlayerItem=playerItem;
    [playerItem addObserver:self forKeyPath:@"status" options:NSKeyValueObservingOptionNew
        context:kShockPlayerItemStatusContext];
    self.hasPlayerObservation=YES;
    self.moviePlayer=[AVPlayer playerWithPlayerItem:playerItem];
    if(live){
        self.liveAttemptCount++;
        self.liveMessage.text=[NSString stringWithFormat:@"Conectando... fonte %lu de %lu",
            (unsigned long)(self.playingSourceIndex+1),
            (unsigned long)self.workingStreams.count];
        self.liveRetryButton.hidden=YES;
        // A live stream can report Ready but never deliver a playable buffer.
        // Allow extra A5 startup time and move on only when it is still stalled.
        AVPlayerItem *expected=playerItem;
        __weak ShockViewController *weakSelf=self;
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW,(int64_t)(22*NSEC_PER_SEC)),
            dispatch_get_main_queue(),^{
                ShockViewController *strong=weakSelf;
                if(!strong||strong.closingPlayer||strong.observedPlayerItem!=expected)return;
                BOOL buffered=expected.playbackBufferFull||expected.playbackLikelyToKeepUp;
                // A ready AVPlayerItem can still be black. Confirm first frame,
                // not merely a playing rate. Fullscreen uses its own video view.
                BOOL preview=[strong showingLiveGuide]&&
                    strong.presentedViewController!=strong.liveFullscreenController;
                BOOL noPicture=preview&&strong.liveVideoLayer&&!strong.liveVideoLayer.readyForDisplay;
                if(expected.status!=AVPlayerItemStatusReadyToPlay||noPicture||
                   (strong.moviePlayer.rate<=0&&!buffered)){
                    [strong recordLivePlayerFailure:expected.error stage:
                        noPicture?@"Sem imagem após conexão":@"Tempo de conexão excedido"];
                    [strong tryNextPlaybackSource];
                }
            });
    }
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(playerItemFailed:)
        name:AVPlayerItemFailedToPlayToEndTimeNotification object:playerItem];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(playerItemStalled:)
        name:AVPlayerItemPlaybackStalledNotification object:playerItem];
    if(live&&[self showingLiveGuide]){
        // Separate lightweight Telares live UI, backed by the iOS 9 hardware decoder.
        self.liveVideoLayer=[AVPlayerLayer playerLayerWithPlayer:self.moviePlayer];
        self.liveVideoLayer.frame=self.liveScreen.bounds;
        self.liveVideoLayer.videoGravity=AVLayerVideoGravityResizeAspect;
        [self.liveScreen.layer addSublayer:self.liveVideoLayer];
        self.liveFullscreen.hidden=NO;self.livePauseButton.hidden=NO;
        if(self.liveFullscreenController&&self.presentedViewController==self.liveFullscreenController)
            self.liveFullscreenController.player=self.moviePlayer;
        [self.livePauseButton setTitle:@"Pausar" forState:UIControlStateNormal];
    }else{
        if(!self.playerController){
            self.playerController=[[AVPlayerViewController alloc]init];
            self.playerController.showsPlaybackControls=YES;
            __weak ShockViewController *weakSelf=self;
            [self presentViewController:self.playerController animated:YES completion:^{
                ShockViewController *strong=weakSelf;
                if(strong&&strong.moviePlayer)[strong.moviePlayer play];
            }];
        }
        self.playerController.player=self.moviePlayer;
    }
    [self.moviePlayer play];
}
- (void)installSourceControl {
    // Use a small optional button; native AVPlayer controls remain available for touch.
    UIButton *button=[UIButton buttonWithType:UIButtonTypeCustom];
    [button setTitle:@"Fontes" forState:UIControlStateNormal];
    button.frame=CGRectMake(0,32,95,43);
    button.autoresizingMask=UIViewAutoresizingFlexibleLeftMargin;
    [button setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    button.backgroundColor=[UIColor colorWithRed:.06 green:.26 blue:.34 alpha:.85];
    button.layer.cornerRadius=10;
    button.titleLabel.font=[UIFont boldSystemFontOfSize:15];
    [button addTarget:self action:@selector(showVerifiedSources) forControlEvents:UIControlEventTouchUpInside];
    CGRect b=self.playerController.view.bounds;
    button.frame=CGRectMake(MAX(10,b.size.width-114),22,95,43);
    [self.playerController.view addSubview:button];
    self.playerSourceButton=button;
    self.playerSourceButton.hidden=self.workingStreams.count<=1;
}
- (void)showVerifiedSources {
    if(self.workingStreams.count<2||self.closingPlayer)return;
    UIAlertController *sheet=[UIAlertController alertControllerWithTitle:@"Fontes verificadas"
        message:@"Estas fontes responderam ao teste de rede; a reprodução completa ainda depende do servidor."
        preferredStyle:UIAlertControllerStyleActionSheet];
    for(NSUInteger i=0;i<self.workingStreams.count;i++){
        NSDictionary *entry=self.workingStreams[i];
        NSString *caption=[self str:entry[@"_label"]];
        if(caption.length>78)caption=[[caption substringToIndex:78]stringByAppendingString:@"…"];
        NSUInteger choice=i;
        [sheet addAction:[UIAlertAction actionWithTitle:[NSString stringWithFormat:@"%@ %@",
            (i==(NSUInteger)self.playingSourceIndex?@"✓":@"○"),caption]
            style:UIAlertActionStyleDefault handler:^(UIAlertAction *action){
                if(choice!=(NSUInteger)self.playingSourceIndex){
                    [self saveCurrentPosition];
                    self.playingSourceIndex=(NSInteger)choice;
                    [self playStream:self.workingStreams[choice]];
                }
            }]];
    }
    [sheet addAction:[UIAlertAction actionWithTitle:@"Cancelar"
        style:UIAlertActionStyleCancel handler:nil]];
    // Action sheets on iPad must specify an anchor or UIKit throws an exception.
    UIPopoverPresentationController *popover=sheet.popoverPresentationController;
    if(popover)popover.sourceView=self.playerSourceButton;
    if(popover)popover.sourceRect=self.playerSourceButton.bounds;
    [self.playerController presentViewController:sheet animated:YES completion:nil];
}
- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object
    change:(NSDictionary *)change context:(void *)context {
    if(context!=kShockPlayerItemStatusContext){
        [super observeValueForKeyPath:keyPath ofObject:object change:change context:context];
        return;
    }
    if(object!=self.observedPlayerItem||self.closingPlayer)return;
    AVPlayerItem *item=self.observedPlayerItem;
    if(item.status==AVPlayerItemStatusReadyToPlay){
        if(self.seekAfterReady>5){
            CMTime position=CMTimeMakeWithSeconds(self.seekAfterReady,600);
            self.seekAfterReady=0;
            [self.moviePlayer seekToTime:position];
        }
        if(!self.timeObserver){
            __weak ShockViewController *weakSelf=self;
            self.timeObserver=[self.moviePlayer addPeriodicTimeObserverForInterval:CMTimeMake(10,1)
                queue:dispatch_get_main_queue() usingBlock:^(CMTime time) {
                    ShockViewController *strong=weakSelf;
                    if(!strong||strong.closingPlayer||!strong.moviePlayer)return;
                    Float64 seconds=CMTimeGetSeconds(time);
                    if(isfinite(seconds)&&seconds>1){
                        strong.playbackStarted=YES;
                        if([strong isLiveItem:strong.activePlayback]){
                            if(strong.liveVideoLayer.readyForDisplay)
                                strong.liveMessage.text=@"Transmissão em reprodução.";
                        }else [strong recordProgress:seconds];
                    }
                }];
        }
        if([self isLiveItem:self.activePlayback]){
            self.liveMessage.text=@"Fonte carregada. Aguardando primeira imagem...";
            self.liveRetryButton.hidden=YES;
        }
    }else if(item.status==AVPlayerItemStatusFailed){
        [self recordLivePlayerFailure:item.error stage:@"Falha ao carregar a fonte"];
        [self tryNextPlaybackSource];
    }
}
- (void)recordLivePlayerFailure:(NSError *)error stage:(NSString *)stage {
    if(![self isLiveItem:self.activePlayback])return;
    NSString *reason=stage.length?stage:@"Falha de transmissão";
    if([error.domain isEqualToString:NSURLErrorDomain]&&
       error.code==NSURLErrorResourceUnavailable)
        reason=@"Recurso indisponível (-1008): URL, rede ou formato não utilizável";
    else if(error)
        reason=[reason stringByAppendingFormat:@" (%@, código %ld)",
            error.domain?:@"iOS",(long)error.code];
    AVPlayerItemErrorLogEvent *event=[[self.observedPlayerItem.errorLog events] lastObject];
    if(event.errorStatusCode>=400&&event.errorStatusCode<=599)
        reason=[reason stringByAppendingFormat:@" · HTTP %ld",(long)event.errorStatusCode];
    self.liveLastFailure=reason;
    // Never expose a stream URL: it can include signed access tokens.
    NSLog(@"[Telares Live] %@",reason);
}
- (void)playerItemFailed:(NSNotification *)note {
    if(note.object==self.observedPlayerItem&&!self.closingPlayer){
        [self recordLivePlayerFailure:self.observedPlayerItem.error stage:@"Interrupção"];
        [self tryNextPlaybackSource];
    }
}
- (void)playerItemStalled:(NSNotification *)note {
    if(note.object!=self.observedPlayerItem||self.closingPlayer)return;
    AVPlayerItem *stalled=self.observedPlayerItem;
    __weak ShockViewController *weakSelf=self;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW,(int64_t)(18*NSEC_PER_SEC)),
       dispatch_get_main_queue(),^{
         ShockViewController *strong=weakSelf;
         if(!strong||strong.closingPlayer||strong.observedPlayerItem!=stalled)return;
         if(strong.moviePlayer.rate<=0&&stalled.playbackBufferEmpty){
             [strong recordLivePlayerFailure:stalled.error stage:@"Buffer esgotado"];
             [strong tryNextPlaybackSource];
         }
       });
}
- (void)recoverLiveHLS:(NSDictionary *)source completed:(void (^)(NSArray *,NSString *))completion {
    NSURL *url=[NSURL URLWithString:[self str:source[@"_url"]]];
    NSString *label=[self str:source[@"_label"]].lowercaseString;
    if(!url.host||(![url.path.lowercaseString hasSuffix:@".m3u8"]&&
        ![label containsString:@"hls"]&&! [label containsString:@"m3u8"])){
        completion(@[],@"A origem não ofereceu alternativas HLS.");return;
    }
    NSMutableURLRequest *request=[NSMutableURLRequest requestWithURL:url
        cachePolicy:NSURLRequestReloadIgnoringLocalCacheData timeoutInterval:7];
    [request setValue:@"bytes=0-32767" forHTTPHeaderField:@"Range"];
    NSURLSessionConfiguration *config=[NSURLSessionConfiguration ephemeralSessionConfiguration];
    config.timeoutIntervalForResource=8;
    NSURLSession *session=[NSURLSession sessionWithConfiguration:config];
    [[session dataTaskWithRequest:request completionHandler:^(NSData *data,NSURLResponse *response,NSError *error){
        NSMutableArray *variants=[NSMutableArray array];
        NSString *reason=@"O servidor não anunciou uma variante H.264.";
        NSHTTPURLResponse *http=[response isKindOfClass:[NSHTTPURLResponse class]]?(id)response:nil;
        if(error)reason=@"Não foi possível ler a playlist da origem.";
        else if(http.statusCode>=400)
            reason=[NSString stringWithFormat:@"Servidor recusou a playlist: HTTP %ld.",(long)http.statusCode];
        else if(data.length>0&&data.length<150000){
            NSString *playlist=[[NSString alloc]initWithData:data encoding:NSUTF8StringEncoding];
            if(![playlist hasPrefix:@"#EXTM3U"])
                reason=@"A origem retornou dados que não são uma playlist HLS.";
            else if([playlist containsString:@"METHOD=SAMPLE-AES"])
                reason=@"Playlist exige criptografia ou proteção incompatível com o iOS 9.";
            else{
                NSString *meta=nil;
                NSMutableSet *seen=[NSMutableSet setWithObject:url.absoluteString];
                for(NSString *raw in [playlist componentsSeparatedByCharactersInSet:
                     [NSCharacterSet newlineCharacterSet]]){
                    NSString *line=[raw stringByTrimmingCharactersInSet:
                        [NSCharacterSet whitespaceAndNewlineCharacterSet]];
                    if([line hasPrefix:@"#EXT-X-STREAM-INF:"]){meta=line.lowercaseString;continue;}
                    if(!meta||!line.length||[line hasPrefix:@"#"])continue;
                    NSString *info=meta;meta=nil;
                    if([info containsString:@"hvc1"]||[info containsString:@"hev1"]||
                       [info containsString:@"av01"]||[info containsString:@"vp09"])continue;
                    if([info containsString:@"codecs="]&&![info containsString:@"avc1"])continue;
                    NSURL *variant=[[NSURL URLWithString:line relativeToURL:response.URL?:url] absoluteURL];
                    if(!variant.host||!([variant.scheme isEqualToString:@"https"]||
                        [variant.scheme isEqualToString:@"http"]))continue;
                    if([url.scheme isEqualToString:@"https"]&&![variant.scheme isEqualToString:@"https"])continue;
                    if([seen containsObject:variant.absoluteString])continue;
                    [seen addObject:variant.absoluteString];
                    NSMutableDictionary *next=[source mutableCopy];
                    next[@"_url"]=variant.absoluteString;
                    if([info containsString:@"640x"]||[info containsString:@"854x"]||
                       [info containsString:@"1280x"]) [variants insertObject:next atIndex:0];
                    else [variants addObject:next];
                    if(variants.count>=8)break;
                }
            }
        }
        [session finishTasksAndInvalidate];
        dispatch_async(dispatch_get_main_queue(),^{completion(variants,reason);});
    }] resume];
}
- (void)tryNextPlaybackSource {
    if(self.closingPlayer||self.liveVariantRecoveryPending)return;
    [self saveCurrentPosition];
    NSInteger next=self.playingSourceIndex+1;
    if(next<(NSInteger)self.workingStreams.count&&next<16){
        self.playingSourceIndex=next;
        self.status.text=[NSString stringWithFormat:@"Tentando alternativa %ld...",(long)(next+1)];
        if([self isLiveItem:self.activePlayback])
            self.liveMessage.text=[NSString stringWithFormat:@"Tentando fonte %ld de %lu...",
                (long)(next+1),(unsigned long)self.workingStreams.count];
        [self playStream:self.workingStreams[(NSUInteger)next]];
        return;
    }
    if([self isLiveItem:self.activePlayback]){
        if(!self.liveVariantRecoveryAttempted&&self.workingStreams.count){
            self.liveVariantRecoveryAttempted=YES;
            self.liveVariantRecoveryPending=YES;
            NSUInteger generation=self.streamRequestGeneration;
            self.liveMessage.text=@"Verificando variantes HLS...";
            __weak ShockViewController *weakSelf=self;
            [self recoverLiveHLS:self.workingStreams.lastObject completed:^(NSArray *variants,NSString *why){
                ShockViewController *strong=weakSelf;
                if(!strong||generation!=strong.streamRequestGeneration||
                   strong.closingPlayer||![strong isLiveItem:strong.activePlayback])return;
                strong.liveVariantRecoveryPending=NO;
                if(variants.count){
                    NSUInteger start=strong.workingStreams.count;
                    strong.workingStreams=[strong.workingStreams arrayByAddingObjectsFromArray:variants];
                    strong.playingSourceIndex=(NSInteger)start;
                    [strong playStream:strong.workingStreams[start]];
                }else{
                    [strong closePlayerWithError:[NSString stringWithFormat:
                        @"Canal indisponível (%lu fonte(s)). %@ %@",
                        (unsigned long)strong.workingStreams.count,
                        why?:@"",strong.liveLastFailure?:@""]];
                }
            }];
            return;
        }
        [self closePlayerWithError:[NSString stringWithFormat:
           @"Não foi possível reproduzir as %lu fonte(s). %@. Tente outro canal ou addon.",
           (unsigned long)self.workingStreams.count,
           self.liveLastFailure?:@"Origem ou formato indisponível"]];
    }else [self closePlayerWithError:
        @"Todas as fontes falharam. O formato pode não ser suportado pelo iOS 9."];
}
- (void)saveCurrentPosition {
    if(!self.playbackStarted||!self.moviePlayer||!self.activePlayback)return;
    if([self isLiveItem:self.activePlayback])return;
    Float64 current=CMTimeGetSeconds(self.moviePlayer.currentTime);
    if(isfinite(current)&&current>0){
        self.lastPlaybackPosition=current;
        [self recordProgress:current];
    }
}
- (void)closePlayerWithError:(NSString *)error {
    if(self.closingPlayer)return;
    self.closingPlayer=YES;
    [self cleanCurrentPlayer];
    __weak ShockViewController *weakSelf=self;
    BOOL embedded=[self isLiveItem:self.activePlayback]&&[self showingLiveGuide];
    if(embedded){
        [self.playerController willMoveToParentViewController:nil];
        [self.playerController.view removeFromSuperview];
        [self.playerController removeFromParentViewController];
    }
    void (^finish)(void)=^{
        ShockViewController *strong=weakSelf;
        if(!strong)return;
        strong.playerController=nil;
        strong.liveFullscreenController=nil;
        strong.playerSourceButton=nil;
        strong.activePlayback=nil;
        strong.workingStreams=nil;
        strong.closingPlayer=NO;
        if(embedded){
            strong.liveFullscreen.hidden=YES;strong.livePauseButton.hidden=YES;
            strong.liveRetryButton.hidden=NO;
            strong.liveMessage.text=error.length?error:
                @"Não foi possível iniciar este canal.";
        }else if(error.length)[strong notice:error];
    };
    if(embedded&&self.liveFullscreenController&&
       self.presentedViewController==self.liveFullscreenController)
        [self dismissViewControllerAnimated:YES completion:finish];
    else if(embedded||!self.playerController.presentingViewController)finish();
    else [self.playerController dismissViewControllerAnimated:YES completion:finish];
}
- (void)recordProgress:(double)seconds {
    if(!self.activePlayback)return;
    if([self isLiveItem:self.activePlayback])return;
    NSMutableDictionary *item=[self.activePlayback mutableCopy];
    item[@"position"]=@(seconds);
    item[@"watched"]=@YES;
    NSMutableArray *all=[[self savedEntries] mutableCopy];
    NSString *key=[self keyFor:item];
    NSIndexSet *old=[all indexesOfObjectsPassingTest:^BOOL(NSDictionary *existing,NSUInteger idx,BOOL *stop){
        return [[self keyFor:existing] isEqualToString:key];
    }];
    if(old.count){
        NSDictionary *existing=all[old.firstIndex];
        item[@"liked"]=existing[@"liked"]?:@NO;
        item[@"later"]=existing[@"later"]?:@NO;
        [all removeObjectsAtIndexes:old];
    }
    // Sensitive streaming URLs and auth tokens are not persisted in the user library.
    [item removeObjectForKey:@"_url"];
    [item removeObjectForKey:@"_label"];
    [all insertObject:item atIndex:0];
    while(all.count>150)[all removeLastObject];
    [[NSUserDefaults standardUserDefaults] setObject:all forKey:@"shock_items_v1"];
}
- (void)removeTimeMonitor {
    if(self.timeObserver&&self.moviePlayer){
        [self.moviePlayer removeTimeObserver:self.timeObserver];
    }
    self.timeObserver=nil;
}
- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    if([self showingLiveGuide]&&self.moviePlayer&&self.liveVideoLayer&&
       !self.presentedViewController){
        self.liveVideoLayer.player=self.moviePlayer;
        self.liveFullscreenController=nil;
    }
    // User left native AVPlayer controller with Done/Back: release all media resources.
    if(self.playerController&&self.playerController.parentViewController!=self&&
       !self.presentedViewController&&!self.closingPlayer){
        [self saveCurrentPosition];
        [self cleanCurrentPlayer];
        self.playerController=nil;
        self.playerSourceButton=nil;
        self.activePlayback=nil;
        self.workingStreams=nil;
        self.selectedMedia=nil;
    }
}
- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [self saveCurrentPosition];
}
- (void)dealloc {
    [NSObject cancelPreviousPerformRequestsWithTarget:self selector:@selector(beginSearch) object:nil];
    [self.streamChecker cancel];
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    [self cleanCurrentPlayer];
}
- (BOOL)prefersStatusBarHidden{return YES;}
- (BOOL)shouldAutorotate{return YES;}
- (UIInterfaceOrientationMask)supportedInterfaceOrientations{return UIInterfaceOrientationMaskAll;}
@end
