// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.util.ArrayList;
import java.util.List;

import nl.infcomtec.jparticle.Cloud;
import nl.infcomtec.jparticle.Device;

public class ParticleCloud {
	Cloud cloud;
	public Object devices;

	public ParticleCloud(String accessToken, boolean readMine, boolean readAll) {
		cloud = new Cloud(accessToken, readMine, readAll);
	}
	
	public List<ParticleDevice> getDevices() {
		ArrayList<ParticleDevice>	devices = new ArrayList<ParticleDevice>();
		for (Device d : cloud.devices.values()) {
			devices.add(new ParticleDevice(d));
		}
		return devices;
	}

	public void subscribe(ParticleDeviceEvent cb) {
		cloud.subscribe(cb);
	}
}
