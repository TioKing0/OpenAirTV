#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>
@interface TelaresSubtitles : NSObject
- (instancetype)initWithPlayer:(AVPlayer *)player controller:(UIViewController *)controller
    sources:(NSArray *)sources type:(NSString *)type identifier:(NSString *)identifier;
- (void)close;
@end
