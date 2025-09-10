// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.json.JSONObject;

import nl.infcomtec.jparticle.AnyDeviceEvent;
import nl.infcomtec.jparticle.Event;

public class ParticleDeviceEvent extends AnyDeviceEvent {

	protected ParticleDevice device;
	protected AccountMonitor accountMonitor;
	private ParticleEvent mostRecentEvent;

	public ParticleDeviceEvent(AccountMonitor accountMonitor, ParticleDevice device2) throws Exception {
		this.device = device2;
		this.accountMonitor = accountMonitor;
	}
	
	private void handleServerEvent(ParticleEvent e) {
		try {
			if (!accountMonitor.handleServerEvent(e.getData())) {
				String subject = "Unknown server event : " + e.getData();
				String body = "photon coreId: " + e.getCoreId() + ", publishedAt: "
						+ Utils.logDateFormat.format(e.getPublishedAt()) + ", server: " + Utils.getHostName()
						+ ", booted: " + Utils.googleSheetsDateFormat.format(Utils.getBootTime());
				Utils.logToConsole(subject + body);
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	public void handleEvent(ParticleEvent e) {
		if (e.getName().equals("server")) {
			handleServerEvent(e);
		} else {
			LocalDateTime ldt = LocalDateTime.ofInstant(e.getPublishedAt().toInstant(), ZoneId.systemDefault());
			String s = Utils.logWithGSheetsDate(ldt, e.toTabbedString(device));
			if (Utils.isDebug) {
				Utils.logToConsole(s);
			}
			mostRecentEvent = e;
			if (e.getData().charAt(0) == '{') {
				JSONObject deviceJson = new JSONObject(e.getData());
				String[] names = JSONObject.getNames(deviceJson);
				for (String name : names) {
					accountMonitor.addDataPoint(ldt, device.getName(), name,
												deviceJson.get(name).toString());
				}
			} else {
				if (Utils.isDebug) {
					Utils.logToConsole("Skipping non-JSON event: " + device.getName() + ", " + e.getName() + ", " + e.getData());
				}
			}
		}
	}

	@Override
	public void event(Event e) {
		ParticleEvent event = new ParticleEvent(e);
		try {
			handleEvent(event);
		} catch (Exception ex) {
			Utils.logToConsole("event(Event e)\t" + ex.getClass().getName() + "\t" + ex.getMessage());
			ex.printStackTrace(new PrintStream(System.out));
		}
	}
	
    @Override
    public String forDeviceId() {
        return device.getId();
    }

    @Override
    public String forDeviceName() {
        return device.getName();
    }

	public String getMostRecentEvent() {
		if (mostRecentEvent == null) {
			return "No mostRecentEvent\tn/a";
		}
		return mostRecentEvent.toTabbedString(device);
	}
	public String getMostRecentEventDateTime() {
		if (mostRecentEvent == null) {
			return "No mostRecentEventDateTime";
		}
		LocalDateTime ldt = LocalDateTime.ofInstant(mostRecentEvent.getPublishedAt().toInstant(), ZoneId.systemDefault());
		return Utils.googleSheetsDateFormat.format(ldt);
	}
}
