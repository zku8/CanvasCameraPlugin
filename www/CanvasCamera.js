//
//  CanvasCamera.js
//  PhoneGap iOS Cordova Plugin to capture Camera streaming into a HTML5 Canvas or an IMG tag.
//
//  Created by Diego Araos <d@wehack.it> on 12/29/12.
//
//  MIT License

var exec = require('cordova/exec');
var CanvasCamera = function(){
    var _obj = null;
    var _context = null;
    var _camImage = null;
    var _cameraPosition = null;

    var _width = 0;
    var _height = 0;
};

CanvasCamera.prototype.initialize = function(obj, width, height) {
    this._obj = obj;
    this._cameraPosition = 'back';
    this._context = obj.getContext("2d");

    this._width = width;
    this._height = height;
    this._obj.width = width;
    this._obj.height = height;
    this._obj.style.width = width + 'px';
    this._obj.style.height = height + 'px';

    this._camImage = new Image();
    this._camImage.onload = function() {
        this.drawImage();
    }.bind(this);
};


CanvasCamera.prototype.start = function(options) {
    cordova.exec(this.capture.bind(this), false, "CanvasCamera", "startCapture", [options]);
};

CanvasCamera.prototype.stop = function() {
    cordova.exec(false, false, "CanvasCamera", "stopCapture", []);
};


CanvasCamera.prototype.capture = function(imgData) {
    if (imgData) {
        this._camImage.src = imgData;
    }
};

CanvasCamera.prototype.setFlashMode = function(flashMode) {
    cordova.exec(function(){}, function(){}, "CanvasCamera", "setFlashMode", [flashMode]);
};

CanvasCamera.prototype.setCameraPosition = function(cameraPosition) {
    cordova.exec(function(){
        this._cameraPosition = cameraPosition;
    }.bind(this), function(){}, "CanvasCamera", "setCameraPosition", [cameraPosition]);
};

CanvasCamera.prototype.drawImage = function() {
    var image = this._camImage;
    var context = this._context;
    var canvasWidth = this._width;
    var canvasHeight = this._height;
    var imageWidth = image.width;
    var imageHeight = image.height;
    var ratio = Math.min(canvasWidth / imageWidth, canvasHeight / imageHeight);
    var newWidth = imageWidth * ratio;
    var newHeight = imageHeight * ratio;
    var cropX, cropY, cropWidth, cropHeight, aspectRatio = 1;

    context.clearRect(0, 0, canvasWidth, canvasHeight);

    // decide which gap to fill
    if (newWidth < canvasWidth) {
        aspectRatio = canvasWidth / newWidth;
    }
    if (newHeight < canvasHeight) {
        aspectRatio = canvasHeight / newHeight;
    }
    newWidth *= aspectRatio;
    newHeight *= aspectRatio;

    // calc source rectangle
    cropWidth = imageWidth / (newWidth / canvasWidth);
    cropHeight = imageHeight / (newHeight / canvasHeight);

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
        -canvasWidth / 2, -canvasHeight / 2, canvasWidth, canvasHeight);

    context.restore();
};

var CanvasCamera = new CanvasCamera();
module.exports = CanvasCamera;
