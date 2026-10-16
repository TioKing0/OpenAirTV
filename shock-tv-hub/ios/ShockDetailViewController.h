#import <UIKit/UIKit.h>

@interface ShockDetailViewController : UIViewController
@property(nonatomic,copy) void (^onPlay)(void);
@property(nonatomic,copy) void (^onLike)(void);
@property(nonatomic,copy) void (^onLater)(void);
- (instancetype)initWithMedia:(NSDictionary *)media liked:(BOOL)liked later:(BOOL)later;
@end
