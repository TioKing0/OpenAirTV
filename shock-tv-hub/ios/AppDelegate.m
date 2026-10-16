#import "AppDelegate.h"
#import "ShockViewController.h"
#import <AVFoundation/AVFoundation.h>
@implementation AppDelegate
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)options {
    NSError *error=nil;
    [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:&error];
    [[AVAudioSession sharedInstance] setActive:YES error:&error];
    self.window=[[UIWindow alloc] initWithFrame:[UIScreen mainScreen].bounds];
    ShockViewController *home=[[ShockViewController alloc] init];
    self.window.rootViewController=[[UINavigationController alloc] initWithRootViewController:home];
    [self.window makeKeyAndVisible];
    return YES;
}
@end
