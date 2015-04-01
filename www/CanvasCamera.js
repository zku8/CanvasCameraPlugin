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
        var _orientation = 'landscape';
        var _obj = null;
        var _context = null;
        var _camImage = null;

        var _x = 0;
        var _y = 0;
        var _width = 0;
        var _height = 0;
    };




    CanvasCamera.prototype.initialize = function(obj) {
        var _this = this;
        this._obj = obj;

        this._context = obj.getContext("2d");

        this._camImage = new Image();

        this._camImage.onload = function() {
            console.log("_this._context.clearRect"+_this._width+","+_this._height);
            _this._context.clearRect(0, 0, _this._width, _this._height);
            if (window.orientation == 90
               || window.orientation == -90)
            {
                _this._context.save();
                // rotate 90
                console.log("_this._context.translate"+(_this._width/2)+","+ (_this._height/2));
                _this._context.translate(_this._width/2, _this._height/2);
                _this._context.rotate((90 - window.orientation) *Math.PI/180);
                _this._context.drawImage(_this._camImage, 0, 0, 352, 288, -_this._width/2, -_this._height/2, _this._width, _this._height);
                //
                _this._context.restore();
            }
            else
            {
                console.log("comes in else");
                _this._context.save();
                // rotate 90
                _this._context.translate(_this._width/2, _this._height/2);
                _this._context.rotate((90 - window.orientation) *Math.PI/180);
                _this._context.drawImage(_this._camImage, 0, 0, 352, 288, -_this._height/2, -_this._width/2, _this._height, _this._width);
                //
                _this._context.restore();
            }
        };

        // register orientation change event
        window.addEventListener('orientationchange', this.doOrientationChange);
        this.doOrientationChange();
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

    CanvasCamera.prototype.doOrientationChange = function() {
        console.log('doOrientationChange');
        switch(window.orientation)
        {
            case -90:
            case 90:
                this._orientation = 'landscape';
                break;
            default:
                this._orientation = 'portrait';
                break;
        }
        console.log('window.innerWidth'+window.innerWidth);
        console.log('window.innerHeight'+window.innerHeight);

        //var windowWidth = window.innerWidth;
        //var windowHeight = window.innerHeight;

        var windowWidth = 200;
        var windowHeight = 200;

        var pixelRatio = window.devicePixelRatio || 1; /// get pixel ratio of device


        this._obj.width = windowWidth;// * pixelRatio;   /// resolution of canvas
        this._obj.height = windowHeight;// * pixelRatio;
        console.log('this._obj.width'+this._obj.width);

        this._obj.style.width = windowWidth + 'px';   /// CSS size of canvas
        this._obj.style.height = windowHeight + 'px';
        console.log('this._obj.style.width'+this._obj.style.width);

        this._x = 0;
        this._y = 0;
        this._width = windowWidth;
        this._height = windowHeight;
        console.log('this._width'+this._width+" this._height"+this._height);
    };

    CanvasCamera.prototype.takePicture = function(onsuccess) {
        cordova.exec(onsuccess, function(){}, "CanvasCamera", "captureImage", []);
    };

    var myplugin = new CanvasCamera();
    module.exports = myplugin;
});

var CanvasCamera = cordova.require("cordova/plugin/CanvasCamera");


var DestinationType = {
    DATA_URL : 0,
    FILE_URI : 1
};

var PictureSourceType = {
    PHOTOLIBRARY : 0,
    CAMERA : 1,
    SAVEDPHOTOALBUM : 2
};

var EncodingType = {
    JPEG : 0,
    PNG : 1
};

var CameraPosition = {
    BACK : 0,
    FRONT : 1
};

var CameraPosition = {
    BACK : 1,
    FRONT : 2
};

var FlashMode = {
    OFF : 0,
    ON : 1,
    AUTO : 2
};

CanvasCamera.DestinationType = DestinationType;
CanvasCamera.PictureSourceType = PictureSourceType;
CanvasCamera.EncodingType = EncodingType;
CanvasCamera.CameraPosition = CameraPosition;
CanvasCamera.FlashMode = FlashMode;

module.exports = CanvasCamera;
