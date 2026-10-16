#import "ShockStreamChecker.h"

@interface ShockStreamChecker ()
@property(nonatomic,strong) NSURLSession *session;
@property(nonatomic,strong) NSArray *items;
@property(nonatomic,strong) NSMutableArray *validItems;
@property(nonatomic,strong) NSMutableDictionary *samples;
@property(nonatomic,strong) NSMutableDictionary *taskItems;
@property(nonatomic,strong) NSMutableSet *finishedTasks;
@property(nonatomic,copy) void (^completion)(NSArray *,NSUInteger);
@property(nonatomic,assign) NSUInteger nextIndex;
@property(nonatomic,assign) NSUInteger inFlight;
@property(nonatomic,assign) NSUInteger checked;
@property(nonatomic,assign) BOOL cancelled;
@end

@implementation ShockStreamChecker
- (void)verify:(NSArray *)sources completion:(void (^)(NSArray *,NSUInteger))callback {
    [self cancel];
    self.cancelled=NO;
    self.items=[sources subarrayWithRange:NSMakeRange(0,MIN(sources.count,16))];
    self.validItems=[NSMutableArray array];
    self.samples=[NSMutableDictionary dictionary];
    self.taskItems=[NSMutableDictionary dictionary];
    self.finishedTasks=[NSMutableSet set];
    self.completion=callback;
    self.nextIndex=0;self.inFlight=0;self.checked=0;
    NSURLSessionConfiguration *settings=[NSURLSessionConfiguration ephemeralSessionConfiguration];
    settings.timeoutIntervalForRequest=6;
    settings.timeoutIntervalForResource=9;
    settings.HTTPMaximumConnectionsPerHost=3;
    settings.URLCache=nil;
    self.session=[NSURLSession sessionWithConfiguration:settings delegate:self
        delegateQueue:[NSOperationQueue mainQueue]];
    [self schedule];
}
- (void)schedule {
    if(self.cancelled)return;
    while(self.inFlight<3&&self.nextIndex<self.items.count){
        NSDictionary *item=self.items[self.nextIndex++];
        NSString *link=item[@"_url"];
        NSURL *url=[NSURL URLWithString:link];
        if(!url.host||!([url.scheme.lowercaseString isEqualToString:@"https"]||
                        [url.scheme.lowercaseString isEqualToString:@"http"])) {
            self.checked++;continue;
        }
        // An HTTP range request avoids downloading the entire video during verification.
        NSMutableURLRequest *request=[NSMutableURLRequest requestWithURL:url
            cachePolicy:NSURLRequestReloadIgnoringLocalCacheData timeoutInterval:6.0];
        [request setValue:@"bytes=0-2047" forHTTPHeaderField:@"Range"];
        [request setValue:@"identity" forHTTPHeaderField:@"Accept-Encoding"];
        NSURLSessionDataTask *task=[self.session dataTaskWithRequest:request];
        NSNumber *key=@(task.taskIdentifier);
        self.taskItems[key]=item;
        self.samples[key]=[NSMutableData data];
        self.inFlight++;
        [task resume];
    }
    if(!self.inFlight&&self.nextIndex>=self.items.count)[self complete];
}
- (void)finishTask:(NSURLSessionTask *)task success:(BOOL)success {
    NSNumber *key=@(task.taskIdentifier);
    if([self.finishedTasks containsObject:key])return;
    [self.finishedTasks addObject:key];
    NSDictionary *item=self.taskItems[key];
    self.checked++;
    if(self.inFlight>0)self.inFlight--;
    if(success&&item){
        NSMutableDictionary *good=[item mutableCopy];
        NSURL *finalURL=task.response.URL;
        if(finalURL.host){
            // Avoid accepting silent HTTPS to HTTP downgrades.
            NSURL *original=[NSURL URLWithString:item[@"_url"]];
            if(!([original.scheme.lowercaseString isEqualToString:@"https"]&&
                 [finalURL.scheme.lowercaseString isEqualToString:@"http"])){
                good[@"_url"]=finalURL.absoluteString;
                [self.validItems addObject:good];
            }
        }
    }
    [self.samples removeObjectForKey:key];
    [self.taskItems removeObjectForKey:key];
    [task cancel];
    if(!self.cancelled)[self schedule];
}
- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)task
    didReceiveResponse:(NSURLResponse *)response
    completionHandler:(void (^)(NSURLSessionResponseDisposition disposition))completionHandler {
    NSHTTPURLResponse *http=[response isKindOfClass:[NSHTTPURLResponse class]]?
         (NSHTTPURLResponse *)response:nil;
    NSInteger status=http.statusCode;
    NSString *mime=response.MIMEType.lowercaseString?:@"";
    if((status!=200&&status!=206)||[mime containsString:@"text/html"]||
       [mime containsString:@"application/json"]){
        completionHandler(NSURLSessionResponseCancel);
        [self finishTask:task success:NO];
        return;
    }
    completionHandler(NSURLSessionResponseAllow);
}
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task
    willPerformHTTPRedirection:(NSHTTPURLResponse *)response
    newRequest:(NSURLRequest *)request
    completionHandler:(void (^)(NSURLRequest *))completionHandler {
    NSURL *previous=response.URL,*next=request.URL;
    BOOL safe=next.host!=nil&&([next.scheme.lowercaseString isEqualToString:@"https"]||
        [next.scheme.lowercaseString isEqualToString:@"http"]);
    if([previous.scheme.lowercaseString isEqualToString:@"https"]&&
       [next.scheme.lowercaseString isEqualToString:@"http"])safe=NO;
    completionHandler(safe?request:nil);
}
- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)task
    didReceiveData:(NSData *)data {
    NSNumber *key=@(task.taskIdentifier);
    if(self.cancelled||[self.finishedTasks containsObject:key])return;
    NSMutableData *sample=self.samples[key];
    if(!sample)return;
    NSUInteger room=2048-sample.length;
    if(room>0)[sample appendData:[data subdataWithRange:NSMakeRange(0,MIN(room,data.length))]];
    BOOL good=[self isLikelyMedia:task.response bytes:sample];
    if(good||sample.length>=512)[self finishTask:task success:good];
}
- (BOOL)isLikelyMedia:(NSURLResponse *)response bytes:(NSData *)data {
    if(data.length<8)return NO;
    NSString *mime=response.MIMEType.lowercaseString?:@"";
    const unsigned char *p=data.bytes;
    NSString *url=response.URL.path.lowercaseString?:@"";
    BOOL playlist=[mime containsString:@"mpegurl"]||[url hasSuffix:@".m3u8"];
    BOOL mp4=[mime hasPrefix:@"video/mp4"]||[url hasSuffix:@".mp4"]||
              [url hasSuffix:@".m4v"]||[url hasSuffix:@".mov"];
    BOOL rawAudio=[mime hasPrefix:@"audio/mp4"]||[url hasSuffix:@".m4a"];
    // MPEG-DASH and Matroska are not supported by the stock iOS 9 AVPlayer.
    if([url hasSuffix:@".mpd"]||[url hasSuffix:@".mkv"]||[url hasSuffix:@".webm"])return NO;
    if(!memcmp(p,"#EXTM3U",7))return YES;
    if(data.length>=12&&!memcmp(p+4,"ftyp",4))return YES;
    if(data.length>=8&&p[0]==0x1a&&p[1]==0x45&&p[2]==0xdf&&p[3]==0xa3)return NO;
    NSString *head=[[NSString alloc]initWithData:[data subdataWithRange:NSMakeRange(0,MIN(96,data.length))]
         encoding:NSUTF8StringEncoding];
    NSString *lower=head.lowercaseString?:@"";
    if([lower containsString:@"<html"]||[lower containsString:@"<!doctype"]||
       [lower hasPrefix:@"{\"error\""]||[lower hasPrefix:@"<?xml"])return NO;
    return playlist||mp4||rawAudio;
}
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task
    didCompleteWithError:(NSError *)error {
    if(self.cancelled)return;
    NSNumber *key=@(task.taskIdentifier);
    if([self.finishedTasks containsObject:key])return;
    NSData *sample=self.samples[key];
    BOOL ok=error==nil&&[self isLikelyMedia:task.response bytes:sample];
    [self finishTask:task success:ok];
}
- (void)complete {
    if(self.cancelled)return;
    NSArray *sorted=[self.validItems sortedArrayUsingComparator:^NSComparisonResult(NSDictionary *a,NSDictionary *b){
        NSInteger left=[self score:a],right=[self score:b];
        return left>right?NSOrderedAscending:left<right?NSOrderedDescending:NSOrderedSame;
    }];
    void (^cb)(NSArray *,NSUInteger)=self.completion;
    NSUInteger count=self.checked;
    self.completion=nil;
    [self.session finishTasksAndInvalidate];
    self.session=nil;
    if(cb)cb(sorted,count);
}
- (NSInteger)score:(NSDictionary *)item {
    NSString *value=[item[@"_label"] isKindOfClass:[NSString class]]?
        [item[@"_label"] lowercaseString]:@"";
    NSInteger points=0;
    if([value containsString:@"1080"]||[value containsString:@"full hd"])points+=40;
    else if([value containsString:@"720"])points+=30;
    else if([value containsString:@"4k"]||[value containsString:@"2160"])points+=24;
    else if([value containsString:@"480"])points+=15;
    if([value containsString:@"dublado"]||[value containsString:@"pt-br"]||
       [value containsString:@"portugu"])points+=30;
    return points;
}
- (void)cancel {
    self.cancelled=YES;
    self.completion=nil;
    [self.session invalidateAndCancel];
    self.session=nil;
    [self.samples removeAllObjects];
    [self.taskItems removeAllObjects];
}
@end
