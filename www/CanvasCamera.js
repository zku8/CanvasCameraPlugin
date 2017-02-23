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
    var _userOptions = {};

    var _fullsizeElement = null;
    var _fullsizeContext = null;
    var _fullsizeImage = null;
    var _onDrawFullsizeCallback = null;

    var _thumbnailElement = null;
    var _thumbnailContext = null;
    var _thumbnailImage = null;
};

CanvasCamera.prototype.initialize = function(canvasElement, thumbnailElement) {
    if (thumbnailElement !== undefined) {
        this._thumbnailElement = thumbnailElement;
        this._thumbnailContext = thumbnailElement.getContext("2d");

        this._thumbnailImage = new Image();
        this._thumbnailImage.onload = function() {
        this.setThumbnailCanvasDimensions();
        this.drawThumbnailImage();
        if(this._onDrawThumbnailCallback) {
          this._onDrawThumbnailCallback(this._thumbnailContext);
        }
        }.bind(this);
    }

    this._fullsizeElement = canvasElement;
    this._fullsizeContext = canvasElement.getContext("2d");
    this._fullsizeImage = new Image();
    this._fullsizeImage.onload = function() {
        this.setFullsizeCanvasDimensions();
        this.drawFullsizeImage();
        if(this._onDrawFullsizeCallback) {
          this._onDrawFullsizeCallback(this._fullsizeContext);
        }
    }.bind(this);
};

CanvasCamera.prototype.start = function(options) {
    this._userOptions = options;

    this.setFullsizeCanvasDimensions();

    if (this._thumbnailElement) {
        this._userOptions.hasThumbnail = true;

        this.setThumbnailCanvasDimensions();

    } else {
        this._userOptions.hasThumbnail = false;
    }

    cordova.exec(this.capture.bind(this), function(error) {
        console.log('start error', error);
    }, "CanvasCamera", "startCapture", [options]);
};

CanvasCamera.prototype.stop = function() {
    cordova.exec(false, function(error) {
        console.log('stop error', error);
    }, "CanvasCamera", "stopCapture", []);
};

CanvasCamera.prototype.capture = function(image) {
    //console.log('images: ', image);
    //console.log('typeof images: ', typeof image);
    if (image && image.fullsize) {
        this._fullsizeImage.src = image.fullsize;
    }

    if (image && image.thumbnail && this._thumbnailImage) {
        this._thumbnailImage.src = image.thumbnail;
    }
};

CanvasCamera.prototype.setOnDrawFullsize = function(onDrawFullsizeCallback) {
  this._onDrawFullsizeCallback = onDrawFullsizeCallback || null;
};

CanvasCamera.prototype.setOnDrawThumbnail = function(onDrawThumbnailCallback) {
  this._onDrawThumbnailCallback = onDrawThumbnailCallback || null;
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

CanvasCamera.prototype.setFullsizeCanvasDimensions = function() {
    if (this._userOptions.width) {
        this._fullsizeElement.width = this._userOptions.width;
    } else {
        this._fullsizeElement.width = window.innerWidth;
    }

    if (this._userOptions.height) {
        this._fullsizeElement.height = this._userOptions.height;
    } else {
        this._fullsizeElement.height = window.innerHeight;
    }
}

CanvasCamera.prototype.setThumbnailCanvasDimensions = function() {
    if (this._fullsizeElement && this._thumbnailElement) {
        if (!this._userOptions.thumbnailRatio) {
            this._userOptions.thumbnailRatio = 1/6;
        }
        this._thumbnailElement.width = parseInt(this._fullsizeElement.width * this._userOptions.thumbnailRatio);
        this._thumbnailElement.height = parseInt(this._fullsizeElement.height * this._userOptions.thumbnailRatio);
    }
}

CanvasCamera.prototype.drawFullsizeImage = function() {
    var image = this._fullsizeImage;
    var context = this._fullsizeContext;

    context.drawImage(image, 0, 0, image.width, image.height);
};

CanvasCamera.prototype.drawThumbnailImage = function() {
    var image = this._thumbnailImage;
    var context = this._thumbnailContext;

    context.drawImage(image, 0, 0, image.width, image.height);
};

var CanvasCamera = new CanvasCamera();
module.exports = CanvasCamera;
