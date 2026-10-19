#import "TelaresSubtitles.h"
#import <math.h>
@interface TelaresSubtitles ()
@property(nonatomic,weak) AVPlayer *player;
@property(nonatomic,weak) UIViewController *controller;
@property(nonatomic,strong) UILabel *caption;
@property(nonatomic,strong) UIButton *button;
@property(nonatomic,strong) NSTimer *timer;
@property(nonatomic,strong) NSArray *tracks;
@property(nonatomic,strong) NSArray *cues;
@property(nonatomic,strong) NSURLSession *session;
@property(nonatomic,assign) NSInteger generation;
@property(nonatomic,assign) double delay;
@property(nonatomic,assign) BOOL closed;
@property(nonatomic,copy) NSString *status;
@end
@implementation TelaresSubtitles
- (NSString *)string:(id)v{return [v isKindOfClass:[NSString class]]?v:@"";}
- (NSInteger)rank:(NSString *)lang {
    NSString *s=[[lang lowercaseString]stringByReplacingOccurrencesOfString:@"_" withString:@"-"];
    return [@[@"pob",@"pt-br",@"por-br"]containsObject:s]?0:[@[@"por",@"pt",@"pt-pt"]containsObject:s]?1:2;
}
- (BOOL)http:(NSString *)value {
    NSURL *u=[NSURL URLWithString:value];return u.host.length&&!u.user&&
        ([@"https" isEqualToString:u.scheme.lowercaseString]||[@"http" isEqualToString:u.scheme.lowercaseString]);
}
- (void)json:(NSString *)value done:(void(^)(NSDictionary *))done {
    if(![self http:value]){done(@{});return;}
    [[self.session dataTaskWithURL:[NSURL URLWithString:value] completionHandler:^(NSData *data,NSURLResponse *response,NSError *error){
        NSDictionary *doc=nil;
        if(!error&&data.length<2000000&&[(NSHTTPURLResponse *)response statusCode]==200){
            id result=[NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
            if([result isKindOfClass:[NSDictionary class]])doc=result;
        }
        dispatch_async(dispatch_get_main_queue(),^{if(!self.closed)done(doc?:@{});});
    }]resume];
}
- (instancetype)initWithPlayer:(AVPlayer *)player controller:(UIViewController *)controller
    sources:(NSArray *)sources type:(NSString *)type identifier:(NSString *)identifier {
    if((self=[super init])){
        self.player=player;self.controller=controller;self.tracks=@[];self.cues=@[];
        self.status=@"Consultando legendas. OpenSubtitles Pro pode exigir configuração em Fontes.";
        NSURLSessionConfiguration *config=[NSURLSessionConfiguration ephemeralSessionConfiguration];
        config.timeoutIntervalForRequest=6;config.timeoutIntervalForResource=10;
        self.session=[NSURLSession sessionWithConfiguration:config];
        UIView *view=controller.view;
        self.caption=[[UILabel alloc]initWithFrame:CGRectMake(30,view.bounds.size.height-135,view.bounds.size.width-60,85)];
        self.caption.autoresizingMask=UIViewAutoresizingFlexibleWidth|UIViewAutoresizingFlexibleTopMargin;
        self.caption.textColor=[UIColor whiteColor];self.caption.backgroundColor=[UIColor clearColor];
        self.caption.shadowColor=[UIColor blackColor];self.caption.shadowOffset=CGSizeMake(2,2);
        self.caption.font=[UIFont boldSystemFontOfSize:23];self.caption.textAlignment=NSTextAlignmentCenter;self.caption.numberOfLines=3;
        self.caption.userInteractionEnabled=NO;[view addSubview:self.caption];
        self.button=[UIButton buttonWithType:UIButtonTypeSystem];self.button.frame=CGRectMake(22,70,125,40);
        self.button.backgroundColor=[UIColor colorWithWhite:0 alpha:.65];[self.button setTitle:@"Legendas" forState:UIControlStateNormal];
        [self.button setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
        [self.button addTarget:self action:@selector(show) forControlEvents:UIControlEventTouchUpInside];[view addSubview:self.button];
        self.timer=[NSTimer scheduledTimerWithTimeInterval:.2 target:self selector:@selector(tick:) userInfo:nil repeats:YES];
        [self load:sources index:0 type:type identifier:identifier];
    }return self;
}
- (void)load:(NSArray *)sources index:(NSUInteger)index type:(NSString *)type identifier:(NSString *)identifier {
    if(self.closed||index>=sources.count){
        if(self.tracks.count&&[self rank:[self string:self.tracks[0][@"lang"]]]==0)[self select:0];return;
    }
    NSString *addon=[self string:sources[index]];
    [self json:addon done:^(NSDictionary *manifest){
        BOOL supports=NO;
        for(id r in manifest[@"resources"]){
            NSDictionary *qualified=[r isKindOfClass:[NSDictionary class]]?r:nil;
            NSString *name=qualified?qualified[@"name"]:r;
            if(![name isEqual:@"subtitles"])continue;
            NSArray *types=qualified[@"types"]?:manifest[@"types"];
            NSArray *prefixes=qualified[@"idPrefixes"]?:manifest[@"idPrefixes"];
            BOOL prefix=!prefixes.count;for(NSString *p in prefixes)if([identifier hasPrefix:p])prefix=YES;
            supports=(!types.count||[types containsObject:type])&&prefix;if(supports)break;
        }
        if(!supports||[manifest[@"behaviorHints"][@"configurationRequired"]boolValue]){
            [self load:sources index:index+1 type:type identifier:identifier];return;
        }
        NSString *base=[addon substringToIndex:addon.length-@"manifest.json".length];
        NSCharacterSet *chars=[NSCharacterSet URLPathAllowedCharacterSet];
        NSString *url=[NSString stringWithFormat:@"%@subtitles/%@/%@.json",base,
            [type stringByAddingPercentEncodingWithAllowedCharacters:chars],
            [identifier stringByAddingPercentEncodingWithAllowedCharacters:chars]];
        [self json:url done:^(NSDictionary *doc){
            NSMutableArray *all=[self.tracks mutableCopy];
            for(id track in doc[@"subtitles"])if([track isKindOfClass:[NSDictionary class]]&&
                [self http:[self string:track[@"url"]]]&&all.count<100)[all addObject:track];
            [all sortUsingComparator:^NSComparisonResult(NSDictionary *a,NSDictionary *b){
                NSInteger x=[self rank:[self string:a[@"lang"]]],y=[self rank:[self string:b[@"lang"]]];
                return x<y?NSOrderedAscending:x>y?NSOrderedDescending:NSOrderedSame;
            }];self.tracks=all;
            [self load:sources index:index+1 type:type identifier:identifier];
        }];
    }];
}
- (double)time:(NSString *)value {
    NSRegularExpression *regex=[NSRegularExpression regularExpressionWithPattern:@"(?:(\\d{1,3}):)?(\\d{2}):(\\d{2})[,.](\\d{3})" options:0 error:nil];
    NSTextCheckingResult *match=[regex firstMatchInString:value options:0 range:NSMakeRange(0,value.length)];
    if(!match)return -1;double parts[4]={0,0,0,0};
    for(NSUInteger i=1;i<=4;i++){NSRange r=[match rangeAtIndex:i];if(r.location!=NSNotFound)parts[i-1]=[[value substringWithRange:r]doubleValue];}
    return parts[0]*3600+parts[1]*60+parts[2]+parts[3]/1000;
}
- (void)select:(NSUInteger)index {
    NSInteger token=++self.generation;self.cues=@[];self.caption.text=@"";
    AVMediaSelectionGroup *internal=[self.player.currentItem.asset mediaSelectionGroupForMediaCharacteristic:AVMediaCharacteristicLegible];
    if(internal)[self.player.currentItem selectMediaOption:nil inMediaSelectionGroup:internal];
    NSString *url=[self string:self.tracks[index][@"url"]];
    [[self.session dataTaskWithURL:[NSURL URLWithString:url]completionHandler:^(NSData *data,NSURLResponse *response,NSError *error){
        NSMutableArray *cues=[NSMutableArray array];
        if(!error&&data.length<2000000&&[(NSHTTPURLResponse *)response statusCode]==200){
            NSString *text=[[NSString alloc]initWithData:data encoding:NSUTF8StringEncoding];
            if(!text)text=[[NSString alloc]initWithData:data encoding:NSISOLatin1StringEncoding];
            text=[text stringByReplacingOccurrencesOfString:@"\r" withString:@""];
            for(NSString *block in [text componentsSeparatedByString:@"\n\n"]){
                NSArray *lines=[block componentsSeparatedByString:@"\n"];
                for(NSUInteger n=0;n<lines.count;n++)if([lines[n]containsString:@"-->"]){
                    NSArray *pair=[lines[n]componentsSeparatedByString:@"-->"];
                    double start=[self time:pair[0]],end=[self time:pair[1]];
                    if(start<0||end<=start)break;
                    NSString *caption=[[lines subarrayWithRange:NSMakeRange(n+1,lines.count-n-1)]componentsJoinedByString:@"\n"];
                    caption=[caption stringByReplacingOccurrencesOfString:@"<[^>]+>" withString:@"" options:NSRegularExpressionSearch range:NSMakeRange(0,caption.length)];
                    [cues addObject:@{ @"start":@(start),@"end":@(end),@"text":caption }];break;
                }if(cues.count>=30000)break;
            }
        }
        dispatch_async(dispatch_get_main_queue(),^{if(!self.closed&&token==self.generation){self.cues=cues;
            self.status=cues.count?@"Legenda carregada. Ajuste positivo atrasa a legenda.":@"Não foi possível carregar SRT/WebVTT desta fonte.";}});
    }]resume];
}
- (void)tick:(NSTimer *)timer {
    double time=CMTimeGetSeconds(self.player.currentTime)-self.delay;
    NSMutableArray *lines=[NSMutableArray array];
    if(isfinite(time))for(NSDictionary *cue in self.cues)if([cue[@"start"]doubleValue]<=time&&time<[cue[@"end"]doubleValue])[lines addObject:cue[@"text"]];
    self.caption.text=[lines componentsJoinedByString:@"\n"];
}
- (void)show {
    UIAlertController *sheet=[UIAlertController alertControllerWithTitle:@"Legendas · PT-BR preferencial"
        message:self.status preferredStyle:UIAlertControllerStyleAlert];
    [sheet addAction:[UIAlertAction actionWithTitle:@"Desativar externas" style:UIAlertActionStyleDefault handler:^(UIAlertAction *a){self.generation++;self.cues=@[];self.caption.text=@"";}]];
    AVMediaSelectionGroup *group=[self.player.currentItem.asset mediaSelectionGroupForMediaCharacteristic:AVMediaCharacteristicLegible];
    for(AVMediaSelectionOption *option in group.options)[sheet addAction:[UIAlertAction actionWithTitle:[@"Interna · " stringByAppendingString:option.displayName]style:UIAlertActionStyleDefault handler:^(UIAlertAction *a){self.generation++;self.cues=@[];[self.player.currentItem selectMediaOption:option inMediaSelectionGroup:group];}]];
    for(NSUInteger i=0;i<self.tracks.count;i++){
        NSDictionary *track=self.tracks[i];NSUInteger selected=i;
        NSString *label=[NSString stringWithFormat:@"%@ · %@",[self string:track[@"lang"]],[self string:track[@"label"]?:track[@"id"]]];
        [sheet addAction:[UIAlertAction actionWithTitle:label style:UIAlertActionStyleDefault handler:^(UIAlertAction *a){[self select:selected];}]];
    }
    [sheet addAction:[UIAlertAction actionWithTitle:[NSString stringWithFormat:@"Antecipar 0,5s (%.1fs)",self.delay]style:UIAlertActionStyleDefault handler:^(UIAlertAction *a){self.delay=MAX(-60,self.delay-.5);}]];
    [sheet addAction:[UIAlertAction actionWithTitle:@"Atrasar 0,5s" style:UIAlertActionStyleDefault handler:^(UIAlertAction *a){self.delay=MIN(60,self.delay+.5);}]];
    [sheet addAction:[UIAlertAction actionWithTitle:@"Zerar atraso" style:UIAlertActionStyleDefault handler:^(UIAlertAction *a){self.delay=0;}]];
    [sheet addAction:[UIAlertAction actionWithTitle:@"Fechar" style:UIAlertActionStyleCancel handler:nil]];
    [self.controller presentViewController:sheet animated:YES completion:nil];
}
- (void)close {self.closed=YES;self.generation++;[self.timer invalidate];self.timer=nil;[self.session invalidateAndCancel];[self.caption removeFromSuperview];[self.button removeFromSuperview];}
@end
