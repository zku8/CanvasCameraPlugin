package com.virtuoworks.cordova.plugin.canvascamera;

import org.json.JSONArray;
import org.json.JSONObject;

public interface CanvasCameraInterface {

    public void setDefaultOptions();

    public void parseAdditionalOptions(JSONObject options) throws Exception;

    public void addPluginResultDataOutput(byte[] imageRawJpegData, JSONObject pluginResultDataOutput);

}
