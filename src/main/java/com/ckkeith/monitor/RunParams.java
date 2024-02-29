// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

public class RunParams {

	public class Dataset {
		// microcontrollerName -> list of sensor names
		Hashtable<String, HashMap<String, String>> microcontrollers;
		public Dataset(Hashtable<String, HashMap<String, String>> microcontrollers) {
			this.microcontrollers = microcontrollers;
		}
	};

	public class SheetConfig {
		public Integer				dataIntervalInMinutes = 20;
		public Integer				writeIntervalInSeconds = 10;
		public Integer				expectedDataIntervalInSeconds = writeIntervalInSeconds;
		Boolean						missingDataShowsBlank = false;
		public ArrayList<Dataset>	dataSets;
		public String				sheetName;
	};

	Integer		htmlWriteIntervalInSeconds = 5;
	public int	dataIntervalInMinutes = 10;
	String		devicesToReport = "";
	Integer		csvTimeGranularityInSeconds = 300; // E.g. '60' == round to minutes, '30' == round to minutes and half-minutes.
	Boolean		writeLongTermData = false;
	Hashtable<String, SheetConfig> sheets = new Hashtable<String, SheetConfig>();
	int			temperatureLimit = 90; // degrees F
	int			timeLimit = 60; // minutes before alert is logged.
	String		emailTo = "chris.keith@gmail.com";
	Integer		gapTriggerInMinutes = 10; // Display gaps in the data if they are longer than this.
	Integer		daysOfGapData = 30; // Go back this many days for gap data.
	Boolean		missingDataShowsBlank = false;

	 // If temperature doesn't go down (after sending email) in resendIntervalInMinutes,
	 // keep resending emails until it does.
	 Integer	resendIntervalInMinutes = 10;
			
	private static Integer getInteger(Element root, String name, Integer defaultValue) {
		NodeList nl = root.getElementsByTagName(name);
		if (nl.getLength() > 0) {
			return Integer.parseInt(nl.item(0).getTextContent());
		}
		return defaultValue;
	}

	private static String getString(Element root, String name, String defaultValue) {
		NodeList nl = root.getElementsByTagName(name);
		if (nl.getLength() > 0) {
			return nl.item(0).getTextContent();
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
		sheetConfig.dataIntervalInMinutes = Integer.valueOf(getNodeList(sheetElement, "dataIntervalInMinutes").item(0).getTextContent());
		sheetConfig.writeIntervalInSeconds = Integer.valueOf(getNodeList(sheetElement, "writeIntervalInSeconds").item(0).getTextContent());
		sheetConfig.expectedDataIntervalInSeconds = getInteger(sheetElement, "expectedDataIntervalInSeconds", sheetConfig.writeIntervalInSeconds);
		NodeList blankDataNodeList = sheetElement.getElementsByTagName("missingDataShowsBlank");
		if (blankDataNodeList.getLength() > 0) {
			sheetConfig.missingDataShowsBlank = Boolean.valueOf(blankDataNodeList.item(0).getTextContent());
		}
		return sheetConfig;
	}

	private void addSheet(Element sheetElement) throws Exception {
		NodeList sheetIdList = getNodeList(sheetElement, "sheetId");
		String sheetId = sheetIdList.item(0).getTextContent();
		SheetConfig sc = loadSheetConfig(sheetElement);
		this.dataIntervalInMinutes = sc.dataIntervalInMinutes;
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
		rp.csvTimeGranularityInSeconds = getInteger(root, "csvTimeGranularityInSeconds", rp.csvTimeGranularityInSeconds);
		rp.writeLongTermData = getInteger(root, "writeLongTermData", 0) == 0 ? false : true;
		rp.devicesToReport = getString(root, "devicesToReport", rp.devicesToReport);
		rp.temperatureLimit = getInteger(root, "temperatureLimit", 80);
		rp.timeLimit = getInteger(root, "timeLimit", 60);
		rp.emailTo = getString(root, "emailTo", "chris.keith@gmail.com");
		rp.resendIntervalInMinutes = getInteger(root, "resendIntervalInMinutes", 10);
		rp.gapTriggerInMinutes = getInteger(root, "gapTriggerInMinutes", 10);
		rp.daysOfGapData = getInteger(root, "daysOfGapData", 30);
		rp.loadSheets(root);
		return rp;
	}

	static RunParams loadFromXML(String filePath) throws Exception {
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
				+ ", devicesToReport = " + devicesToReport
				+ ", csvTimeGranularityInSeconds = " + csvTimeGranularityInSeconds
				+ ", temperatureLimit = " + temperatureLimit
				+ ", timeLimit = " + timeLimit
				+ ", emailTo = " + emailTo
				+ ", resendIntervalInMinutes = " + resendIntervalInMinutes
				+ ", gapTriggerInMinutes = " + gapTriggerInMinutes
				+ ", daysOfGapData = " + daysOfGapData
				;
	}
}
