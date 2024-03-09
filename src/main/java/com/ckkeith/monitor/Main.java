// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;

public class Main {
	private static ArrayList<AccountMonitor> monitors = 
					new ArrayList<AccountMonitor>();

	public static void run() {
		if (Utils.isDebug) {
			Utils.logToConsole("main run starting : " + Utils.getCurrentThreadString());
		}
		try {
			Utils.displayEnv();
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
		} catch (Exception e) {
			Utils.logToConsole("main() :\t" + e.getClass().getName() + "\t" + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		}
		if (Utils.isDebug) {
			Utils.logToConsole("main run ending : " + Utils.getCurrentThreadString());
		}
	}
}
