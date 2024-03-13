// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;

import javax.json.*;

public class RunParams {

	public record JsonDataset(String microcontrollerName, String particleEventName,
								String jsonEventName, String displayName) {}
	public ArrayList<JsonDataset> jsonDatasets = new ArrayList<JsonDataset>();

	Integer		csvWriteIntervalInSeconds = 15;
	public int	dataIntervalInMinutes = 10;
	
	public static RunParams loadFromJson() throws FileNotFoundException {
		RunParams rp = new RunParams();
		File datasetFile = Utils.findResourceFile("runparams.json");
		JsonReader jsonReader = Json.createReader(new FileReader(datasetFile));
		JsonObject mainObj = jsonReader.readObject();
		rp.csvWriteIntervalInSeconds = Integer.valueOf(((JsonNumber)mainObj.get("csvWriteIntervalInSeconds")).intValue());
		rp.dataIntervalInMinutes = Integer.valueOf(((JsonNumber)mainObj.get("dataIntervalInMinutes")).intValue());
		JsonArray a = (JsonArray)mainObj.get("datasets");
		for (JsonValue val : a) {
			JsonObject obj = (JsonObject)val;
			String microcontrollerName = ((JsonString)obj.get("microcontrollerName")).getString();
			String particleEventName = ((JsonString)obj.get("particleEventName")).getString();
			String jsonEventName = ((JsonString)obj.get("jsonEventName")).getString();
			String displayName = ((JsonString)obj.get("displayName")).getString();
			rp.jsonDatasets.add(new JsonDataset(microcontrollerName, particleEventName, jsonEventName, displayName));			
		}
		return rp;
	}

	public Boolean containsSensor(String deviceName, String sensorName) {
		for (JsonDataset ds : this.jsonDatasets) {
			if (ds.microcontrollerName.equals(deviceName) && ds.jsonEventName.equals(sensorName)) {
				return true;
			}
		}
		return false;
	}

	public String getDisplayNameForSensor(String deviceName, String sensorName) {
		for (JsonDataset ds : this.jsonDatasets) {
			if (ds.microcontrollerName.equals(deviceName) && ds.jsonEventName.equals(sensorName)) {
				return ds.displayName;
			}
		}
		return deviceName + ":" + sensorName;
	}
}
