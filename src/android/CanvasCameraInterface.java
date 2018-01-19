package com.virtuoworks.cordova.plugin.canvascamera;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Created by Sami Radi on 18/01/2018.
 */

public interface CanvasCameraInterface {

    public void setDefaultOptions();

    public void parseAdditionalOptions(JSONObject options) throws Exception;

    public void addPluginResultDataOutput(byte[] imageRawJpegData, JSONObject pluginResultDataOutput);

}
