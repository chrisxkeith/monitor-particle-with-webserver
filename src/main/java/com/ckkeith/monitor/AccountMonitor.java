// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.restservice.GreetingController;

public class AccountMonitor extends Thread {

	String accessToken = null;
	String accountName = null;
	private String logFileName;
	private Map<String, ParticleDeviceEvent> eventSubscribers = new HashMap<String, ParticleDeviceEvent>();
	private HtmlFileDataWriter htmlFileDataWriter;
	RunParams runParams;
	Map<String, DeviceMonitor> deviceMonitors = new HashMap<String, DeviceMonitor>();
	Set<String> deviceNames = new HashSet<String>();

	public AccountMonitor(String credentials) throws Exception {
		String[] creds = credentials.split("\t");
		if (creds.length > 0) {
			this.accessToken = creds[0];
		} else {
			throw new Exception("No particle token specified. particle-tokens.txt file should be: [particle_token][tab][particle_account_name]");
		}
		if (creds.length > 1) {
			this.accountName = creds[1];
		} else {
			Utils.logToConsole(this.accessToken);
			throw new Exception("No account name specified. particle-tokens.txt file should be: [particle_token][tab][particle_account_name]");
		}
		logFileName = Utils.getLogFileName(accountName, "devices-overview.txt");
		this.runParams = RunParams.loadFromJson();
		Set<String> devicesSeen = new HashSet<String>();
		for (RunParams.JsonDataset ds : runParams.jsonDatasets) {
			if (!devicesSeen.contains(ds.microcontrollerName())) {
				devicesSeen.add(ds.microcontrollerName());
				deviceNames.add(ds.microcontrollerName());
			}
		}
		if (deviceNames.size() == 0) {
			Utils.logToConsole("No deviceNames for " + accountName);
			System.exit(-1);
		}
	}

	void startHtmlWriter() {
		if (htmlFileDataWriter == null && runParams.htmlWriteIntervalInSeconds > 0) {
			htmlFileDataWriter = new HtmlFileDataWriter(this);
			htmlFileDataWriter.start();
			GreetingController.writer = htmlFileDataWriter;
		}
	}
	void startDeviceMonitors() {
		startHtmlWriter();
		ParticleCloud c = new ParticleCloud("Bearer " + accessToken, true, false);
		ArrayList<DeviceMonitor> newDevices = new ArrayList<DeviceMonitor>();
		for (ParticleDevice device : c.getDevices()) {
			try {
				if (deviceNames.contains(device.getName()) && deviceMonitors.get(device.getName()) == null) {
					if (!device.getConnected()) {
						if (!Utils.isDebug) {
							Utils.logToConsole("Skipping disconnected device : " + device.getName());
						}
					} else {
						// Get device variables and functions
						device = device.getDevice("Bearer " + accessToken);
						DeviceMonitor dm = new DeviceMonitor(this, device, c);
						Utils.logWithGSheetsDate(LocalDateTime.now(), dm.toTabbedString(), logFileName);
						deviceMonitors.put(device.getName(), dm);
						newDevices.add(dm);
						// Server returned HTTP response code: 502 for URL: https://api.particle.io/v1/devices/4b0050001151373331333230
						if (Utils.isDebug) {
							Thread.sleep(3 * 1000);
						} else {
							LocalDateTime then = LocalDateTime.now().plusSeconds(3);
							Utils.sleepUntil(
								"AccountMonitor.startDeviceMonitors() sleeping to try to avoid \"Too many requests\" (http 502) error for: "
										+ device.getName(),
								then);
						}
					}
				}
			} catch (Exception e) {
				String err = "run() :\t" + device.getName() + "\t" + e.getClass().getName() + "\t" + e.getMessage();
				Utils.logToConsole(err);
				e.printStackTrace(new PrintStream(System.out));
			}
		}
		if (newDevices.size() == 0) {
			Utils.logToConsole("Didn't find any devices for : " + accountName);
			System.exit(-6);
		}
		for (DeviceMonitor dm : newDevices) {
			dm.start();
		}
	}

	public void run() {
		Utils.logToConsole(Utils.padWithSpaces(this.accountName, 20) +
				"\tAccountMonitor thread starting : " + Utils.getCurrentThreadString());
		Utils.logWithGSheetsDate(LocalDateTime.now(), "AccountMonitor thread starting.", logFileName, ",");
		startDeviceMonitors();
		Utils.logToConsole(Utils.padWithSpaces(this.accountName, 20) +
				"\tAccountMonitor thread exiting : " + Utils.getCurrentThreadString());
	}

	public void addEventSubscriber(String name, ParticleDeviceEvent cb) {
		synchronized(eventSubscribers) {
			eventSubscribers.put(name, cb);
		}
	}

	public void addDataPoint(LocalDateTime ldt, String deviceName, String event, String data) {
		if (runParams.containsSensor(deviceName, event)) {
			if ((this.htmlFileDataWriter != null)) {
				try {
					Float.parseFloat(data);
					this.htmlFileDataWriter.addData(new SensorDataPoint(ldt, deviceName, event, data));
				} catch(Exception e) {
					Utils.logToConsole("Skipping data: " + deviceName + ", " + event + ", " + data);
				}
			}
		} else if (Utils.isDebug) {
		 	Utils.logToConsole("Skipping event: " + deviceName + ", " + event + ", " + data);
		}
	}

	public boolean handleServerEvent(String event) throws Exception {
		return true;
	}
}
