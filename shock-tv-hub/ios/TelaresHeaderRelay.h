#import <Foundation/Foundation.h>
@interface TelaresHeaderRelay : NSObject
+ (NSDictionary *)publicHeaders:(NSDictionary *)headers;
- (instancetype)initWithHeaders:(NSDictionary *)headers error:(NSError **)error;
- (NSURL *)localURL:(NSURL *)url;
- (void)close;
@end
