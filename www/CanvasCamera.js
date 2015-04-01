//
//  CanvasCamera.js
//  PhoneGap iOS Cordova Plugin to capture Camera streaming into a HTML5 Canvas or an IMG tag.
//
//  Created by Diego Araos <d@wehack.it> on 12/29/12.
//
//  MIT License

cordova.define("cordova/plugin/CanvasCamera", function(require, exports, module) {
    var exec = require('cordova/exec');
    var CanvasCamera = function(){
        var _obj = null;
        var _context = null;
        var _camImage = null;

        var _width = 0;
        var _height = 0;
    };

    CanvasCamera.prototype.initialize = function(obj) {
        var _this = this;
        this._obj = obj;

        this._context = obj.getContext("2d");

        this._camImage = new Image();

        this._camImage.onload = function() {
            var image = this;
            var canvasWidth = _this._width;
            var canvasHeight = _this._height;
            var imageWidth = image.width;
            var imageHeight = image.height;
            var ratio = Math.min(canvasWidth / imageWidth, canvasHeight / imageHeight);
            var newWidth = imageWidth * ratio;
            var newHeight = imageHeight * ratio;
            var cutX, cutY, cutWidth, cutHeight, aspectRatio = 1;

            _this._context.clearRect(0, 0, canvasWidth, canvasHeight);

            /// decide which gap to fill
            if (newWidth < canvasWidth) {
                aspectRatio = canvasWidth / newWidth;
            }
            if (newHeight < canvasHeight) {
                aspectRatio = canvasHeight / newHeight;
            }
            newWidth *= aspectRatio;
            newHeight *= aspectRatio;

            /// calc source rectangle
            cutWidth = imageWidth / (newWidth / canvasWidth);
            cutHeight = imageHeight / (newHeight / canvasHeight);

            cutX = (imageWidth - cutWidth) * 0.5;
            cutY = (imageHeight - cutHeight) * 0.5;

            /// make sure source rectangle is valid
            if (cutX < 0) cutX = 0;
            if (cutY < 0) cutY = 0;
            if (cutWidth > imageWidth) cutWidth = imageWidth;
            if (cutHeight > imageHeight) cutHeight = imageHeight;

            /// fill image in dest. rectangle
            _this._context.drawImage(image,
                cutX, cutY, cutWidth, cutHeight,
                0, 0, canvasWidth, canvasHeight);
        };

        var pixelRatio = window.devicePixelRatio || 1; /// get pixel ratio of device
        console.log('pixelRatio=' + pixelRatio);

        this._width = 200;
        this._height = 200;
        this._obj.width = 200;// * pixelRatio;   /// resolution of canvas
        this._obj.height = 200;// * pixelRatio;

        this._obj.style.width = 200 + 'px';   /// CSS size of canvas
        this._obj.style.height = 200 + 'px';
    };


    CanvasCamera.prototype.start = function(options) {
        cordova.exec(function(imgData){
            if (imgData) {
                this._camImage.src = imgData;
            }
        }.bind(this), false, "CanvasCamera", "startCapture", [options]);
    };

    CanvasCamera.prototype.stop = function() {
        cordova.exec(false, false, "CanvasCamera", "stopCapture", []);
    };


    CanvasCamera.prototype.capture = function(imgData) {
        this._camImage.src = imgData;
    };

    CanvasCamera.prototype.setFlashMode = function(flashMode) {
        cordova.exec(function(){}, function(){}, "CanvasCamera", "setFlashMode", [flashMode]);
    };

    CanvasCamera.prototype.setCameraPosition = function(cameraPosition) {
        cordova.exec(function(){}, function(){}, "CanvasCamera", "setCameraPosition", [cameraPosition]);
    };

    CanvasCamera.prototype.takePicture = function(onsuccess) {
        cordova.exec(onsuccess, function(){}, "CanvasCamera", "captureImage", []);
    };

    var myplugin = new CanvasCamera();
    module.exports = myplugin;
});

var CanvasCamera = cordova.require("cordova/plugin/CanvasCamera");

module.exports = CanvasCamera;
