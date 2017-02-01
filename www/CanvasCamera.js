//
//  CanvasCamera.js
//  PhoneGap iOS Cordova Plugin to capture Camera streaming into a HTML5 Canvas or an IMG tag.
//
//  Created by Diego Araos <d@wehack.it> on 12/29/12.
//
//  Updated by VirtuoWorks.
//
//  MIT License

var exec = require('cordova/exec');
var CanvasCamera = function(){
    var _obj = null;
    var _context = null;
    var _camImage = null;
    var _onDraw = null;
};

CanvasCamera.prototype.initialize = function(obj) {
    this._obj = obj;
    this._context = obj.getContext("2d");
    console.log('this._context from CanvasCamera.js in platform_www');
    console.log(this._context);
    this._camImage = new Image();
    this._camImage.onload = function() {
        this.drawImage();
        if(this._onDraw) {
          this._onDraw(this._context);
        }
    }.bind(this);
};

CanvasCamera.prototype.start = function(options) {
    this._obj.width = options.width;
    this._obj.height = options.height;
    cordova.exec(this.capture.bind(this), function(error) {
        console.log('start error', error);
    }, "CanvasCamera", "startCapture", [options]);
};

CanvasCamera.prototype.stop = function() {
    cordova.exec(false, function(error) {
        console.log('stop error', error);
    }, "CanvasCamera", "stopCapture", []);
};

CanvasCamera.prototype.capture = function(imgData) {
    // console.log('capture from native code', imgData);
    if (imgData) {
        // console.log(imgData);

        // var buffer = this.base64ToBuffer(imgData);
        // var imageData = new ImageData(buffer, width, height);

        this._camImage.src = imgData;
    } else {
        console.log('no imgData');
    }
};

CanvasCamera.prototype.setOnDraw = function(onDraw) {
  this._onDraw = onDraw || null;
};

CanvasCamera.prototype.setFlashMode = function(flashMode) {
    cordova.exec(function(){}, function(error) {
        console.log('setFlashMode error', error);
    }, "CanvasCamera", "setFlashMode", [flashMode]);
};

CanvasCamera.prototype.setCameraPosition = function(cameraPosition) {
    cordova.exec(function(){
        this._cameraPosition = cameraPosition;
    }.bind(this), function(error) {
        console.log('setCameraPosition error', error);
    }, "CanvasCamera", "setCameraPosition", [cameraPosition]);
};

CanvasCamera.prototype.drawImage = function() {
    // console.log('window.orientation: ' + window.orientation);
    var image = this._camImage;
    var context = this._context;
    var canvasWidth = this._obj.width = this._obj.clientWidth;
    var canvasHeight = this._obj.height = this._obj.clientHeight;

    var desiredWidth = canvasWidth;
    var desiredHeight = canvasHeight;
    if (window.orientation != 90 && window.orientation != -90) {
        console.log()
        desiredWidth = canvasHeight;
        desiredHeight = canvasWidth;
    }

    var imageWidth = image.width;
    var imageHeight = image.height;
    var ratio = Math.min(desiredWidth / imageWidth, desiredHeight / imageHeight);
    var newWidth = imageWidth * ratio;
    var newHeight = imageHeight * ratio;
    var cropX, cropY, cropWidth, cropHeight, aspectRatio = 1;

    //context.clearRect(0, 0, desiredWidth, desiredHeight);

    // decide which gap to fill
    if (newWidth < desiredWidth) {
        aspectRatio = desiredWidth / newWidth;
    }
    if (newHeight < desiredHeight) {
        aspectRatio = desiredHeight / newHeight;
    }
    newWidth *= aspectRatio;
    newHeight *= aspectRatio;

    // calc source rectangle
    cropWidth = imageWidth / (newWidth / desiredWidth);
    cropHeight = imageHeight / (newHeight / desiredHeight);

    cropX = (imageWidth - cropWidth) * 0.5;
    cropY = (imageHeight - cropHeight) * 0.5;

    // make sure source rectangle is valid
    if (cropX < 0) cropX = 0;
    if (cropY < 0) cropY = 0;
    if (cropWidth > imageWidth) cropWidth = imageWidth;
    if (cropHeight > imageHeight) cropHeight = imageHeight;

    // rotate context according to orientation
    context.save();
    context.translate(canvasWidth / 2, canvasHeight / 2);
    context.rotate((90 - window.orientation) * Math.PI/180);

    // additional rotate for front facing camera in lanscape orientation
    if (this._cameraPosition === 'front' &&
        (window.orientation === 90 || window.orientation === -90))
    {
        context.rotate((180) * Math.PI/180);
    }

    // fill image in dest. rectangle
    context.drawImage(image,
        cropX, cropY, cropWidth, cropHeight,
        -desiredWidth / 2, -desiredHeight / 2, desiredWidth, desiredHeight);

    context.restore();
};

/*
CanvasCamera.prototype.base64ToBuffer = function (base64) {
  
  // UniBabel
  // https://github.com/Daplie/unibabel-js/blob/master/index.js
  
  var binstr = atob(base64);

  var buffer;

  if ('undefined' !== typeof Uint8Array) {
    buffer = new Uint8Array(binstr.length); // Uint8ClampedArray
  } else {
    buffer = [];
  }

  Array.prototype.forEach.call(binstr, function (ch, i) {
    buffer[i] = ch.charCodeAt(0);
  });

  return buffer;
}*/

/*
    UniBabel
    https://github.com/Daplie/unibabel-js/blob/master/index.js
*//*
function base64ToBuffer(base64) {
  var binstr = atob(base64);
  var buf = binaryStringToBuffer(binstr);
  return buf;
}
function binaryStringToBuffer(binstr) {
  var buf;

  if ('undefined' !== typeof Uint8Array) {
    buf = new Uint8Array(binstr.length);
  } else {
    buf = [];
  }

  Array.prototype.forEach.call(binstr, function (ch, i) {
    buf[i] = ch.charCodeAt(0);
  });

  return buf;
}*/
/* End: UniBabel */



var CanvasCamera = new CanvasCamera();
module.exports = CanvasCamera;
