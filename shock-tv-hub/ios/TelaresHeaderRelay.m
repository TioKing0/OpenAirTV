#import "TelaresHeaderRelay.h"
#import <sys/socket.h>
#import <netinet/in.h>
#import <arpa/inet.h>
#import <unistd.h>
#import <errno.h>

static BOOL TRValid(NSURL *url){return url.host.length&&!url.user.length&&!url.password.length&&
    ([url.scheme.lowercaseString isEqual:@"https"]||[url.scheme.lowercaseString isEqual:@"http"]);}
static BOOL TRWrite(int fd,NSData *data){
    const uint8_t *p=data.bytes;NSUInteger left=data.length;
    while(left){ssize_t n=send(fd,p,left,0);if(n<0&&errno==EINTR)continue;if(n<=0)return NO;p+=n;left-=n;}return YES;
}
static NSData *TRData(NSString *s){return [s dataUsingEncoding:NSUTF8StringEncoding];}
@interface TelaresHeaderRelay ()
@property(nonatomic) int listener;
@property(nonatomic) unsigned short port;
@property(nonatomic,copy) NSString *token;
@property(nonatomic,copy) NSDictionary *headers;
@property(nonatomic) BOOL closed;
@property(nonatomic,strong) NSMutableSet *clients;
@property(nonatomic,strong) NSLock *lock;
@property(nonatomic,strong) dispatch_semaphore_t slots;
- (NSString *)rewrite:(NSString *)text base:(NSURL *)base;
- (void)serve:(int)client;
@end

// Each request streams segment bytes directly to its socket; only HLS text is buffered.
@interface TRTransfer : NSObject <NSURLSessionDataDelegate,NSURLSessionTaskDelegate>
@property(nonatomic) int fd;
@property(nonatomic) BOOL head,playlist,sent;
@property(nonatomic,strong) NSMutableData *body;
@property(nonatomic,strong) NSHTTPURLResponse *response;
@property(nonatomic,strong) TelaresHeaderRelay *relay;
@property(nonatomic,strong) NSURLSession *session;
@property(nonatomic,strong) dispatch_semaphore_t done;
- (void)start:(NSURLRequest *)request;
@end
@implementation TRTransfer
- (void)start:(NSURLRequest *)request {
    self.done=dispatch_semaphore_create(0);
    NSURLSessionConfiguration *config=[NSURLSessionConfiguration ephemeralSessionConfiguration];
    config.HTTPCookieStorage=nil;config.URLCache=nil;config.HTTPShouldSetCookies=NO;
    config.timeoutIntervalForRequest=12;config.timeoutIntervalForResource=35;
    NSOperationQueue *queue=[[NSOperationQueue alloc]init];queue.maxConcurrentOperationCount=1;
    self.session=[NSURLSession sessionWithConfiguration:config delegate:self delegateQueue:queue];
    [[self.session dataTaskWithRequest:request] resume];
    if(dispatch_semaphore_wait(self.done,dispatch_time(DISPATCH_TIME_NOW,38*NSEC_PER_SEC))!=0){
        [self.session invalidateAndCancel];
        dispatch_semaphore_wait(self.done,DISPATCH_TIME_FOREVER);
    }
    self.session=nil;
}
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task
 willPerformHTTPRedirection:(NSHTTPURLResponse *)response newRequest:(NSURLRequest *)request
 completionHandler:(void (^)(NSURLRequest *))completionHandler {
    if(!TRValid(request.URL)||([response.URL.scheme isEqual:@"https"]&&![request.URL.scheme isEqual:@"https"])){completionHandler(nil);return;}
    NSMutableURLRequest *next=[request mutableCopy];
    for(NSString *key in self.relay.headers)[next setValue:self.relay.headers[key] forHTTPHeaderField:key];
    completionHandler(next);
}
- (void)sendHeaders:(NSUInteger)length known:(BOOL)known {
    if(self.sent)return;self.sent=YES;
    NSInteger status=self.response.statusCode;
    NSString *mime=self.playlist?@"application/vnd.apple.mpegurl":self.response.MIMEType?:@"application/octet-stream";
    NSMutableString *h=[NSMutableString stringWithFormat:@"HTTP/1.1 %ld Response\r\nConnection: close\r\nContent-Type: %@\r\n",(long)status,mime];
    if(known)[h appendFormat:@"Content-Length: %lu\r\n",(unsigned long)length];
    for(NSString *key in @[@"Content-Range",@"Accept-Ranges"]){
        NSString *value=self.response.allHeaderFields[key];
        if(value.length&&[value rangeOfCharacterFromSet:[NSCharacterSet newlineCharacterSet]].location==NSNotFound)[h appendFormat:@"%@: %@\r\n",key,value];
    }
    [h appendString:@"\r\n"];TRWrite(self.fd,TRData(h));
}
- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)task
 didReceiveResponse:(NSURLResponse *)response completionHandler:(void (^)(NSURLSessionResponseDisposition))completionHandler {
    if(![response isKindOfClass:[NSHTTPURLResponse class]]){completionHandler(NSURLSessionResponseCancel);return;}
    self.response=(id)response;
    NSString *mime=response.MIMEType.lowercaseString;
    self.playlist=self.response.statusCode==200&&([mime containsString:@"mpegurl"]||[response.URL.path.lowercaseString hasSuffix:@".m3u8"]);
    if(self.playlist&&!self.head)self.body=[NSMutableData data];
    else [self sendHeaders:MAX(0,response.expectedContentLength) known:response.expectedContentLength>=0];
    completionHandler(NSURLSessionResponseAllow);
}
- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)task didReceiveData:(NSData *)data {
    if(self.head)return;
    if(self.playlist){
        if(self.body.length+data.length>2000000){[task cancel];return;}[self.body appendData:data];
    }else if(!TRWrite(self.fd,data))[task cancel];
}
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error {
    if(!error&&self.playlist&&!self.head){
        NSString *text=[[NSString alloc]initWithData:self.body encoding:NSUTF8StringEncoding];
        if([text hasPrefix:@"#EXTM3U"]){
            NSData *data=TRData([self.relay rewrite:text base:self.response.URL]);
            [self sendHeaders:data.length known:YES];TRWrite(self.fd,data);
        }
    }
    if(!self.sent)TRWrite(self.fd,TRData(@"HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"));
    [session finishTasksAndInvalidate];dispatch_semaphore_signal(self.done);
}
@end

@implementation TelaresHeaderRelay
+ (NSDictionary *)publicHeaders:(NSDictionary *)headers {
    if(![headers isKindOfClass:[NSDictionary class]])return @{};
    NSDictionary *allowed=@{@"user-agent":@"User-Agent",@"referer":@"Referer",@"origin":@"Origin",@"accept":@"Accept"};
    NSMutableDictionary *safe=[NSMutableDictionary dictionary];
    for(id key in headers){
        if(![key isKindOfClass:[NSString class]])continue;
        NSString *name=allowed[[key lowercaseString]];id value=headers[key];
        if(name&&[value isKindOfClass:[NSString class]]&&[value length]>0&&[value length]<=2048&&
            [value rangeOfCharacterFromSet:[NSCharacterSet newlineCharacterSet]].location==NSNotFound)safe[name]=value;
    }
    return safe;
}
- (instancetype)initWithHeaders:(NSDictionary *)headers error:(NSError **)error {
    if(!(self=[super init]))return nil;_listener=-1;
    _headers=[[self.class publicHeaders:headers] copy];_token=[[NSUUID UUID] UUIDString];
    _clients=[NSMutableSet set];_lock=[[NSLock alloc]init];_slots=dispatch_semaphore_create(4);
    int fd=socket(AF_INET,SOCK_STREAM,0);struct sockaddr_in addr={0};addr.sin_family=AF_INET;addr.sin_addr.s_addr=htonl(INADDR_LOOPBACK);addr.sin_len=sizeof(addr);
    if(fd<0||bind(fd,(struct sockaddr *)&addr,sizeof(addr))<0||listen(fd,4)<0){
        if(error)*error=[NSError errorWithDomain:NSPOSIXErrorDomain code:errno userInfo:nil];if(fd>=0)close(fd);return nil;
    }
    socklen_t length=sizeof(addr);getsockname(fd,(struct sockaddr *)&addr,&length);_port=ntohs(addr.sin_port);_listener=fd;
    __weak TelaresHeaderRelay *weakSelf=self;
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT,0),^{
        while(YES){
            int client=accept(fd,NULL,NULL);if(client<0)break;
            TelaresHeaderRelay *relay=weakSelf;
            if(!relay||relay.closed){close(client);break;}
            if(dispatch_semaphore_wait(relay.slots,DISPATCH_TIME_NOW)!=0){close(client);continue;}
            int one=1;setsockopt(client,SOL_SOCKET,SO_NOSIGPIPE,&one,sizeof(one));
            struct timeval timeout={12,0};setsockopt(client,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));setsockopt(client,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout));
            [relay.lock lock];[relay.clients addObject:@(client)];[relay.lock unlock];
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT,0),^{
                @autoreleasepool{[relay serve:client];}
                [relay.lock lock];[relay.clients removeObject:@(client)];close(client);[relay.lock unlock];
                dispatch_semaphore_signal(relay.slots);
            });
        }
    });return self;
}
- (NSURL *)localURL:(NSURL *)url {
    if(!TRValid(url))return nil;
    NSString *encoded=[TRData(url.absoluteString) base64EncodedStringWithOptions:0];
    encoded=[[encoded stringByReplacingOccurrencesOfString:@"+" withString:@"-"] stringByReplacingOccurrencesOfString:@"/" withString:@"_"];
    NSString *suffix=[url.path.lowercaseString hasSuffix:@".m3u8"]?@"index.m3u8":@"media";
    return [NSURL URLWithString:[NSString stringWithFormat:@"http://127.0.0.1:%u/%@/%@/%@",self.port,self.token,encoded,suffix]];
}
- (NSString *)rewrite:(NSString *)text base:(NSURL *)base {
    NSMutableArray *lines=[NSMutableArray array];
    NSRegularExpression *rx=[NSRegularExpression regularExpressionWithPattern:@"URI=\"([^\"\\r\\n]+)\"" options:0 error:nil];
    for(NSString *raw in [text componentsSeparatedByCharactersInSet:[NSCharacterSet newlineCharacterSet]]){
        NSMutableString *line=[raw mutableCopy];
        if(line.length&&![line hasPrefix:@"#"]){
            NSURL *remote=[[NSURL URLWithString:[line stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]] relativeToURL:base] absoluteURL];
            if(TRValid(remote))line=[[self localURL:remote].absoluteString mutableCopy];
        }else{
            NSArray *matches=[rx matchesInString:line options:0 range:NSMakeRange(0,line.length)];
            for(NSTextCheckingResult *match in [matches reverseObjectEnumerator]){
                NSURL *remote=[[NSURL URLWithString:[line substringWithRange:[match rangeAtIndex:1]] relativeToURL:base] absoluteURL];
                if(TRValid(remote))[line replaceCharactersInRange:[match rangeAtIndex:1] withString:[self localURL:remote].absoluteString];
            }
        }[lines addObject:line];
    }return [lines componentsJoinedByString:@"\n"];
}
- (void)serve:(int)client {
    NSMutableData *buffer=[NSMutableData data];char byte;BOOL complete=NO;
    while(buffer.length<16000&&recv(client,&byte,1,0)==1){[buffer appendBytes:&byte length:1];
        if(buffer.length>=4&&!memcmp((const char *)buffer.bytes+buffer.length-4,"\r\n\r\n",4)){complete=YES;break;}}
    if(!complete)return;
    NSString *request=[[NSString alloc]initWithData:buffer encoding:NSUTF8StringEncoding];
    NSArray *lines=[request componentsSeparatedByString:@"\r\n"],*first=[lines[0] componentsSeparatedByString:@" "];
    if(first.count!=3||!([first[0] isEqual:@"GET"]||[first[0] isEqual:@"HEAD"]))return;
    NSString *host=nil,*range=nil;
    for(NSString *line in lines){NSRange colon=[line rangeOfString:@":"];if(colon.location==NSNotFound)continue;
        NSString *key=[[line substringToIndex:colon.location] lowercaseString],*value=[[line substringFromIndex:colon.location+1] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]];
        if([key isEqual:@"host"])host=value;if([key isEqual:@"range"])range=value;}
    NSArray *path=[first[1] componentsSeparatedByString:@"/"];
    if(![host isEqual:[NSString stringWithFormat:@"127.0.0.1:%u",self.port]]||path.count!=4||![path[1] isEqual:self.token])return;
    NSString *encoded=[[path[2] stringByReplacingOccurrencesOfString:@"-" withString:@"+"] stringByReplacingOccurrencesOfString:@"_" withString:@"/"];
    NSData *decoded=[[NSData alloc]initWithBase64EncodedString:encoded options:0];if(!decoded.length)return;
    NSURL *url=[NSURL URLWithString:[[NSString alloc]initWithData:decoded encoding:NSUTF8StringEncoding]];if(!TRValid(url)||self.closed)return;
    NSMutableURLRequest *up=[NSMutableURLRequest requestWithURL:url cachePolicy:NSURLRequestReloadIgnoringLocalCacheData timeoutInterval:12];
    up.HTTPMethod=first[0];for(NSString *key in self.headers)[up setValue:self.headers[key] forHTTPHeaderField:key];
    [up setValue:@"identity" forHTTPHeaderField:@"Accept-Encoding"];
    if(range&&![url.path.lowercaseString hasSuffix:@".m3u8"]&&[range rangeOfString:@"^bytes=[0-9]*-[0-9]*$" options:NSRegularExpressionSearch].location!=NSNotFound)[up setValue:range forHTTPHeaderField:@"Range"];
    TRTransfer *transfer=[[TRTransfer alloc]init];transfer.fd=client;transfer.head=[first[0] isEqual:@"HEAD"];transfer.relay=self;[transfer start:up];
}
- (void)close {
    [self.lock lock];if(self.closed){[self.lock unlock];return;}self.closed=YES;
    if(self.listener>=0){shutdown(self.listener,SHUT_RDWR);close(self.listener);self.listener=-1;}
    for(NSNumber *fd in self.clients)shutdown(fd.intValue,SHUT_RDWR);
    [self.lock unlock];
}
- (void)dealloc {[self close];}
@end
