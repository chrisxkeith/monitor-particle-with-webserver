// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.File;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class Main {
	private static ArrayList<AccountMonitor> monitors = new ArrayList<AccountMonitor>();

	@SuppressWarnings("unused")
	private static LocalDateTime getDailyShutdownTime() {
		// Shutdown a few minutes before midnight.
		return LocalDateTime.now().withHour(23).withMinute(57);
	}

	private static LocalDateTime getHourlyShutdownTime() {
		// Shutdown a few minutes before the hour interval after system restarted.
		LocalDateTime bootTime = Utils.getBootTime();
		int bootMinute;
		if (bootTime == null) {
			bootMinute = 0;
		} else {
			bootMinute = bootTime.getMinute();
		}
		int hourIncrement = 1;
		if (bootMinute > LocalDateTime.now().getMinute()) {
			hourIncrement--;
		}
		return LocalDateTime.now().plusHours(hourIncrement).withMinute(bootMinute - 3);
		// - 3 to increase the odds that this instance is gone
		// before Task Scheduler tries to start a new one.
	}

	private static LocalDateTime getShutdownTime() {
		// A new instance of this should be restarted by Task Scheduler.
		return getHourlyShutdownTime();
	}

	public static void main(String[] args) {
		Utils.logToConsole("main thread starting : " + Utils.getCurrentThreadString());
		try {
			if (!Utils.isDebug) {
				Utils.displayEnv();
			}
			String isRunningFrom;
			if (Utils.runFromTerminal() || Utils.isDebug) {
				isRunningFrom = "terminal / debug";
			} else {
				isRunningFrom = "Task Scheduler / automatic";
			}
			Utils.logToConsole("Running from " + isRunningFrom);
			String filePath = Utils.getHomeDir() + File.separator + "Documents" + File.separator + "particle-tokens.txt";
			ArrayList<String> accountTokens = Utils.readParameterFile(filePath);
			for (String c : accountTokens) {
				if (c != null && !c.startsWith("#")) {
					AccountMonitor m = new AccountMonitor(c);
					m.start();
					monitors.add(m);
				}
			}
			if (!(Utils.runFromTerminal() || Utils.isDebug)) {
				Utils.sleepUntil("MonitorParticle main - waiting to System.exit(0).", getShutdownTime());
				System.exit(0);
			}
			Utils.logToConsole("Running from " + isRunningFrom);
		} catch (Exception e) {
			Utils.logToConsole("main() :\t" + e.getClass().getName() + "\t" + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		}
		Utils.logToConsole("main thread ending : " + Utils.getCurrentThreadString());
	}
}
