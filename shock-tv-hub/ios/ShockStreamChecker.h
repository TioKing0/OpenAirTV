#import <Foundation/Foundation.h>
@interface ShockStreamChecker : NSObject <NSURLSessionDataDelegate, NSURLSessionTaskDelegate>
// Performs short, bounded HTTP media probes. A successful probe does not guarantee full playback.
- (void)verify:(NSArray *)sources completion:(void (^)(NSArray *available, NSUInteger checked))callback;
- (void)cancel;
@end
