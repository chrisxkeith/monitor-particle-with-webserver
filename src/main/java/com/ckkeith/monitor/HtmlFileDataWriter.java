// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HtmlFileDataWriter implements Runnable {

	private AccountMonitor accountMonitor;
	
	private ConcurrentSkipListMap<LocalDateTime, ConcurrentSkipListMap<String, String>> sensorData = 
		new ConcurrentSkipListMap<LocalDateTime, ConcurrentSkipListMap<String, String>>();
	private ConcurrentSkipListMap<String, String> sensorNames = new ConcurrentSkipListMap<String, String>();

	private static String separator = "/";
	Map<String, String> displayNameToSensorName = new HashMap<String, String>();

	public HtmlFileDataWriter(AccountMonitor accountMonitor) {
		this.accountMonitor = accountMonitor;
		Iterator<RunParams.JsonDataset> sensorNameIt = accountMonitor.runParams.jsonDatasets.iterator();
		while (sensorNameIt.hasNext()) {
			RunParams.JsonDataset ds = sensorNameIt.next();
			displayNameToSensorName.put(ds.displayName(), ds.microcontrollerName() + separator + ds.jsonEventName());
		}
		Utils.logToConsole("CSV : " + getCSVFileName());
		Utils.logToConsole("HTML: file://" + Utils.findResourceFile("sensorgraph.html").getAbsolutePath());
		Utils.logToConsole("JSON: http://localhost:8080/sensordata");
	}
	
	public void addData(SensorDataPoint sensorDataPoint) {
		synchronized (this) {
			String fullSensorName = sensorDataPoint.deviceName + separator + sensorDataPoint.sensorName;
			sensorNames.put(fullSensorName, sensorDataPoint.sensorName);
			ConcurrentSkipListMap<String, String> sensorValues = sensorData.get(sensorDataPoint.timestamp);
			if (sensorValues == null) {
				sensorValues = new ConcurrentSkipListMap<String, String>();
			}
			sensorValues.put(fullSensorName, sensorDataPoint.sensorData);
			sensorData.put(sensorDataPoint.timestamp, sensorValues);
		}
	}

	private String getDisplayNameForSensor(String fullSensorName) {
		String[] deviceSensorNames = fullSensorName.split(separator);
		return accountMonitor.runParams.getDisplayNameForSensor(deviceSensorNames[0], 
																deviceSensorNames[1]);
	}

	private LocalDateTime[] findTimeLimits() {
		LocalDateTime min = LocalDateTime.MAX;
		LocalDateTime max = LocalDateTime.MIN;
		Set<LocalDateTime> keys = sensorData.keySet();
		Iterator<LocalDateTime> itr = keys.iterator();
		while (itr.hasNext()) {
			LocalDateTime timestamp = itr.next();
			if (timestamp.isBefore(min)) {
				min = timestamp;
			}
			if (timestamp.isAfter(max)) {
				max = timestamp;
			}
		}
		LocalDateTime[] ret = { min, max };
		return ret;
	}

	void deleteOldData() {
		LocalDateTime start = LocalDateTime.now().minusMinutes(accountMonitor.runParams.dataIntervalInMinutes);
		Set<LocalDateTime> keys = sensorData.keySet();
		Iterator<LocalDateTime> itr = keys.iterator();
		while (itr.hasNext()) {
			LocalDateTime timestamp = itr.next();
			if (timestamp.isBefore(start)) {
				sensorData.remove(timestamp);
			}
		}
	}
	
	private boolean chromeStarted = false;
	@SuppressWarnings("unused")
	private void startChrome(String fn) {
		if (! chromeStarted) {
		    try {
				List<String> params = new ArrayList<String>();
				params.add("c:/Program Files (x86)/Google/Chrome/Application/chrome.exe");
				params.add(fn);
			    ProcessBuilder pb = new ProcessBuilder(params);
				pb.start();
				chromeStarted = true;
			} catch (Exception e) {
				Utils.logToConsole("Running chrome failed, currently only works on Windows. " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private LocalDateTime lastTimeStampWrittenToFile = LocalDateTime.of(1900, 1, 1, 0, 0);
	private ConcurrentSkipListMap<LocalDateTime, ConcurrentSkipListMap<String, String>>
				createMapAtOneSecondResolution() {
		ConcurrentSkipListMap<LocalDateTime, ConcurrentSkipListMap<String, String>> newMap =
				new ConcurrentSkipListMap<LocalDateTime, ConcurrentSkipListMap<String, String>>();
		Iterator<LocalDateTime> sensorDataIt = sensorData.keySet().iterator();
		LocalDateTime lastTimeStamp = LocalDateTime.of(1900, 1, 1, 0, 0);
		while (sensorDataIt.hasNext()) {
			LocalDateTime timestamp = sensorDataIt.next();
			LocalDateTime timestampInSeconds = timestamp.truncatedTo(ChronoUnit.SECONDS);
			if (timestampInSeconds.isAfter(lastTimeStampWrittenToFile)) {
				ConcurrentSkipListMap<String, String> oldEntries = sensorData.get(timestamp);
				ConcurrentSkipListMap<String, String> newEntries = newMap.get(timestampInSeconds);
				if (newEntries == null) {
					newMap.put(timestampInSeconds, oldEntries);
				} else {
					for (Map.Entry<String, String> e : oldEntries.entrySet()) {
						newEntries.put(e.getKey(), e.getValue());
					}
				}
				lastTimeStamp = timestampInSeconds;
			}
		}
		lastTimeStampWrittenToFile = lastTimeStamp;
		return newMap;
	}

	private void addHeader(StringBuilder sb) {
		sb.append("Time");
		for (Entry<String, String> displayNameToSensor : displayNameToSensorName.entrySet()) {
			sb.append(",");
			sb.append(displayNameToSensor.getKey());
		}
		sb.append("\n");
	}

	private StringBuilder getFullCSV(Boolean doAppend) {
		StringBuilder sb = new StringBuilder();
		if (!doAppend) {
			addHeader(sb);
		}
		ConcurrentSkipListMap<LocalDateTime, ConcurrentSkipListMap<String, String>> newMap =
						createMapAtOneSecondResolution();
		Iterator<LocalDateTime> sensorDataIt = newMap.keySet().iterator();
		while (sensorDataIt.hasNext()) {
			LocalDateTime timestamp = sensorDataIt.next();
			sb.append(timestamp);
			ConcurrentSkipListMap<String, String> entries = newMap.get(timestamp);
			for (Entry<String, String> displayNameToSensor : displayNameToSensorName.entrySet()) {
				String val = entries.get(displayNameToSensor.getValue());
				sb.append(",");
				if (val != null) {
					sb.append(val);
				}
			}
			sb.append("\n");
		}
		return sb;
	}

	private String getCSVFileName() {
		String today = (new SimpleDateFormat("yyyy-MM-dd")).format(new java.util.Date());
		try {
			return Utils.getLogFileName(accountMonitor.accountName,
								"sensordata-" + today + ".csv");
		} catch (Exception e) {
			Utils.logToConsole("Unable to getCSVFileName()");
			e.printStackTrace();
			return "";
		}
	}

	private void writeCSV() throws Exception {
		deleteOldData();
		String fileName = getCSVFileName();
		Boolean doAppend = (new File(fileName)).exists();
		FileWriter csvStream = new FileWriter(fileName, true);
		try {
			csvStream.write(getFullCSV(doAppend).toString());
		} finally {
			csvStream.close();
		}
	}

	private void writeHtmlForDay(String theDay) throws Exception {
		String fileName =  Utils.getLogFileName(accountMonitor.accountName,
							"sensordata-" + theDay + ".html");
		FileWriter htmlStream = new FileWriter(fileName, false);
		try {
			htmlStream.write("<!DOCTYPE html>\r\n" + //
								"\r\n" + //
								"<html>\r\n" + //
								"<body>\r\n" + //
								"<script src=\"https://cdnjs.cloudflare.com/ajax/libs/moment.js/2.22.2/moment.min.js\"></script>\r\n" + //
								"<script src=\"https://cdnjs.cloudflare.com/ajax/libs/Chart.js/2.7.3/Chart.min.js\"></script>\r\n" + //
								"\r\n" + //
								"<canvas id=\"myChart\" width=\"1500\" height=\"600\"></canvas>\r\n" + //
								"<script>\r\n" + //
								"    var ctx = document.getElementById(\"myChart\").getContext('2d');\r\n" + //
								"\tvar myChart = new Chart(ctx, {\r\n" + //
								"\t\ttype : 'line',\r\n" + //
								"\t\tdata : {\"datasets\": ");
			ObjectMapper mapper = new ObjectMapper();
			htmlStream.write(mapper.writeValueAsString(datasets()));
			htmlStream.write("}, \r\n\t\toptions: ");
			htmlStream.write(mapper.writeValueAsString(options()));
			htmlStream.write("\r\n\t});\r\n" + //
								"</script>\r\n" + //
								"</body>\r\n" + //
								"</html>");
		} finally {
			htmlStream.close();
		}
	}

	private void writeHtml() throws Exception {
		writeHtmlForDay((new SimpleDateFormat("yyyy-MM-dd")).format(new java.util.Date()));
	}

	String getHTMLFileName(String deviceName) throws Exception {
		String safeFn;
		if (deviceName == null) {
			safeFn = "all";
		} else {
			safeFn = Utils.getSafeName(deviceName);
		}
		String today = (new SimpleDateFormat("-yyyy-MM-dd-")).format(new java.util.Date());
		safeFn += today;
		return safeFn;
	}

	private record Datapoint(String t, Integer y) {}
	private record Dataset(String label, Integer lineTension, String borderColor, String backgroundColor, Object[] data) {}
	
	private final String colors[] = {
		"255, 99, 132, 1",
		"75, 192, 192, 1",
		"153, 102, 255, 1",
		"255, 159, 64, 1",
		"54, 162, 235, 1",
		"127, 45, 132, 1",
		"34, 81, 192, 1",
		"76, 51, 255, 1",
		"127, 78, 64, 1",
		"27, 81, 235, 1"
	};

	private List<Dataset> datasets() {
		List<Dataset> datasetArray = new ArrayList<Dataset>();
		Iterator<String> sensorIt = sensorNames.keySet().iterator();
		int colorIndex = 0;
		while (sensorIt.hasNext()) {
			String sensorName = sensorIt.next();
			Set<LocalDateTime> keys = sensorData.keySet();
			Iterator<LocalDateTime> itr = keys.iterator();
			List<Datapoint> datapointArray = new ArrayList<Datapoint>();
			while (itr.hasNext()) {
				LocalDateTime timestamp = itr.next();
				ConcurrentSkipListMap<String, String> entries = sensorData.get(timestamp);	
				String val = entries.get(sensorName);
				if (val != null && !val.isEmpty()) {
					datapointArray.add(new Datapoint(timestamp.toString(), Integer.parseInt(val)));
				}
			}
			datasetArray.add(new Dataset(getDisplayNameForSensor(sensorName), 0, 
								"rgba(" + colors[colorIndex++] + ")", "rgba(0, 0, 0, 0.0)",
								datapointArray.toArray()));
			if (colorIndex >= colors.length) {
				colorIndex = 0;
			}
		}
		return datasetArray;
	}

	private record Animation(Integer duration) {}
	private record TimeRecord(String min, String max) {}
	private record TimeAxis(String type, TimeRecord time, Boolean display, String labelString) {}
	private record Axis(Boolean display, String labelString) {}
	private record Scales(TimeAxis[] xAxes, Axis[] yAxes) {}
	private record Options(Boolean responsive, Animation animation, Scales scales) {}
	private record Datasetx(Object[] datasets, String[] labels) {}
	public record FullJson(Datasetx datasets, Options options) {}

	private static Options options(LocalDateTime min, LocalDateTime max) {
		TimeRecord minMax = new TimeRecord(	Utils.googleSheetsDateFormat.format(min),
											Utils.googleSheetsDateFormat.format(max));
		TimeAxis[] xAxis = new TimeAxis[1];
		xAxis[0] = new TimeAxis("time", minMax, true, "");
		Axis[] yAxis = new Axis[1];
		yAxis[0] = new Axis(true, "");
		Scales scales = new Scales(xAxis, yAxis);
		Animation animation = new Animation(0);
		return new Options(false, animation, scales);
	}

	private Options options() {
		LocalDateTime limits[] = findTimeLimits();
		LocalDateTime max = LocalDateTime.now();
		if (max.isBefore(limits[1])) {
			max = limits[1];
		}
		return options(limits[0], max);
	}

	private static Options nulloptions() {
		LocalDateTime min = LocalDateTime.now();
		return options(min, min.plusHours(1));
	}

	public FullJson sensordata() {
		return new FullJson(new Datasetx(datasets().toArray(), new String[0]), options());
	}

	static public FullJson nullsensordata() {
		return new FullJson(new Datasetx((new Dataset[0]), new String[0]), nulloptions());
	}

	private String readCSV(String theDay) {
		try {
			String fileName = Utils.getLogFileName(accountMonitor.accountName, "sensordata-" + theDay + ".csv");
			if ((new File(fileName)).exists()) {
				try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
					String[] headers = br.readLine().split(",");
					String line = br.readLine();
					while (line != null) {
						String[] vals = line.split(",");
						LocalDateTime ldt = LocalDateTime.parse(vals[0]);
						for (int i = 1; i < vals.length; i++) {
							if (!vals[i].isEmpty()) {
								String[] deviceAndSensor = accountMonitor.runParams.getDeviceAndSensor(headers[i]);
								SensorDataPoint sensorDataPoint = new SensorDataPoint(ldt, deviceAndSensor[0], deviceAndSensor[1], vals[i]);
								addData(sensorDataPoint);
							}
						}
						line = br.readLine();
					}
				}
				writeHtmlForDay(theDay);
			}
			return Utils.getLogFileName(accountMonitor.accountName, "sensordata-" + theDay + ".html");
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}

    public String pastday(String theDay) {
		return readCSV(theDay);
    }

	public void run() {
		try {
			writeCSV();
			writeHtml();
		} catch (Exception e) {
			Utils.logToConsole("HtmlFileDataWriter.run(): " + e.getMessage());
			e.printStackTrace();
		}
	}
}