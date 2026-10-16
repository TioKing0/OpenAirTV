#import "ShockDetailViewController.h"
#import <QuartzCore/QuartzCore.h>

@interface ShockDetailViewController ()
@property(nonatomic,strong) NSDictionary *media;
@property(nonatomic,strong) UIScrollView *scroll;
@property(nonatomic,strong) UIImageView *poster;
@property(nonatomic,strong) UILabel *nameLabel;
@property(nonatomic,strong) UILabel *typeLabel;
@property(nonatomic,strong) UILabel *descriptionLabel;
@property(nonatomic,strong) UIButton *playButton;
@property(nonatomic,strong) UIButton *likeButton;
@property(nonatomic,strong) UIButton *laterButton;
@property(nonatomic,assign) BOOL liked;
@property(nonatomic,assign) BOOL later;
@end

@implementation ShockDetailViewController
- (instancetype)initWithMedia:(NSDictionary *)media liked:(BOOL)liked later:(BOOL)later {
    if((self=[super init])){
        _media=media;
        _liked=liked;_later=later;
    }
    return self;
}
- (NSString *)string:(id)obj {
    return [obj isKindOfClass:[NSString class]]?obj:@"";
}
- (UIButton *)button:(NSString *)title action:(SEL)action strong:(BOOL)strong {
    UIButton *b=[UIButton buttonWithType:UIButtonTypeCustom];
    [b setTitle:title forState:UIControlStateNormal];
    b.titleLabel.font=[UIFont boldSystemFontOfSize:16];
    b.titleLabel.adjustsFontSizeToFitWidth=YES;
    b.titleLabel.minimumScaleFactor=.8;
    b.layer.cornerRadius=10;
    b.clipsToBounds=YES;
    b.backgroundColor=strong?
      [UIColor colorWithRed:.26 green:.70 blue:.80 alpha:1]:
      [UIColor colorWithRed:.11 green:.27 blue:.35 alpha:1];
    [b setTitleColor:strong?[UIColor colorWithRed:.02 green:.10 blue:.15 alpha:1]:
      [UIColor whiteColor] forState:UIControlStateNormal];
    [b addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];
    return b;
}
- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor=[UIColor colorWithRed:.03 green:.07 blue:.12 alpha:1];
    self.title=@"Detalhes";
    self.navigationController.navigationBar.barStyle=UIBarStyleBlack;
    self.navigationController.navigationBar.barTintColor=self.view.backgroundColor;
    self.navigationController.navigationBar.tintColor=[UIColor colorWithRed:.43 green:.84 blue:.91 alpha:1];
    self.navigationItem.leftBarButtonItem=[[UIBarButtonItem alloc]initWithTitle:@"‹ Voltar"
        style:UIBarButtonItemStylePlain target:self action:@selector(close)];
    self.scroll=[[UIScrollView alloc]initWithFrame:self.view.bounds];
    self.scroll.autoresizingMask=UIViewAutoresizingFlexibleWidth|UIViewAutoresizingFlexibleHeight;
    self.scroll.backgroundColor=self.view.backgroundColor;
    [self.view addSubview:self.scroll];

    self.poster=[[UIImageView alloc]initWithFrame:CGRectZero];
    self.poster.contentMode=UIViewContentModeScaleAspectFill;
    self.poster.clipsToBounds=YES;
    self.poster.backgroundColor=[UIColor colorWithRed:.12 green:.23 blue:.30 alpha:1];
    self.poster.layer.cornerRadius=12;
    [self.scroll addSubview:self.poster];

    self.typeLabel=[[UILabel alloc]initWithFrame:CGRectZero];
    self.typeLabel.text=[[self string:self.media[@"type"]] isEqualToString:@"series"]?
       @"SÉRIE  ·  SHOCK TV":@"FILME  ·  SHOCK TV";
    self.typeLabel.textColor=[UIColor colorWithRed:.42 green:.83 blue:.89 alpha:1];
    self.typeLabel.font=[UIFont boldSystemFontOfSize:13];
    [self.scroll addSubview:self.typeLabel];

    self.nameLabel=[[UILabel alloc]initWithFrame:CGRectZero];
    self.nameLabel.font=[UIFont boldSystemFontOfSize:30];
    self.nameLabel.textColor=[UIColor whiteColor];
    self.nameLabel.numberOfLines=3;
    self.nameLabel.text=[self string:self.media[@"name"]];
    [self.scroll addSubview:self.nameLabel];

    self.descriptionLabel=[[UILabel alloc]initWithFrame:CGRectZero];
    self.descriptionLabel.font=[UIFont systemFontOfSize:16];
    self.descriptionLabel.textColor=[UIColor colorWithWhite:.84 alpha:1];
    self.descriptionLabel.numberOfLines=0;
    NSString *description=[self string:self.media[@"description"]];
    self.descriptionLabel.text=description.length?description:
         @"Não há sinopse disponível para este título.";
    [self.scroll addSubview:self.descriptionLabel];

    self.playButton=[self button:@"▶  Assistir" action:@selector(play) strong:YES];
    self.likeButton=[self button:@"" action:@selector(like) strong:NO];
    self.laterButton=[self button:@"" action:@selector(toggleLater) strong:NO];
    [self.scroll addSubview:self.playButton];
    [self.scroll addSubview:self.likeButton];
    [self.scroll addSubview:self.laterButton];
    [self updateActions];
    NSString *url=[self string:self.media[@"poster"]];
    if([url hasPrefix:@"https://"]){
        __weak ShockDetailViewController *weakSelf=self;
        [[[NSURLSession sharedSession] dataTaskWithURL:[NSURL URLWithString:url]
            completionHandler:^(NSData *data,NSURLResponse *response,NSError *error){
                if(error||data.length>1600000||data.length==0)return;
                UIImage *image=[UIImage imageWithData:data];
                if(image)dispatch_async(dispatch_get_main_queue(),^{
                    weakSelf.poster.image=image;
                });
            }] resume];
    }
}
- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    CGFloat w=self.view.bounds.size.width;
    BOOL landscape=w>self.view.bounds.size.height;
    CGFloat margin=landscape?35:24;
    CGFloat posterWidth=landscape?MIN(270,w*.29):MIN(235,w*.42);
    CGFloat posterHeight=posterWidth*1.49;
    self.poster.frame=CGRectMake(margin,26,posterWidth,posterHeight);
    CGFloat textX=landscape?margin+posterWidth+28:margin;
    CGFloat textY=landscape?29:posterHeight+54;
    CGFloat textWidth=w-textX-margin;
    self.typeLabel.frame=CGRectMake(textX,textY,textWidth,22);
    self.nameLabel.frame=CGRectMake(textX,textY+26,textWidth,landscape?112:92);
    CGSize summarySize=[self.descriptionLabel sizeThatFits:CGSizeMake(textWidth,CGFLOAT_MAX)];
    CGFloat summaryY=textY+(landscape?145:125);
    self.descriptionLabel.frame=CGRectMake(textX,summaryY,textWidth,MIN(450,MAX(45,summarySize.height)));
    CGFloat buttonsY=MAX(summaryY+self.descriptionLabel.bounds.size.height+22,
        landscape?textY+260:posterHeight+260);
    CGFloat bW=landscape?MIN(190,(textWidth-12)/2):MIN(275,textWidth);
    self.playButton.frame=CGRectMake(textX,buttonsY,bW,49);
    self.likeButton.frame=CGRectMake(textX,buttonsY+61,bW,46);
    self.laterButton.frame=CGRectMake(textX+bW+12,buttonsY+61,MIN(235,MAX(110,textWidth-bW-12)),46);
    if(!landscape){
        self.laterButton.frame=CGRectMake(textX,buttonsY+119,bW,46);
    }
    CGFloat needed=MAX(self.poster.frame.origin.y+self.poster.frame.size.height,
        self.laterButton.frame.origin.y+self.laterButton.frame.size.height);
    self.scroll.contentSize=CGSizeMake(w,needed+45);
}
- (void)updateActions {
    [self.likeButton setTitle:self.liked?@"♥  Curtido":@"♡  Curtir" forState:UIControlStateNormal];
    [self.laterButton setTitle:self.later?@"✓  Na minha lista":@"＋  Assistir mais tarde"
        forState:UIControlStateNormal];
}
- (void)close{[self dismissViewControllerAnimated:YES completion:nil];}
- (void)play{if(self.onPlay)self.onPlay();}
- (void)like{self.liked=!self.liked;[self updateActions];if(self.onLike)self.onLike();}
- (void)toggleLater{self.later=!self.later;[self updateActions];if(self.onLater)self.onLater();}
@end
