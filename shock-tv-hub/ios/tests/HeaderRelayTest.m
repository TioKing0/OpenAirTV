#import <Foundation/Foundation.h>
#import "TelaresHeaderRelay.h"
static NSData *Fetch(NSURL *url,NSString *range,NSInteger *status){
    NSMutableURLRequest *request=[NSMutableURLRequest requestWithURL:url];request.timeoutInterval=10;
    if(range)[request setValue:range forHTTPHeaderField:@"Range"];
    dispatch_semaphore_t done=dispatch_semaphore_create(0);__block NSData *result;__block NSError *failure;
    NSURLSessionConfiguration *config=[NSURLSessionConfiguration ephemeralSessionConfiguration];
    NSURLSession *session=[NSURLSession sessionWithConfiguration:config];
    [[session dataTaskWithRequest:request completionHandler:^(NSData *data,NSURLResponse *response,NSError *error){
        result=data;failure=error;*status=[(NSHTTPURLResponse *)response statusCode];dispatch_semaphore_signal(done);
    }] resume];
    NSCAssert(dispatch_semaphore_wait(done,dispatch_time(DISPATCH_TIME_NOW,15*NSEC_PER_SEC))==0,@"Relay request timed out");
    [session finishTasksAndInvalidate];NSCAssert(!failure,@"Relay request failed: %@",failure);return result;
}
int main(int argc,char **argv){@autoreleasepool{
    NSCAssert(argc==2,@"Expected fixture origin port");
    NSString *base=[NSString stringWithFormat:@"http://127.0.0.1:%s",argv[1]];
    NSDictionary *headers=@{@"User-Agent":@"Telares-Test",@"Referer":@"https://fixture.invalid/",@"Origin":@"https://fixture.invalid",@"Cookie":@"not-forwarded"};
    NSCAssert(![TelaresHeaderRelay publicHeaders:headers][@"Cookie"],@"Private header accepted");
    NSError *error;TelaresHeaderRelay *relay=[[TelaresHeaderRelay alloc]initWithHeaders:headers error:&error];
    NSCAssert(relay&&!error,@"Relay initialization failed: %@",error);
    NSInteger status=0;
    NSData *data=Fetch([relay localURL:[NSURL URLWithString:[base stringByAppendingString:@"/master.m3u8"]]],nil,&status);
    NSString *master=[[NSString alloc]initWithData:data encoding:NSUTF8StringEncoding];
    NSCAssert(status==200&&[master hasPrefix:@"#EXTM3U"],@"Master playlist failed");
    NSString *child=nil;for(NSString *line in [master componentsSeparatedByString:@"\n"])if([line hasPrefix:@"http://127.0.0.1:"])child=line;
    NSCAssert(child,@"Relative variant not rewritten");
    NSString *playlist=[[NSString alloc]initWithData:Fetch([NSURL URLWithString:child],nil,&status) encoding:NSUTF8StringEncoding];
    NSRegularExpression *rx=[NSRegularExpression regularExpressionWithPattern:@"URI=\"([^\"]+)\"" options:0 error:nil];
    NSArray *matches=[rx matchesInString:playlist options:0 range:NSMakeRange(0,playlist.length)];
    NSCAssert(matches.count==2,@"Key/map attributes not retained");
    for(NSTextCheckingResult *match in matches){
        NSURL *url=[NSURL URLWithString:[playlist substringWithRange:[match rangeAtIndex:1]]];
        NSCAssert([url.host isEqual:@"127.0.0.1"],@"Key/map left outside relay");
        NSCAssert(Fetch(url,nil,&status).length>0&&status==200,@"Key/map request failed");
    }
    NSString *segment=nil;for(NSString *line in [playlist componentsSeparatedByString:@"\n"])if([line hasPrefix:@"http://127.0.0.1:"])segment=line;
    NSCAssert(segment,@"Segment not rewritten");
    NSData *partial=Fetch([NSURL URLWithString:segment],@"bytes=2-5",&status);
    NSCAssert(status==206&&[partial isEqual:[@"2345" dataUsingEncoding:NSUTF8StringEncoding]],@"Byte range not preserved");
    [relay close];puts("Native Foundation HLS headers, variant, key/map, segment and Range passed");
}return 0;}
