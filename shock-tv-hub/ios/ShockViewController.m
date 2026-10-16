#import "ShockViewController.h"
#import <AVFoundation/AVFoundation.h>
#import <AVKit/AVKit.h>
#import <CoreMedia/CoreMedia.h>

static NSString * const kDefaultCinemeta = @"https://v3-cinemeta.strem.io/manifest.json";
static NSString * const kDefaultFenix = @"https://fenixflix.fenixhub.online/manifest.json";
static NSString * const kDefaultTorrentio = @"https://torrentio.strem.fun/manifest.json";
static NSString * const kDefaultNetflix = @"https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json";
static NSString * const kDefaultPirate = @"https://thepiratebay-plus.strem.fun/manifest.json";

@interface ShockViewController () <UITableViewDelegate, UITableViewDataSource, UISearchBarDelegate>
@property(nonatomic,strong) UITableView *table;
@property(nonatomic,strong) UISearchBar *search;
@property(nonatomic,strong) UISegmentedControl *sections;
@property(nonatomic,strong) UILabel *status;
@property(nonatomic,strong) NSMutableArray *sources;
@property(nonatomic,strong) NSMutableDictionary *manifests;
@property(nonatomic,strong) NSMutableArray *catalogItems;
@property(nonatomic,strong) NSMutableArray *results;
@property(nonatomic,strong) NSMutableDictionary *posterCache;
@property(nonatomic,strong) NSMutableSet *knownIDs;
@property(nonatomic,copy) NSString *filter;
@property(nonatomic,copy) NSString *mode;
@property(nonatomic,copy) NSString *currentTerm;
@property(nonatomic,strong) NSDictionary *activeSeries;
@property(nonatomic,strong) AVPlayer *moviePlayer;
@property(nonatomic,strong) AVPlayerViewController *playerController;
@property(nonatomic,strong) id timeObserver;
@property(nonatomic,strong) NSMutableDictionary *activePlayback;
@property(nonatomic,assign) NSInteger taskGeneration;
@property(nonatomic,assign) NSInteger pending;
@end

@implementation ShockViewController

- (UIColor *)dark { return [UIColor colorWithRed:0.037 green:0.071 blue:0.12 alpha:1]; }
- (UIColor *)accent { return [UIColor colorWithRed:0.39 green:0.84 blue:0.91 alpha:1]; }
- (NSArray *)defaultSources {
    return @[kDefaultCinemeta,kDefaultFenix,kDefaultNetflix,kDefaultTorrentio,kDefaultPirate];
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
    self.title = @"SHOCK TV";
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
    self.navigationItem.rightBarButtonItem =
        [[UIBarButtonItem alloc] initWithTitle:@"Fontes" style:UIBarButtonItemStylePlain
                                      target:self action:@selector(showSources)];

    self.sections = [[UISegmentedControl alloc] initWithItems:@[@"Início",@"Filmes",@"Séries",@"Minha lista"]];
    self.sections.selectedSegmentIndex = 0;
    self.sections.tintColor = [self accent];
    [self.sections addTarget:self action:@selector(changeSection) forControlEvents:UIControlEventValueChanged];
    [self.view addSubview:self.sections];

    self.search = [[UISearchBar alloc] initWithFrame:CGRectZero];
    self.search.delegate = self;
    self.search.barTintColor = [self dark];
    self.search.tintColor = [self accent];
    self.search.placeholder = @"Buscar filmes e séries nos addons";
    self.search.autocapitalizationType = UITextAutocapitalizationTypeNone;
    self.search.returnKeyType = UIReturnKeySearch;
    [self.view addSubview:self.search];

    self.table = [[UITableView alloc] initWithFrame:CGRectZero style:UITableViewStylePlain];
    self.table.delegate = self;
    self.table.dataSource = self;
    self.table.rowHeight = 88;
    self.table.backgroundColor = [self dark];
    self.table.separatorColor = [UIColor colorWithWhite:0.22 alpha:1];
    self.table.tableFooterView = [[UIView alloc] initWithFrame:CGRectZero];
    [self.view addSubview:self.table];

    self.status = [[UILabel alloc] initWithFrame:CGRectZero];
    self.status.font = [UIFont systemFontOfSize:12];
    self.status.textColor = [UIColor colorWithWhite:0.75 alpha:1];
    self.status.textAlignment = NSTextAlignmentLeft;
    self.status.text = @"Buscando catálogos...";
    [self.view addSubview:self.status];
    [self loadCatalogs];
}
- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    CGFloat width = self.view.bounds.size.width, height = self.view.bounds.size.height;
    self.sections.frame = CGRectMake(14,12,width-28,36);
    self.search.frame = CGRectMake(6,51,width-12,50);
    self.status.frame = CGRectMake(20,height-34,width-40,25);
    self.table.frame = CGRectMake(0,103,width,height-141);
}
- (NSString *)keyFor:(NSDictionary *)item {
    NSString *type = [self str:item[@"type"]];
    NSString *ident = [self str:item[@"id"]];
    NSString *episode = [self str:item[@"_episode"]];
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
        finish(nil,[NSError errorWithDomain:@"ShockTV" code:-1
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
                error = [NSError errorWithDomain:@"ShockTV.HTTP" code:http.statusCode
                    userInfo:@{NSLocalizedDescriptionKey:@"O servidor não respondeu com sucesso."}];
            if (!error && (data.length == 0 || data.length > 2000000))
                error = [NSError errorWithDomain:@"ShockTV.JSON" code:1
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
                if (fetched >= 5) break;
                NSString *type = [self str:catalog[@"type"]];
                NSString *ident = [self str:catalog[@"id"]];
                if ((! [type isEqualToString:@"movie"] && ![type isEqualToString:@"series"])
                    || !ident.length) continue;
                fetched++;
                NSString *endpoint = [NSString stringWithFormat:@"%@catalog/%@/%@.json",
                    base,[self pathEncode:type],[self pathEncode:ident]];
                [self requestJSON:endpoint complete:^(NSDictionary *payload,NSError *error) {
                    if (generation != self.taskGeneration || error) return;
                    NSMutableArray *found = [NSMutableArray array];
                    for (NSDictionary *raw in [self arr:payload[@"metas"]]) {
                        NSDictionary *media = [self mediaFrom:raw type:type base:base];
                        if(media)[found addObject:media];
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
    self.filter = [@[@"Início",@"Filmes",@"Séries",@"Minha lista"] objectAtIndex:
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
    for (NSString *name in @[@"id",@"name",@"type",@"poster",@"description",@"_base",@"_episode"])
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
        [self.table reloadData];return;
    }
    [self.results removeAllObjects];
    NSArray *items = self.currentTerm.length>=2 ? self.catalogItems : self.catalogItems;
    if ([self.filter isEqualToString:@"Minha lista"]) {
        for(NSDictionary *item in [self savedEntries])
            if ([item[@"liked"] boolValue]||[item[@"later"] boolValue]||[item[@"watched"] boolValue])
                [self.results addObject:item];
    } else {
        for (NSDictionary *item in items) {
            NSString *type = [self str:item[@"type"]];
            if ([self.filter isEqualToString:@"Filmes"] && ![type isEqualToString:@"movie"])continue;
            if ([self.filter isEqualToString:@"Séries"] && ![type isEqualToString:@"series"])continue;
            if (self.currentTerm.length>=2 &&
                [[self str:item[@"name"]].lowercaseString rangeOfString:self.currentTerm.lowercaseString].location
                    == NSNotFound)continue;
            [self.results addObject:item];
        }
    }
    self.status.text = [NSString stringWithFormat:@"%lu título(s) • iPad iOS 9 • mídia HTTP/HLS",
                        (unsigned long)self.results.count];
    [self.table reloadData];
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
    UIAlertController *alert=[UIAlertController alertControllerWithTitle:@"Shock TV" message:message
        preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}
- (NSInteger)tableView:(UITableView *)table numberOfRowsInSection:(NSInteger)section {
    return (NSInteger)self.results.count;
}
- (UITableViewCell *)tableView:(UITableView *)table cellForRowAtIndexPath:(NSIndexPath *)path {
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
    cell.imageView.image=nil;
    NSString *url=[self str:item[@"poster"]];
    if (url.length && ![self.mode isEqualToString:@"streams"]){
        UIImage *cached=self.posterCache[url];
        if(cached)cell.imageView.image=cached;
        else if([url hasPrefix:@"https://"]){
            NSURL *imageURL=[NSURL URLWithString:url];
            NSURLSessionDataTask *task=[[NSURLSession sharedSession]
                dataTaskWithURL:imageURL completionHandler:^(NSData *data,NSURLResponse *response,NSError *error) {
                    if (error||data.length>1800000)return;
                    UIImage *img=[UIImage imageWithData:data];
                    if(!img)return;
                    dispatch_async(dispatch_get_main_queue(),^{
                        if(self.posterCache.count>40)[self.posterCache removeAllObjects];
                        self.posterCache[url]=img;
                        [self.table reloadRowsAtIndexPaths:@[path] withRowAnimation:UITableViewRowAnimationNone];
                    });
                }];
            [task resume];
        }
    }
    return cell;
}
- (void)tableView:(UITableView *)table didSelectRowAtIndexPath:(NSIndexPath *)path {
    [table deselectRowAtIndexPath:path animated:YES];
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
    NSString *name=[self str:item[@"name"]];
    NSString *summary=[self str:item[@"description"]];
    if(summary.length>550)summary=[[summary substringToIndex:550] stringByAppendingString:@"…"];
    UIAlertController *sheet=[UIAlertController alertControllerWithTitle:name
        message:summary.length?summary:@"Selecione uma opção para este título."
        preferredStyle:UIAlertControllerStyleAlert];
    [sheet addAction:[UIAlertAction actionWithTitle:@"▶  Assistir" style:UIAlertActionStyleDefault
        handler:^(UIAlertAction *action){
            if([[self str:item[@"type"]] isEqualToString:@"series"]&&![self str:item[@"_episode"]].length)
                [self resolveEpisodes:item];
            else [self resolveStreams:item];
        }]];
    BOOL liked=[self isMarked:item field:@"liked"],later=[self isMarked:item field:@"later"];
    [sheet addAction:[UIAlertAction actionWithTitle:liked?@"♥  Remover curtido":@"♡  Curtir"
        style:UIAlertActionStyleDefault handler:^(UIAlertAction *action){[self saveEntry:item update:@"liked"];}]];
    [sheet addAction:[UIAlertAction actionWithTitle:later?@"✓  Tirar de assistir mais tarde":@"＋  Assistir mais tarde"
        style:UIAlertActionStyleDefault handler:^(UIAlertAction *action){[self saveEntry:item update:@"later"];}]];
    [sheet addAction:[UIAlertAction actionWithTitle:@"Voltar" style:UIAlertActionStyleCancel handler:nil]];
    [self presentViewController:sheet animated:YES completion:nil];
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
            self.mode=@"episodes";
            self.title=@"Episódios";
            self.navigationItem.leftBarButtonItem=[[UIBarButtonItem alloc] initWithTitle:@"Voltar"
                style:UIBarButtonItemStylePlain target:self action:@selector(exitSpecialList)];
            self.status.text=[NSString stringWithFormat:@"%lu episódios encontrados",(unsigned long)self.results.count];
            [self.table reloadData];
        }];
    }];
}
- (void)resolveStreams:(NSDictionary *)item {
    NSString *type=[self str:item[@"type"]];
    NSString *ident=[self str:item[@"_episode"]];
    if(!ident.length)ident=[self str:item[@"id"]];
    self.status.text=@"Consultando streams dos addons...";
    NSMutableArray *collected=[NSMutableArray array];
    [self checkStreamSources:[self.sources copy] index:0 item:item
                 type:type identifier:ident found:collected];
}
- (void)checkStreamSources:(NSArray *)all index:(NSUInteger)index item:(NSDictionary *)item
                      type:(NSString *)type identifier:(NSString *)ident found:(NSMutableArray *)found {
    if(index>=all.count){
        if(!found.count){
            [self notice:@"Nenhuma mídia HTTP/HLS compatível foi retornada. Torrents não são reproduzidos pelo AVPlayer deste iPad."];
            return;
        }
        [self.results removeAllObjects];
        [self.results addObjectsFromArray:found];
        self.mode=@"streams";
        self.title=@"Opções de reprodução";
        self.navigationItem.leftBarButtonItem=[[UIBarButtonItem alloc] initWithTitle:@"Voltar"
            style:UIBarButtonItemStylePlain target:self action:@selector(exitSpecialList)];
        self.status.text=[NSString stringWithFormat:@"%lu fonte(s) HTTP(S)",(unsigned long)found.count];
        [self.table reloadData];
        return;
    }
    NSString *source=[self str:all[index]];
    [self manifest:source done:^(NSDictionary *manifest){
        if(![self manifest:manifest resource:@"stream" type:type identifier:ident]){
            [self checkStreamSources:all index:index+1 item:item type:type identifier:ident found:found];
            return;
        }
        NSString *base=[source substringToIndex:source.length-@"manifest.json".length];
        NSString *endpoint=[NSString stringWithFormat:@"%@stream/%@/%@.json",
            base,[self pathEncode:type],[self pathEncode:ident]];
        [self requestJSON:endpoint complete:^(NSDictionary *payload,NSError *error){
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
                // AVPlayer handles MP4 and compatible HLS. Source-protected headers and BitTorrent are not supported.
                [found addObject:data];
                if(found.count>=28)break;
            }
            [self checkStreamSources:all index:index+1 item:item type:type identifier:ident found:found];
        }];
    }];
}
- (void)exitSpecialList {
    self.title=@"SHOCK TV";
    self.mode=@"catalog";
    self.navigationItem.leftBarButtonItem=nil;
    self.activeSeries=nil;
    if(self.currentTerm.length>=2)[self beginSearch];
    else [self loadCatalogs];
}
- (void)playStream:(NSDictionary *)item {
    NSString *urlString=[self str:item[@"_url"]];
    NSURL *url=[NSURL URLWithString:urlString];
    if(!url||!url.host){[self notice:@"Endereço de reprodução inválido."];return;}
    NSString *ext=url.pathExtension.lowercaseString;
    if(![@[@"m3u8",@"mp4",@"m4v",@"mov",@"mp3",@"aac"] containsObject:ext]&&
       ![urlString containsString:@"m3u8"]){
        // Some CDN endpoints omit extensions. AVPlayer may still recognize valid HLS by Content-Type.
        self.status.text=@"Testando compatibilidade de mídia...";
    }
    self.activePlayback=[item mutableCopy];
    self.moviePlayer=[AVPlayer playerWithURL:url];
    self.playerController=[[AVPlayerViewController alloc] init];
    self.playerController.player=self.moviePlayer;
    NSString *entryKey=[self keyFor:item];
    for(NSDictionary *entry in [self savedEntries])
        if([[self keyFor:entry] isEqualToString:entryKey]&&[entry[@"position"] doubleValue]>5){
            CMTime resume=CMTimeMakeWithSeconds([entry[@"position"] doubleValue],600);
            [self.moviePlayer seekToTime:resume];
            break;
        }
    __weak ShockViewController *weakSelf=self;
    [self presentViewController:self.playerController animated:YES completion:^{
        [weakSelf.moviePlayer play];
    }];
    [self removeTimeMonitor];
    self.timeObserver=[self.moviePlayer addPeriodicTimeObserverForInterval:CMTimeMake(10,1)
        queue:dispatch_get_main_queue() usingBlock:^(CMTime time){
            ShockViewController *strong=weakSelf;
            if(!strong)return;
            Float64 current=CMTimeGetSeconds(time);
            if(!isfinite(current)||current<1)return;
            [strong recordProgress:current];
        }];
}
- (void)recordProgress:(double)seconds {
    if(!self.activePlayback)return;
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
    if(self.timeObserver && self.moviePlayer){
        [self.moviePlayer removeTimeObserver:self.timeObserver];
        self.timeObserver=nil;
    }
}
- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    if(self.moviePlayer && self.activePlayback){
        Float64 position=CMTimeGetSeconds(self.moviePlayer.currentTime);
        if(isfinite(position)&&position>0)[self recordProgress:position];
    }
}
- (void)dealloc {
    [NSObject cancelPreviousPerformRequestsWithTarget:self selector:@selector(beginSearch) object:nil];
    [self removeTimeMonitor];
}
- (BOOL)prefersStatusBarHidden{return YES;}
- (BOOL)shouldAutorotate{return YES;}
- (UIInterfaceOrientationMask)supportedInterfaceOrientations{return UIInterfaceOrientationMaskAll;}
@end
