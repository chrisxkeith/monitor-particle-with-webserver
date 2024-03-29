// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.PrintStream;

public class DeviceMonitor extends Thread {

	private String accessToken;
	ParticleDevice device;
	private ParticleCloud cloud;
	private AccountMonitor accountMonitor;

	public DeviceMonitor(AccountMonitor accountMonitor, ParticleDevice device, ParticleCloud cloud) throws Exception {
		this.device = device;
		this.cloud = cloud;
		this.accountMonitor = accountMonitor;
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
		ParticleDeviceEvent cb = new ParticleDeviceEvent(accountMonitor, device);
		cloud.subscribe(cb);
		accountMonitor.addEventSubscriber(device.getName(), cb);
		log("Subscribed to: " + device.getName());
	}

	public void run() {
		if (Utils.isDebug) {
			log("DeviceMonitor thread started : " + Utils.getCurrentThreadString());
		}
		try {
			if (ableToConnect()) {
				subscribe();
			}
		} catch (Exception e) {
			Utils.logToConsole("run() :\t" + e.getClass().getName() + "\t" + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		}
		if (Utils.isDebug) {
			log("DeviceMonitor thread exiting : " + Utils.getCurrentThreadString());
		}
	}

}
