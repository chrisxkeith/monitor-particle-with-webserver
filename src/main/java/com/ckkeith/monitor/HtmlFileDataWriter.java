// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

public class HtmlFileDataWriter extends Thread {

	private AccountMonitor accountMonitor;
	
	private ConcurrentSkipListMap<LocalDateTime, ConcurrentSkipListMap<String, String>> sensorData = new ConcurrentSkipListMap<LocalDateTime, ConcurrentSkipListMap<String, String>>();
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

	private void writeln(FileWriter fw, String s) throws Exception {
		fw.write(s + System.getProperty("line.separator"));
	}

	private int colorIndex = 0;

	private String getNextColor() {
		final String colors[] = {
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
		String nextColor = colors[colorIndex++];
		if (colorIndex >= colors.length) {
			colorIndex = 0;
		}
		return nextColor;
	}

	private int addDataForSensor(FileWriter jsonStream, String sensorName) throws Exception {
		writeln(jsonStream, "\t\t\t\t\"data\" : [");
		Set<LocalDateTime> keys = sensorData.keySet();
		Iterator<LocalDateTime> itr = keys.iterator();
		boolean first = true;
		int nDataPoints = 0;
		while (itr.hasNext()) {
			LocalDateTime timestamp = itr.next();
			ConcurrentSkipListMap<String, String> entries = sensorData.get(timestamp);

			String val = entries.get(sensorName);
			if (val != null && !val.isEmpty()) {
				StringBuilder sb2 = new StringBuilder();
				if (!first) {
					sb2.append(",");
				} else {
					first = false;
				}
				sb2.append("{ \"t\" : \"").append(timestamp).append("\", \"y\" : " + val + "}");
				writeln(jsonStream, sb2.toString());
				nDataPoints++;
			}
		}
		writeln(jsonStream, "\t\t\t\t]");
		return nDataPoints;
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

	private Integer writeJson(FileWriter jsonStream, String deviceName) throws Exception {
		Integer nDataPoints = 0;
		try {
			colorIndex = 0;
			writeln(jsonStream, "\t{ \"datasets\" : ");
			writeln(jsonStream, "\t\t[");

			Iterator<String> sensorIt = sensorNames.keySet().iterator();
			Boolean firstSensor = true;
			while (sensorIt.hasNext()) {
				String sensorName = sensorIt.next();
				if (((deviceName == null) || sensorName.startsWith(deviceName))) {
					StringBuilder sb1 = new StringBuilder("\t\t\t");
					if (firstSensor) {
						firstSensor = false;
					} else {
						sb1.append(" , ");
					}
					sb1.append("{ \"label\" : \"");
					sb1.append(getDisplayNameForSensor(sensorName)).append("\",");
					writeln(jsonStream, sb1.toString());
					writeln(jsonStream, "\"lineTension\" : 0,");
					writeln(jsonStream, "\t\t\t\"borderColor\" : \"rgba(" + getNextColor() + ")\",");
					writeln(jsonStream, "\t\t\t\"backgroundColor\" : \"rgba(0, 0, 0, 0.0)\",");
					nDataPoints += addDataForSensor(jsonStream, sensorName);
					writeln(jsonStream, "\t\t\t}");
				}
			}
		} finally {
			writeln(jsonStream, "\t\t]");
			writeln(jsonStream, "\t}");
		}
		return nDataPoints;
	}

	private File findFile(String fn) {
		File f = new File(fn);
		if (f.exists()) {
			return f;
		}
		f = new File("monitor-particle-using-api" + File.pathSeparator + fn);
		if (f.exists()) {
			return f;
		}
		return null;
	}

	private void appendFromFileToFile(FileWriter htmlStream, String fromFile, String fileName) throws Exception {
		File f = findFile(fromFile);
		if (f == null) {
			return;
		}
		try (BufferedReader br = new BufferedReader(new FileReader(f))) {
			String line;
			LocalDateTime limits[] = findTimeLimits();
			while ((line = br.readLine()) != null) {
				if (line.contains("_fileNameGoesHere_.html")) {
					String accountPath = Utils.getHomeURLPath(
							"Documents" + File.separator + "tmp" + File.separator + Utils.getHostName() + File.separator
									+ Utils.getSafeName(accountMonitor.accountName));
					line = line.replace("_fileNameGoesHere_", fileName)
							.replace("_homePathGoesHere_", accountPath)
							.replace("_writeIntervalGoesHere_",
									Integer.valueOf(accountMonitor.runParams.htmlWriteIntervalInSeconds).toString());
				} else if (line.contains("_suggestedTimeMin_")) {
					line = line.replace("_suggestedTimeMin_", Utils.googleSheetsDateFormat.format(limits[0]));
				} else if (line.contains("_suggestedTimeMax_")) {
					LocalDateTime max = LocalDateTime.now();
					if (max.isBefore(limits[1])) {
						max = limits[1];
					}
					line = line.replace("_suggestedTimeMax_", Utils.googleSheetsDateFormat.format(max));
				}
				writeln(htmlStream, line);
			}
		}
	}

	int currentFileNumber = 0;

	int getNextFileNumber(int thisFileNumber) {
		if (thisFileNumber == 0) {
			return 1;
		}
		return 0;
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

	private String getCSVFileName() throws Exception {
		String today = (new SimpleDateFormat("yyyy-MM-dd")).format(new java.util.Date());
		return Utils.getLogFileName(accountMonitor.accountName,
							"sensordata-" + today + ".csv");
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

	int doWriteOneHtml(String safeFn, File tempFile, String deviceName, int thisFileNumber, String thisFileName)
				throws Exception {
		int nDataPoints = 0;
		FileWriter htmlStream = new FileWriter(tempFile.getCanonicalPath(), false);
		try {
			appendFromFileToFile(htmlStream, "src/main/resources/prefix.html",
					safeFn + String.format("%03d", getNextFileNumber(thisFileNumber)));
			nDataPoints = writeJson(htmlStream, deviceName);
			appendFromFileToFile(htmlStream, "src/main/resources/suffix.html", "junk");
		} finally {
			htmlStream.close();
		}
		File thisFile = new File(thisFileName);
		try {
			thisFile.delete();
		} catch (Exception ex) {
			Utils.logToConsole(
					"thisFile.delete() failed : " + ex.getMessage() + " " + ex.getClass().getName() + ", continuing");
		}
		try {
			Files.move(tempFile.toPath(), thisFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ex) {
			Utils.logToConsole(
					"Files.move() failed : " + ex.getMessage() + " " + ex.getClass().getName() + ", continuing");
		}
		return nDataPoints;
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

	void writeOneHtml(String deviceName, int thisFileNumber) {
		String thisFileName = "not yet specified";
		int nDataPoints = 0;
		try {
			deleteOldData();
			String safeFn;
			safeFn = getHTMLFileName(deviceName);
			String fileName = Utils.getLogFileName(accountMonitor.accountName, safeFn + "NNN.html");
			thisFileName = fileName.replace("NNN", String.format("%03d", thisFileNumber));
			String dir = new File(fileName).getParent();
			File tempFile = File.createTempFile("tmp", ".html", new File(dir));
			try {
				nDataPoints = doWriteOneHtml(safeFn, tempFile, deviceName, thisFileNumber, thisFileName);
			} finally {
				tempFile.delete();
			}
		} catch (Exception e) {
			Utils.logToConsole("FAILED to write : " + thisFileName + " : # data points : "
					+Integer.valueOf(nDataPoints).toString() + " : " + e.getMessage());
			e.printStackTrace();
			// If there's any failure, continue and write the next file at the appropriate time.
		}
	}

	void writeHtml() throws Exception {
		synchronized (this) {
			int thisFileNumber = currentFileNumber;
			writeOneHtml(null, thisFileNumber);
			currentFileNumber = getNextFileNumber(currentFileNumber);
		}
	}
	
	void fillEmpty() {
		ConcurrentSkipListMap<String, String> prevEntries = new ConcurrentSkipListMap<String, String>();
		Iterator<LocalDateTime> sensorDataIt = sensorData.keySet().iterator();
		while (sensorDataIt.hasNext()) {
			LocalDateTime timestamp = sensorDataIt.next();
			ConcurrentSkipListMap<String, String> entries = sensorData.get(timestamp);
			Iterator<String> sensorNameIt = sensorNames.keySet().iterator();
			while (sensorNameIt.hasNext()) {
				String sensorName = sensorNameIt.next();
				if (!entries.containsKey(sensorName)) {
					if (prevEntries.containsKey(sensorName)) {
						entries.put(sensorName, prevEntries.get(sensorName));
					}
				}
				String val = entries.get(sensorName);
				if (val != null) {
					prevEntries.put(sensorName, val);
				}
			}
		}
	}

	public void run() {
		try {
			Utils.logToConsole("CSV : " + getCSVFileName());
			Utils.logToConsole("HTML: " + Utils.findResourceFile("sensorgraph.html").getAbsolutePath());
			Utils.logToConsole("JSON: http://localhost:8080/sensordata");
			while (true) {
				fillEmpty();
				writeHtml();
				this.writeCSV();
				Thread.sleep(accountMonitor.runParams.htmlWriteIntervalInSeconds * 1000);
			}
		} catch (Exception e) {
			Utils.logToConsole(e.getMessage());
			e.printStackTrace();
		}
	}

	private record Datapoint(String t, Integer y) {}
	private record Dataset(String label, Integer lineTension, String borderColor, String backgroundColor, Object[] data) {}
	
	private List<Dataset> datasets() {
		List<Dataset> datasetArray = new ArrayList<Dataset>();
		Iterator<String> sensorIt = sensorNames.keySet().iterator();
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
								"rgba(" + getNextColor() + ")", "rgba(0, 0, 0, 0.0)",
								datapointArray.toArray()));
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

	public FullJson sensordata() {
		List<Dataset> datasetArray = datasets();
		LocalDateTime limits[] = findTimeLimits();
		LocalDateTime max = LocalDateTime.now();
		if (max.isBefore(limits[1])) {
			max = limits[1];
		}
		TimeRecord minMax = new TimeRecord(	Utils.googleSheetsDateFormat.format(limits[0]),
											Utils.googleSheetsDateFormat.format(max));
		TimeAxis[] xAxis = new TimeAxis[1];
		xAxis[0] = new TimeAxis("time", minMax, true, "");
		Axis[] yAxis = new Axis[1];
		yAxis[0] = new Axis(true, "");
		Scales scales = new Scales(xAxis, yAxis);
		Animation animation = new Animation(0);
		Options options = new Options(false, animation, scales);
		return new FullJson(new Datasetx(datasetArray.toArray(), new String[0]), options);
	}
}