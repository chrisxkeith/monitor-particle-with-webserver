// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.time.LocalDateTime;

public class SensorDataPoint {
	LocalDateTime	timestamp;
	String			deviceName;
	String			sensorName;
	String			sensorData;

	public SensorDataPoint(LocalDateTime timestamp, String deviceName, String sensorName, String sensorData) {
		super();
		this.timestamp = timestamp;
		this.deviceName = deviceName;
		this.sensorName = sensorName;
		this.sensorData = sensorData;
	}
}