// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DeviceMonitor extends Thread {

	private String accessToken;
	ParticleDevice device;
	private ParticleCloud cloud;
	private String logFileName;
	private AccountMonitor accountMonitor;

	public DeviceMonitor(AccountMonitor accountMonitor, ParticleDevice device, ParticleCloud cloud) throws Exception {
		this.device = device;
		this.cloud = cloud;
		this.accountMonitor = accountMonitor;
		logFileName = Utils.getLogFileName(accountMonitor.accountName, device.getName() + "_particle_log.txt");
	}

	public String toTabbedString() {
		StringBuffer sb = new StringBuffer();
		sb.append(Utils.padWithSpaces(accountMonitor.accountName, 20)).append("\t");
		sb.append(Utils.padWithSpaces(device.getName(), 20)).append("\t");
		sb.append(device.getId()).append("\t");
		sb.append(device.getConnected()).append("\t");
		sb.append(device.getLastHeard()).append("\t");
		sb.append(getVersionString(device)).append("\t");
		sb.append(Utils.getHostName());
		return sb.toString();
	}

	private String getVersionString(ParticleDevice d) {
		return d.getVariable("GitHubHash");
	}

	private void log(String s) {
		String msg = Utils.padWithSpaces(device.getName(), 20) + "\t" + s + "\t" + Utils.getHostName();
		Utils.logWithGSheetsDate(LocalDateTime.now(), msg, logFileName);
		Utils.logToConsole(msg);
	}

	private boolean ableToConnect() throws Exception {
		int retries = 24;
		while (!device.getConnected() && retries > 0) {
			log("not connected. Will retry in an hour.");
			sleep(60 * 60 * 1000);
			device = device.getDevice("Bearer " + accessToken);
			retries--;
		}
		if (!device.getConnected()) {
			log("not connected after 24 hours.");
		}
		return device.getConnected();
	}

	private void subscribe() throws Exception {
		for (Map.Entry<String, RunParams.SheetConfig> entry :
					this.accountMonitor.runParams.sheets.entrySet()) {
			Iterator<RunParams.Dataset> datasetIt = entry.getValue().dataSets.iterator();
			while (datasetIt.hasNext()) {
				RunParams.Dataset d = datasetIt.next();
				for (Map.Entry<String, HashMap<String, String>> mc : d.microcontrollers.entrySet()) {
					if (mc.getKey().equals(device.getName())) {
						ParticleDeviceEvent cb = new ParticleDeviceEvent(accountMonitor, device);
						cloud.subscribe(cb);
						accountMonitor.addEventSubscriber(device.getName(), cb);
						log("Subscribed to: " + device.getName());
					}
				}
			}
		}
	}

	public void run() {
		log("DeviceMonitor thread started : " + Utils.getCurrentThreadString());
		try {
			if (ableToConnect()) {
				subscribe();
			}
		} catch (Exception e) {
			Utils.logToConsole("run() :\t" + e.getClass().getName() + "\t" + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		}
		log("DeviceMonitor thread exiting : " + Utils.getCurrentThreadString());
	}

}
