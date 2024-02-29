// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.util.Date;

import nl.infcomtec.jparticle.Device;

public class ParticleDevice {
	Device device;
	String accessToken;

	public ParticleDevice(Device d) {
		this.device = d;
	}

	public ParticleDevice getDevice(String accessToken) throws Exception {
		this.accessToken = accessToken;
		return new ParticleDevice(Device.getDevice(device.id, accessToken));
	}

	public String getName() {
		return device.name;
	}

	public boolean getConnected() {
		return device.connected;
	}

	public String getId() {
		return device.id;
	}

	public Date getLastHeard() {
		return device.lastHeard;
	}
	
	public String getVariable(String variableName) {
		if (device.variables == null) {
			return "unknown (no variables)";
		}
		if (device.variables.has(variableName)) {
			String ret = device.readString(variableName, "Bearer " + accessToken);
			if (ret == null || ret.isEmpty()) {
				return "unknown (null or empty value)";
			}
			return ret;
		}
		return "unknown (no " + variableName + ")";
	}
}
