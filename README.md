# CanvasCamera Plugin


## Plugin's Purpose
The purpose of the plugin is to capture video to preview camera in a web page's canvas element.<br>
Allows to select front or back camera and to control the flash.

## Supported Platforms
- iOS
- Android

## Dependencies
[Cordova][cordova] will check all dependencies and install them if they are missing.

## Installation
The plugin can either be installed into the local development environment or cloud based through [PhoneGap Build][PGB].

### Adding the Plugin to your project
Through the [Command-line Interface][CLI]:

```bash
cordova plugin add https://github.com/VirtuoWorks/CanvasCameraPlugin.git && cordova prepare
```

### Removing the Plugin from your project
Through the [Command-line Interface][CLI]:

```bash
cordova plugin remove com.virtuoworks.cordova-plugin-canvas-camera
```

## Using the plugin
The plugin creates the object ```window.plugin.CanvasCamera``` with the following methods:

### Plugin initialization
The plugin and its methods are not available before the *deviceready* event has been fired.
Call `initialize` with a reference to the canvas object used to preview the video and a second, optional, reference to a thumbnail canvas.

```javascript
document.addEventListener('deviceready', function () {

    // Call the initialize() function with canvas element reference
    var objCanvas = document.getElementById('canvas');
    window.plugin.CanvasCamera.initialize(objCanvas);
    // window.plugin.CanvasCamera is now available

}, false);
```

### `start`
Start capturing video as images from camera to preview camera on web page.<br>
`capture` callback function will be called with image data (image file url) each time the plugin takes an image for a frame.<br>

```javascript
window.plugin.CanvasCamera.start(options);
```

This function starts a video capturing session, then the plugin takes each frame as a JPEG image and gives its url to web page calling the `capture` callback function with the image url(s).<br>
The `capture` callback function will draw the image inside a canvas element to display the video.


#### Example
```javascript
var options = {
    cameraPosition: 'front',
};
window.plugin.CanvasCamera.start(options);
```
### `setFlashMode`
Set flash mode for camera.<br>

```javascript
window.plugin.CanvasCamera.setFlashMode(true);
```

### `setCameraPosition`
Change input camera to 'front' or 'back' camera.

```javascript
window.plugin.CanvasCamera.setCameraPosition('front');
```

### Options
Optional parameters to customize the settings.

```javascript
{
  cameraPosition: 'front',
  fps: 30,
  width: 640,
  height: 480,
  thumbnailRatio: 1/6
}
```

- `cameraPosition` **String**, 'front' or 'back'.
- `fps` **Number**, desired number of frames per second.
- `width` **Number**, width in pixels of the video to capture.
- `height` **Number**, height in pixels of the video to capture.
- `thumbnailRatio` **Number**, a ratio used to scale down the thumbnail.


## Usage

### Full size video only
```javascript
let fullsizeCanvasElement = document.getElementById('fullsize-canvas');

CanvasCamera.initialize(fullsizeCanvasElement);

let options:CanvasCamera.CanvasCameraOptions = {
    cameraPosition: 'back',
};

CanvasCamera.start(options);

CanvasCamera.setOnDrawFullsize(function(fullsizeCtx) {
    // do something with fullsize video
}.bind(this));
```

### With thumbnail video
```javascript
let fullsizeCanvasElement = document.getElementById('fullsize-canvas');
let thumbnailCanvasElement = document.getElementById('thumbnail-canvas');

CanvasCamera.initialize(fullsizeCanvasElement,thumbnailCanvasElement);

let options:CanvasCamera.CanvasCameraOptions = {
    cameraPosition: 'front',
    fps: 15,
    thumbnailRatio: 1/6
};

CanvasCamera.start(options);

CanvasCamera.setOnDrawFullsize(function(fullsizeCtx) {
    // do something with fullsize video
}.bind(this));

CanvasCamera.setOnDrawThumbnail(function(thumbnailCtx) {
    // do something with thumbnail video
}.bind(this));
```


## Contributing

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Added some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create new Pull Request


## License

This software is released under the [MIT License][mit_license].

[cordova]: https://cordova.apache.org
[PGB_plugin]: https://build.phonegap.com/
[CLI]: http://cordova.apache.org/docs/en/latest/guide/cli/index.html
[PGB]: http://docs.phonegap.com/phonegap-build/
[mit_license]: https://opensource.org/licenses/MIT
