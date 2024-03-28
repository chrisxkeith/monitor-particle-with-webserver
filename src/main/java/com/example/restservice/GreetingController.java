package com.example.restservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.ckkeith.monitor.HtmlFileDataWriter;

import jakarta.servlet.http.HttpServletResponse;

@RestController
public class GreetingController {

	public static HtmlFileDataWriter writer;

	@GetMapping("/sensordata")
	@ResponseBody
	public HtmlFileDataWriter.FullJson sensordata(HttpServletResponse response) {
		response.setHeader("Access-Control-Allow-Origin", "*");
		return writer.sensordata();
	}
	@GetMapping("/pastday")
	@ResponseBody
	public String pastday(@RequestParam("theDay") String theDay) {
		return writer.pastday(theDay);
	}
}
