// Please credit chris.keith@gmail.com
package com.ckkeith.monitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.SystemUtils;
import org.w3c.dom.Document;

public class Utils {
	public static String getHomeDir() throws Exception {
		String d = System.getProperty("user.home");
		if (d == null || d.isEmpty()) {
			d = System.getProperty("HOMEDRIVE") + System.getProperty("HOMEPATH");
		}
		if (d == null || d.isEmpty()) {
			throw new Exception("Unable to determine home directory from environment variables.");
		}
		return d;
	}

	public static String getHomeURLPath(String accountPath) throws Exception {
		String d = getHomeDir() + File.separator + accountPath;
		if (d.charAt(1) == ':') {
			d = d.replaceAll("\\\\", "/");
		}
		return d;
	}

	public static final boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()
			.toString().indexOf("jdwp") >= 0;

	public static final SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

	public static String padWithSpaces(String s, int length) {
		StringBuffer ret = new StringBuffer(s);
		int nSpaces = length - s.length();
		while (nSpaces > 0) {
			ret.append(" ");
			nSpaces--;
		}
		return ret.toString();
	}

	private static void logRaw(String s, String logFileName) {
		try {
			FileWriter fstream = new FileWriter(logFileName, true);
			fstream.write(s + System.getProperty("line.separator"));
			fstream.flush();
			fstream.close();
		} catch (Exception e) {
			System.out.println(s + "\tError writing log file : " + logFileName + "\t" + e.toString());
			e.printStackTrace(new PrintStream(System.out));
		}
	}

	public static String nowInLogFormat() {
		return logDateFormat.format(new java.util.Date());
	}

	public static void logToConsole(String s) {
		String d = logDateFormat.format(new java.util.Date());
		System.out.println(d + "\t" + s + "\t");
	}

	public static void log(String s, String logFileName) {
		String d = logDateFormat.format(new java.util.Date());
		System.out.println(d + "\t" + s);
		logRaw(d + "\t" + s, logFileName);
	}

	final static public DateTimeFormatter googleSheetsDateFormat =
			DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
	public static String logWithGSheetsDate(LocalDateTime ldt, String s, String logFileName, String sep) {
		String d = logDateFormat.format(new java.util.Date());
		String d2 = googleSheetsDateFormat.format(ldt);
		String logString = d + sep + d2 + sep + s;
		logRaw(logString, logFileName);
		return logString;
	}

	public static String logWithGSheetsDate(LocalDateTime ldt, String s, String logFileName) {
		return logWithGSheetsDate(ldt, s, logFileName, "\t");
	}

	public static LocalDateTime getLocalDateTime(String str) {
		return LocalDateTime.parse(str, googleSheetsDateFormat);
	}

	static String getSafeName(String s) {
		String safeName = s.replaceAll("\\W+", "-");
		if (safeName.length() > 24) {
			safeName = safeName.substring(0, 11)
					+ "--"
					+ safeName.substring(safeName.length() - 12);
		}
		return safeName.toLowerCase();
	}

	private static void mkdir(String path) {
		File dir = new File(path);
		if (!dir.exists()) {
			try {
				dir.mkdir();
			} catch (Exception e) {
				System.out.println("Unable to create directory : " + path);
				throw e;
			}
		}
	}

	public static String getMasterLogFileDir() throws Exception {
		String d = Utils.getHomeDir();
		String machineName = getSafeName(getHostName());
		String path = d + File.separator + "Documents" + File.separator
				+ "tmp" + File.separator + machineName;
		mkdir(path);	// looks like mkdir will only create a directory at one level.
		return path;
	}

	public static String getLogFileDir(String accountName) throws Exception {
		String path = getMasterLogFileDir();
		String acctId = getSafeName(accountName);
		path += File.separator + acctId;
		mkdir(path);
		return path;
	}

	public static String getLogFileName(String accountName, String fn) throws Exception {
		return getLogFileDir(accountName) + File.separator + fn;
	}

	public static void sleepUntil(String msg, LocalDateTime then) throws Exception {
		Utils.logToConsole(msg + "\tAbout to sleep until\t" + then);
		Thread.sleep(ChronoUnit.MILLIS.between(LocalDateTime.now(), then));
		Utils.logToConsole(msg + "\tFinished sleeping, started at\t" + then);
	}

	public static ArrayList<String> readParameterFile(String parameterFilePath) throws Exception {
		File f = new File(parameterFilePath);
		if (!f.exists()) {
			System.out.println("No parameter file : " + parameterFilePath);
			System.exit(-7);
		}
		return readTextFileIntoArray(parameterFilePath);
	}

	public static ArrayList<String> readTextFileIntoArray(String parameterFilePath) throws Exception {
		ArrayList<String> creds = new ArrayList<String>(10);
		BufferedReader br = new BufferedReader(new FileReader(parameterFilePath));
		try {
			String s;
			while ((s = br.readLine()) != null) {
				creds.add(s);
			}
		} finally {
			br.close();
		}
		return creds;
	}

	public static Document readTextFileIntoDOM(String filePath) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new FileInputStream(new File(filePath)));
	}

	public static Document readStringIntoDOM(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
	}

	public static String getHostName() {
		String serverName = System.getenv("COMPUTERNAME");
		if (serverName == null || serverName.isEmpty()) {
			serverName = System.getenv("HOSTNAME");
		}
		if (serverName == null || serverName.isEmpty()) {
			try {
				serverName = InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				System.out.println("Error on InetAddress.getLocalHost().getHostName()\t" + e.toString());
				e.printStackTrace(new PrintStream(System.out));
			}
		}
		if (serverName == null || serverName.isEmpty()) {
			serverName = "unknown-host";
		}
		return serverName;
	}

	public static List<String> runCommandForOutput(List<String> params) {
	    ProcessBuilder pb = new ProcessBuilder(params);
	    Process p;
	    ArrayList<String> result = new ArrayList<String>();
	    try {
	        p = pb.start();
	        final BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        reader.lines().iterator().forEachRemaining(result::add);
	        p.waitFor();
	        p.destroy();
	    } catch (Exception e) {
	        e.printStackTrace();
	        result.add(e.getMessage());
	    }
	    return result;
	}

	public static LocalDateTime getBootTime() {
		if (SystemUtils.IS_OS_WINDOWS) {
			ArrayList<String> cmd = new ArrayList<String>();
			cmd.add("systeminfo");
			List<String> output = runCommandForOutput(cmd);
			for (String s : output) {
				if (s.contains("System Boot Time:")) {
					s = s.replace("System Boot Time:", "");
					s = s.trim();
					// 11/14/2018, 11:48:32 AM
					s = s.replace(", ", "T");
					try {
						SimpleDateFormat f = new SimpleDateFormat("MM'/'dd'/'yyyy'T'hh:mm:ss' 'a");
						Date d = f.parse(s);
						return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
					} catch(Exception e) {
						Utils.logToConsole("Unable to parse datetime: " + s);
						return null;
					}
				}
			}
		}
		return null;
	}

	private static void writeTableHeader(StringBuilder sb, String[] headers) {
		sb.append("<table border=\"1\">");
		sb.append("<tr>");
		for (int i = 0; i < headers.length; i++) {
			sb.append("<th style=\"text-align:left\">").append(headers[i]).append("</th>");
		}
		sb.append("</tr>");
	}

	public static void writeTable(StringBuilder sb, String[] headers, Integer[] columns, ArrayList<String> bodyLines) {
		writeTableHeader(sb, headers);
		for (String s : bodyLines) {
			sb.append("<tr>");
			String[] f = s.split("\t");
			for (int i = 0; i < columns.length; i++) {
				String str = "Bad data in line : '" + s + "'";
				try {
					str = f[columns[i]];
				} catch (Throwable t) {
					// show error, don't throw.
				}
				sb.append("<td style=\"text-align:left\">").append(str).append("</td>");
			}
			sb.append("</tr>");
		}
		sb.append("</table>");
	}

	public static void displayEnv() {
		ArrayList<String> envVars = new ArrayList<String>();
		for (String key : System.getenv().keySet()) {
			envVars.add(key + "=" + System.getenv(key));
		}
		Collections.sort(envVars);
		for (String e : envVars) {
			Utils.logToConsole(e);
		}
	}

	public static boolean runFromTerminal() {
		for (String key : System.getenv().keySet()) {
			if ("TERM".equals(key)) {
				return true;
			}
		}
		return false;
	}

    public static StringBuilder getDataSb(List<List<Object>> values) {
        StringBuilder sb = new StringBuilder();
        if (values == null) {
            sb.append("values == null");
        } else if (values.isEmpty()) {
            sb.append("values.isEmpty()");
        } else {
            for (List<Object> row : values) {
            	for (Object o : row) {
            		sb.append(o.toString()).append("\t");
            	}
                sb.append(System.getProperty("line.separator"));
            }
        }
        return sb;
    }
    
	public static void dumpToFile(List<List<Object>> values, String accountName) throws Exception {
        if (Utils.isDebug) {
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
		    String d2 = dateFormat.format(LocalDateTime.now());
            String tmpFileName = Utils.getLogFileName(accountName, "tmp" + d2);
            FileWriter fstream = new FileWriter(tmpFileName, true);
			fstream.write(getDataSb(values).toString());
            fstream.close();
            Utils.logToConsole("Dumped: " + tmpFileName);
        }
	}
	
	public static String getCurrentThreadString() {
		Thread currentThread = Thread.currentThread();
		return currentThread.getName() + "[" + currentThread.getId() + "]";
	}
}
