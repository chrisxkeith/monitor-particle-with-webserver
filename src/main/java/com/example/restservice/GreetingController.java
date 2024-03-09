package com.example.restservice;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.ckkeith.monitor.HtmlFileDataWriter;

import jakarta.servlet.http.HttpServletResponse;

@RestController
public class GreetingController {

	private static final String template = "Hello, %s!";
	private final AtomicLong counter = new AtomicLong();
	public static HtmlFileDataWriter writer;

	@GetMapping("/greeting")
	public Greeting greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
		return new Greeting(counter.incrementAndGet(), String.format(template, name));
	}
	@GetMapping("/sensordata")
	@ResponseBody
	public HtmlFileDataWriter.Datasets sensordata(HttpServletResponse response) {
		response.setHeader("Access-Control-Allow-Origin", "*");
		return writer.sensordata();
	}
}
