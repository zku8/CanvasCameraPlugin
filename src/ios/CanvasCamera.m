//
//  CanvasCamera.js
//  PhoneGap iOS Cordova Plugin to capture Camera streaming into a HTML5 Canvas or an IMG tag.
//
//  Created by Diego Araos <d@wehack.it> on 12/29/12.
//
//  Updated by VirtuoWorks.
//
//  MIT License

#import "CanvasCamera.h"
#import <MobileCoreServices/MobileCoreServices.h>

// parameter
#define kQualityKey         @"quality"
#define kWidthKey           @"width"
#define kHeightKey          @"height"
#define kFpsKey             @"fps"
#define kDevicePositionKey  @"cameraPosition"
#define kHasThumbnailKey    @"hasThumbnail"
#define kThumbnailRatioKey  @"thumbnailRatio"

@interface CanvasCamera () {
    dispatch_queue_t queue;
    BOOL bIsStarted;

    // parameters
    AVCaptureFlashMode          _flashMode;
    AVCaptureDevicePosition     _devicePosition;

    // options
    int _quality;
    int _width;
    int _height;
    int _fps;
    bool _hasThumbnail;
    double _thumbnailRatio;
}

@end

@implementation CanvasCamera

#pragma mark - Interfaces

- (void)startCapture:(CDVInvokedUrlCommand *)command
{
    // check already started
    if (self.session && bIsStarted)
    {
        // failure callback
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Already started"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

        return;
    }

    // init parameters - default values
    _quality =85;
    _width = 352;
    _height = 288;
    _fps = 30;
    _devicePosition = AVCaptureDevicePositionBack;
    _hasThumbnail = false;
    _thumbnailRatio = 1 / 6;

    // parse options
    if ([command.arguments count] > 0)
    {
        NSDictionary *jsonData = [command.arguments objectAtIndex:0];
        [self getOptions:jsonData];
    }


    [self.commandDelegate runInBackground:^{
        // add support for options (fps, capture quality, capture format, etc.)
        self.session = [[AVCaptureSession alloc] init];
        self.session.sessionPreset = AVCaptureSessionPreset352x288; // AVCaptureSessionPresetPhoto | AVCaptureSessionPreset352x288 | AVCaptureSessionPreset640x480
        
        self.device = [self cameraWithPosition: _devicePosition];

        self.input = [AVCaptureDeviceInput deviceInputWithDevice:self.device error:nil];

        self.output = [[AVCaptureVideoDataOutput alloc] init];
        self.output.videoSettings = [NSDictionary dictionaryWithObject:[NSNumber numberWithInt:kCVPixelFormatType_32BGRA] forKey:(id)kCVPixelBufferPixelFormatTypeKey];

        queue = dispatch_queue_create("canvas_camera_queue", NULL);
        [self.output setSampleBufferDelegate:(id)self queue:queue];

        [self.session addInput:self.input];
        [self.session addOutput:self.output];

        NSError *error;
        CMTime frameDuration = CMTimeMake(1, _fps);
        NSArray *supportedFrameRateRanges = [self.device.activeFormat videoSupportedFrameRateRanges];
        BOOL frameRateSupported = NO;
        for (AVFrameRateRange *range in supportedFrameRateRanges)
        {
            if (CMTIME_COMPARE_INLINE(frameDuration, >=, range.minFrameDuration) &&
                CMTIME_COMPARE_INLINE(frameDuration, <=, range.maxFrameDuration))
            {
                frameRateSupported = YES;
            }
        }

        if (frameRateSupported && [self.device lockForConfiguration:&error])
        {
            [self.device setActiveVideoMaxFrameDuration:frameDuration];
            [self.device setActiveVideoMinFrameDuration:frameDuration];
            [self.device unlockForConfiguration];
        }


        [self.session startRunning];

        bIsStarted = YES;
        
        
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@""];
        // The sendPluginResult method is thread-safe.
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];

}

- (void)stopCapture:(CDVInvokedUrlCommand *)command
{
    CDVPluginResult *pluginResult = nil;

    if (self.session)
    {
        [self.session stopRunning];
        self.session = nil;

        bIsStarted = NO;

        // success callback
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@""];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
    else
    {
        bIsStarted = NO;

        // failure callback
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Already stopped"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
}

- (void)setFlashMode:(CDVInvokedUrlCommand *)command
{
    CDVPluginResult *pluginResult = nil;

    NSString *errMsg = @"";
    BOOL bParsed = NO;
    if (command.arguments.count <= 0)
    {
        bParsed = NO;
        errMsg = @"Please specify a flash mode";
    }
    else
    {
        bParsed = YES;

        BOOL bFlashModeOn = [[command.arguments objectAtIndex:0] boolValue];
        if (bFlashModeOn)
        {
            _flashMode = AVCaptureFlashModeOn;
        }
        else
        {
            _flashMode = AVCaptureFlashModeOff;
        }
    }


    if (bParsed)
    {
        BOOL bSuccess = NO;
        // check session is started
        if (bIsStarted && self.session)
        {
            if ([self.device hasTorch] && [self.device hasFlash])
            {
                [self.device lockForConfiguration:nil];
                if (_flashMode == AVCaptureFlashModeOn)
                {
                    [self.device setTorchMode:AVCaptureTorchModeOn];
                    [self.device setFlashMode:AVCaptureFlashModeOn];
                }
                else if (_flashMode == AVCaptureFlashModeOff)
                {
                    [self.device setTorchMode:AVCaptureTorchModeOff];
                    [self.device setFlashMode:AVCaptureFlashModeOff];
                }
                [self.device unlockForConfiguration];

                bSuccess = YES;
            }
            else
            {
                bSuccess = NO;
                errMsg = @"This device has no flash or torch";
            }
        }
        else
        {
            bSuccess = NO;
            errMsg = @"Session is not started";
        }

        if (bSuccess)
        {
            // success callback
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@""];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
        else
        {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errMsg];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    }
    else
    {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errMsg];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
}

- (void)setCameraPosition:(CDVInvokedUrlCommand *)command
{
    CDVPluginResult *pluginResult = nil;

    NSString *errMsg = @"";
    BOOL bParsed = NO;
    if (command.arguments.count <= 0)
    {
        bParsed = NO;
        errMsg = @"Please specify a device position";
    }
    else
    {
        NSString *devicePosition = [command.arguments objectAtIndex:0];
        if ([devicePosition isEqualToString:@"front"]) {
            _devicePosition = AVCaptureDevicePositionFront;
            bParsed = YES;
        }
        else if ([devicePosition isEqualToString:@"back"]) {
            _devicePosition = AVCaptureDevicePositionBack;
            bParsed = YES;
        }
    }

    if (bParsed)
    {
        //Change camera source
        if(self.session)
        {
            //Remove existing input
            AVCaptureInput* currentCameraInput = [self.session.inputs objectAtIndex:0];
            if(((AVCaptureDeviceInput*)currentCameraInput).device.position != _devicePosition)
            {
                //Indicate that some changes will be made to the session
                [self.session beginConfiguration];

                //Remove existing input
                AVCaptureInput* currentCameraInput = [self.session.inputs objectAtIndex:0];
                [self.session removeInput:currentCameraInput];

                //Get new input
                AVCaptureDevice *newCamera = nil;

                newCamera = [self cameraWithPosition:_devicePosition];

                //Add input to session
                AVCaptureDeviceInput *newVideoInput = [[AVCaptureDeviceInput alloc] initWithDevice:newCamera error:nil];
                [self.session addInput:newVideoInput];

                //Commit all the configuration changes at once
                [self.session commitConfiguration];

                // success callback
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@""];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
            else
            {
                // success callback
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@""];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
        }
        else
        {
            errMsg = @"Capture stopped";
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errMsg];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    }
    else
    {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errMsg];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
}

#pragma mark - capture delegate


- (void)captureOutput:(AVCaptureOutput *)captureOutput didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer fromConnection:(AVCaptureConnection *)connection
{
    Boolean landscape = [CanvasCamera setVideoOrientation:connection];

    @autoreleasepool {

        UIImage *image = [CanvasCamera getUIImageFromSampleBuffer:sampleBuffer];

        image = [CanvasCamera getResizedUIImage:image toSize:CGSizeMake(_width, _height) landscape:landscape];
        NSData *fullImageData = UIImageJPEGRepresentation(image, 1.0);

        NSData *thumbImageData = nil;
        if (_hasThumbnail) {
            UIImage *thumbnail = [CanvasCamera getResizedUIImage:image ratio:_thumbnailRatio];
            thumbImageData = UIImageJPEGRepresentation(thumbnail, 1.0);
        }
        
        // resize image
        // image = [CanvasCamera resizeImage:image toSize:CGSizeMake(352.0, 288.0)];


        dispatch_async(dispatch_get_main_queue(), ^{
            @autoreleasepool {

                NSMutableDictionary *dictionary =  [[NSMutableDictionary alloc] init];

                
                // Get a file path to save the JPEG
                static int i = 0;
                i++;

                NSString *fullImagePath = [CanvasCamera getFilePath:@"f" idx:i ext:@"jpg"];

                // Write the data to the file
                [fullImageData writeToFile:fullImagePath atomically:YES];
                fullImagePath = [NSString stringWithFormat:@"file://%@", fullImagePath];
                dictionary[@"fullsize"] = fullImagePath;

                if (thumbImageData) {
                    NSString *thumbImagePath = [CanvasCamera getFilePath:@"t" idx:i ext:@"jpg"];
                    [thumbImageData writeToFile:thumbImagePath atomically:YES];
                    thumbImagePath = [NSString stringWithFormat:@"file://%@", thumbImagePath];
                    dictionary[@"thumbnail"] = thumbImagePath;
                }

                NSError *error = nil;
                NSData *json;
                
                // Dictionary convertable to JSON ?
                if ([NSJSONSerialization isValidJSONObject:dictionary])
                {
                    // Serialize the dictionary
                    json = [NSJSONSerialization dataWithJSONObject:dictionary options:0 error:&error];
                    
                    // If no errors, let's view the JSON
                    if (json != nil && error == nil)
                    {
                        NSString *jsonString = [[NSString alloc] initWithData:json encoding:NSUTF8StringEncoding];
                        
                        //NSLog(@"JSON: %@", jsonString);

                        NSString *javascript = [NSString stringWithFormat:@"%@%@%@", @"CanvasCamera.capture(", jsonString, @");"];
                        
                        if ([self.webView respondsToSelector:@selector(stringByEvaluatingJavaScriptFromString:)])
                        {
                            // Cordova-iOS pre-4
                            [self.webView performSelectorOnMainThread:@selector(stringByEvaluatingJavaScriptFromString:) withObject:javascript waitUntilDone:YES];
                        }
                        else
                        {
                            // Cordova-iOS 4+
                            [self.webView performSelectorOnMainThread:@selector(evaluateJavaScript:completionHandler:) withObject:javascript waitUntilDone:YES];
                        }
                    }
                }
                
            }
        });

    }
}

#pragma mark - Utilities

+ (UIImage *)getUIImageFromSampleBuffer:(CMSampleBufferRef)sampleBuffer
{
    CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
    CVPixelBufferLockBaseAddress(imageBuffer,0);
    uint8_t *baseAddress = (uint8_t *)CVPixelBufferGetBaseAddress(imageBuffer);
    size_t bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer);
    size_t width = CVPixelBufferGetWidth(imageBuffer);
    size_t height = CVPixelBufferGetHeight(imageBuffer);
    
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    CGContextRef newContext = CGBitmapContextCreate(baseAddress, width, height, 8, bytesPerRow, colorSpace, kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst);
    
    CGImageRef newImage = CGBitmapContextCreateImage(newContext);
    
    CGContextRelease(newContext);
    CGColorSpaceRelease(colorSpace);
    
    UIImage *image = [UIImage imageWithCGImage:newImage];

    CGImageRelease(newImage);
    CVPixelBufferUnlockBaseAddress(imageBuffer,0);
    
    return image;
}

+ (Boolean)setVideoOrientation:(AVCaptureConnection *)connection
{
    UIDeviceOrientation deviceOrientation = [[UIDevice currentDevice] orientation];
    Boolean landscape = true;

    if ([connection isVideoOrientationSupported])
    {
        switch(deviceOrientation) {
            case UIInterfaceOrientationPortraitUpsideDown:
                [connection setVideoOrientation:AVCaptureVideoOrientationPortraitUpsideDown];
                landscape = false;
                break;
            case UIInterfaceOrientationPortrait:
                [connection setVideoOrientation:AVCaptureVideoOrientationPortrait];
                landscape = false;
                break;
            case UIInterfaceOrientationLandscapeLeft:
                [connection setVideoOrientation:AVCaptureVideoOrientationLandscapeLeft];
                break;
            case UIInterfaceOrientationLandscapeRight:
                [connection setVideoOrientation:AVCaptureVideoOrientationLandscapeRight];
                break;
            default:
                break;
        }
    }

    return landscape;
}


// Find a camera with the specified AVCaptureDevicePosition, returning nil if one is not found
- (AVCaptureDevice *)cameraWithPosition:(AVCaptureDevicePosition) position
{
    NSArray *devices = [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo];
    for (AVCaptureDevice *device in devices)
    {
        if ([device position] == position)
            return device;
    }
    return nil;
}

+ (NSString *)getFilePath:(NSString *)prefix idx:(int)idx ext:(NSString *)ext
{
    NSString *documentsDirectory = [CanvasCamera getAppPath];

    NSString* filename = [NSString stringWithFormat:@"%@%d.%@", prefix, idx, ext];
    NSString* imagePath = [documentsDirectory stringByAppendingPathComponent:filename];
    
    if (idx > 10)
    {
        NSString *prevPath = [NSString stringWithFormat:@"%@%d.%@", prefix, (idx - 10), ext];
        NSError *error = nil;
        [[NSFileManager defaultManager] removeItemAtPath:prevPath error:&error];
    }
    
    return imagePath;
}

+ (NSString *)getAppPath
{
    // Get a file path to save the JPEG
    NSArray* paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString* documentsDirectory = [paths objectAtIndex:0];
    NSString *dataPath = [documentsDirectory stringByAppendingPathComponent:@"/tmp"];

    if (![[NSFileManager defaultManager] fileExistsAtPath:dataPath])
    {
        NSError *error = nil;
        [[NSFileManager defaultManager] createDirectoryAtPath:dataPath withIntermediateDirectories:NO attributes:nil error:&error]; //Create folder
        if (error) {
            NSLog(@"error occurred in create tmp folder : %@", [error localizedDescription]);
        }
    }
    return dataPath;
}

/**
 * parse options parameter and set it to local variables
 *
 */

- (void)getOptions: (NSDictionary *)jsonData
{
    if (![jsonData isKindOfClass:[NSDictionary class]])
    {
        return;
    }

    // get parameters from argument.

    // device position
    NSString *obj = [jsonData objectForKey:kDevicePositionKey];
    if (obj != nil)
    {
        if ([obj isEqualToString:@"front"])
        {
            _devicePosition = AVCaptureDevicePositionFront;
        }
        else
        {
            _devicePosition = AVCaptureDevicePositionBack;
        }
    }

    // quaility
    obj = [jsonData objectForKey:kQualityKey];
    if (obj != nil)
    {
        _quality = [obj intValue];
    }

    // width
    obj = [jsonData objectForKey:kWidthKey];
    if (obj != nil)
    {
        _width = [obj intValue];
    }

    // height
    obj = [jsonData objectForKey:kHeightKey];
    if (obj != nil)
    {
        _height = [obj intValue];
    }

    // fps
    obj = [jsonData objectForKey:kFpsKey];
    if (obj != nil)
    {
        _fps = [obj intValue];
    }

    obj = [jsonData objectForKey:kHasThumbnailKey];
    if (obj != nil)
    {
        _hasThumbnail = [obj boolValue];
    }

    obj = [jsonData objectForKey:kThumbnailRatioKey];
    if (obj != nil)
    {
        _thumbnailRatio = [obj doubleValue];
    }
}


+ (UIImage *)getResizedUIImage:(UIImage *)image ratio:(double)ratio
{
    if (ratio == 0) {
        ratio = 1;
    }

    CGSize newSize = CGSizeMake((CGFloat)(image.size.width * ratio), (CGFloat)(image.size.height * ratio));
    
    return [CanvasCamera getResizedUIImage:image toSize:newSize];
}

+ (UIImage *)getResizedUIImage:(UIImage *)image toSize:(CGSize)newSize landscape:(Boolean)landscape
{
    if (!landscape) {
        newSize = CGSizeMake(newSize.height, newSize.width);
    }

    return [CanvasCamera getResizedUIImage:image toSize:newSize];
}

+ (UIImage *)getResizedUIImage:(UIImage *)image toSize:(CGSize)newSize
{
    UIGraphicsBeginImageContext(newSize);
    [image drawInRect:CGRectMake(0, 0, newSize.width, newSize.height)];
    UIImage *newImage = UIGraphicsGetImageFromCurrentImageContext();

    UIGraphicsEndImageContext();
    return newImage;
}
@end
