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
    var _userOptions = null;
    var _canvasElement = null;
    var _canvasContext = null;
    var _imageElement = null;
    var _onDrawCallback = null;
};

CanvasCamera.prototype.initialize = function(canvasElement) {
    this._canvasElement = canvasElement;
    this._canvasContext = canvasElement.getContext("2d");
    this._imageElement = new Image();
    this._imageElement.onload = function() {
        this.setCanvasDimensions();
        this.drawImage();
        if(this._onDrawCallback) {
          this._onDrawCallback(this._canvasContext);
        }
    }.bind(this);
};

CanvasCamera.prototype.start = function(options) {
    this._userOptions = options;
/*    this._canvasElement.width = options.width;
    this._canvasElement.height = options.height;*/
    this.setCanvasDimensions();
    cordova.exec(this.capture.bind(this), function(error) {
        console.log('start error', error);
    }, "CanvasCamera", "startCapture", [options]);
};

CanvasCamera.prototype.stop = function() {
    cordova.exec(false, function(error) {
        console.log('stop error', error);
    }, "CanvasCamera", "stopCapture", []);
};

CanvasCamera.prototype.capture = function(imgSrc) {
    if (imgSrc) {
        this._imageElement.src = imgSrc;
    }
};

CanvasCamera.prototype.setOnDraw = function(onDrawCallback) {
  this._onDrawCallback = onDrawCallback || null;
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

CanvasCamera.prototype.getOrientation = function() {
    let currentOrientation = "";
    if (window.orientation == 0) {
        currentOrientation = "portrait";
    } else if (window.orientation == 90) {
        currentOrientation = "landscape";
    } else if (window.orientation == -90) {
        currentOrientation = "landscape";
    } else if (window.orientation == 180) {
        currentOrientation = "portrait";
    }
    return currentOrientation
}

CanvasCamera.prototype.getCanvasWidth = function() {
    return this._canvasElement.width;
}

CanvasCamera.prototype.getCanvasHeight = function() {
    return this._canvasElement.height;
}

CanvasCamera.prototype.setCanvasDimensions = function() {
    if (this._userOptions.width) {
        this._canvasElement.width = this._userOptions.width;
    } else {
        this._canvasElement.width = window.innerWidth;
    }

    if (this._userOptions.height) {
        this._canvasElement.height = this._userOptions.height;
    } else {
        this._canvasElement.height = window.innerHeight;
    }
}

CanvasCamera.prototype.drawImage = function() {
    // console.log('window.orientation: ' + window.orientation);

/*
    var image = this._imageElement;
    var context = this._canvasContext;
    var canvasWidth = this._canvasElement.width = this._canvasElement.clientWidth;
    var canvasHeight = this._canvasElement.height = this._canvasElement.clientHeight;

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
*/



    // rotate context according to orientation
    /*context.save();
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
    */


    var image = this._imageElement;
    var context = this._canvasContext;

    context.drawImage(image, 0, 0, image.width, image.height);
};


var CanvasCamera = new CanvasCamera();
module.exports = CanvasCamera;