// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import javax.json.*;

public class RunParams {

	public record JsonDataset(String microcontrollerName, String particleEventName,
								String jsonEventName, String displayName) {}
	public ArrayList<JsonDataset> jsonDatasets = new ArrayList<JsonDataset>();

	public class Dataset {
		// microcontrollerName -> list of sensor names
		Hashtable<String, HashMap<String, String>> microcontrollers;
		public Dataset(Hashtable<String, HashMap<String, String>> microcontrollers) {
			this.microcontrollers = microcontrollers;
		}
	};

	public class SheetConfig {
		public ArrayList<Dataset>	dataSets;
	};

	Integer		htmlWriteIntervalInSeconds = 5;
	public int	dataIntervalInMinutes = 10;
	Hashtable<String, SheetConfig> sheets = new Hashtable<String, SheetConfig>();

	private static RunParams loadFromJson() throws FileNotFoundException {
		RunParams rp = new RunParams();
		File datasetFile = Utils.findFile("src/main/resources/com/ckkeith/monitor/runparams.json");
		JsonReader jsonReader = Json.createReader(new FileReader(datasetFile));
		JsonObject mainObj = jsonReader.readObject();
		rp.htmlWriteIntervalInSeconds = Integer.valueOf(((JsonNumber)mainObj.get("htmlWriteIntervalInSeconds")).intValue());
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

	private static Integer getInteger(Element root, String name, Integer defaultValue) {
		NodeList nl = root.getElementsByTagName(name);
		if (nl.getLength() > 0) {
			return Integer.parseInt(nl.item(0).getTextContent());
		}
		return defaultValue;
	}

	private ArrayList<Dataset> buildDatasetList(NodeList datasetElems) throws Exception {
		ArrayList<Dataset> datasets = new ArrayList<Dataset>();
		for (int i = 0; i < datasetElems.getLength(); i++) {
			Node datasetNode = datasetElems.item(i);
			if (datasetNode.getNodeType() == Node.ELEMENT_NODE) {
				Hashtable<String, HashMap<String, String>> microcontrollers = 
							new Hashtable<String, HashMap<String, String>>();
				Element elem = (Element)datasetNode;
				NodeList microcontrollerNameElems = elem.getElementsByTagName("microcontrollerName");
				if (microcontrollerNameElems.getLength() == 0) {
					throw new Exception("microcontrollerName.getLength() == 0");
				}
				String microcontrollerName = microcontrollerNameElems.item(0).getTextContent();
				NodeList sensors = elem.getElementsByTagName("sensors");
				if (sensors.getLength() == 0) {
					throw new Exception("sensors.getLength() == 0");
				}
				HashMap<String, String> sensorNames = new HashMap<String, String>();
				NodeList sensorNodes = ((Element)sensors.item(0)).getElementsByTagName("sensor");
				for (int nameIndex = 0; nameIndex < sensorNodes.getLength(); nameIndex++) {
					Node sensorNode = sensorNodes.item(nameIndex);
					if (sensorNode != null && sensorNode.getNodeType() == Node.ELEMENT_NODE) {
						Element sensorElem = (Element)sensorNode;
						String name = sensorElem.getElementsByTagName("name").item(0).getTextContent();
						String displayName = sensorElem.getElementsByTagName("displayName").item(0).getTextContent();
						sensorNames.put(name, displayName);
					}
				}
				microcontrollers.put(microcontrollerName, sensorNames);
				datasets.add(new Dataset(microcontrollers));
			}
		}
		return datasets;
	}

	NodeList getNodeList( Element element, String id) throws Exception {
		NodeList nodeList = element.getElementsByTagName(id);
		if (nodeList.getLength() == 0) {
			throw new Exception(id + ": getLength() == 0");
		}
		return nodeList;
	}

	private SheetConfig loadSheetConfig(Element sheetElement) throws Exception {
		SheetConfig sheetConfig = new SheetConfig();
		NodeList datasetElems = getNodeList(sheetElement, "dataSet");
		sheetConfig.dataSets = buildDatasetList(datasetElems);
		this.dataIntervalInMinutes = Integer.valueOf(getNodeList(sheetElement, "dataIntervalInMinutes").item(0).getTextContent());
		return sheetConfig;
	}

	private void addSheet(Element sheetElement) throws Exception {
		NodeList sheetIdList = getNodeList(sheetElement, "sheetId");
		String sheetId = sheetIdList.item(0).getTextContent();
		SheetConfig sc = loadSheetConfig(sheetElement);
		sheets.put(sheetId, sc);
	}

	private void loadSheets(Element root) throws Exception {
		NodeList sheetsRoot = root.getElementsByTagName("sheets");
		if (sheetsRoot.getLength() == 0) {
			throw new Exception("sheetsRoot.getLength() == 0");
		}
		Node sheets = sheetsRoot.item(0);
		if (sheets.getNodeType() != Node.ELEMENT_NODE) {
			throw new Exception("sheets.getNodeType() != Node.ELEMENT_NODE");
		}
		NodeList sheetsList = ((Element)sheets).getElementsByTagName("sheet");
		for (int i = 0; i < sheetsList.getLength(); i++) {
			if (sheetsList.item(i).getNodeType() == Node.ELEMENT_NODE) {
				addSheet((Element)sheetsList.item(i));
			}
		}
	}

	private static RunParams loadFromDOM(Element root) throws Exception {
		RunParams rp = new RunParams();
		rp.htmlWriteIntervalInSeconds = getInteger(root, "htmlWriteIntervalInSeconds", rp.htmlWriteIntervalInSeconds);
		rp.loadSheets(root);
		return rp;
	}

	static RunParams loadFromXML(String filePath) throws Exception {
		loadFromJson();
		Element root = Utils.readTextFileIntoDOM(filePath).getDocumentElement();
		return loadFromDOM(root);
	}

	static RunParams loadFromXMLString(String xml) throws Exception {
		Element root = Utils.readStringIntoDOM(xml).getDocumentElement();
		return loadFromDOM(root);
	}

	public Boolean containsSensor(String deviceName, String sensorName) {
		for (Map.Entry<String, SheetConfig> sheetEntry : this.sheets.entrySet()) {
			for (Dataset dataSet : sheetEntry.getValue().dataSets) {
				for (Map.Entry<String, HashMap<String, String>> microcontroller :
										dataSet.microcontrollers.entrySet()) {
					if (microcontroller.getKey().equals(deviceName)) {
						return true;
					}

				}
			}
		}
		return false;
	}

	public String getDisplayNameForSensor(String deviceName, String sensorName) {
		for (Map.Entry<String, SheetConfig> sheetEntry : this.sheets.entrySet()) {
			for (Dataset dataSet : sheetEntry.getValue().dataSets) {
				for (Map.Entry<String, HashMap<String, String>> microcontroller :
										dataSet.microcontrollers.entrySet()) {
					if (microcontroller.getKey().equals(deviceName)) {
						for (Map.Entry<String, String> sensorEntry : 
										microcontroller.getValue().entrySet()) {
							if (sensorEntry.getKey().equals(sensorName)) {
								return sensorEntry.getValue();
							}
						}
					}

				}
			}
		}
		return deviceName + ":" + sensorName;
	}

	public String toString() {
		return "RunParams : "
				+ ", htmlWriteIntervalInSeconds = " + htmlWriteIntervalInSeconds
				;
	}
}
