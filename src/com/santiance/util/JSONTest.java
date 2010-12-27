package com.santiance.util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class JSONTest {
	final static String OK   = " [  OK  ] ";
	final static String FAIL = " [ FAIL ] ";
	
	public static void main(String[] args) {
		String dirPath;
		File dir;
		Map<String, JSON> values = new HashMap<String, JSON>();
		
		if (args.length > 0) {
			dirPath = args[0];
		} else {
			dirPath = "tests";
		}
		
		dir = new File(dirPath);
		
		for (File file : dir.listFiles()) {
			String result;
			JSON value;
			
			try {
				value = testFile(file);
				result = (value != null) ? OK : FAIL;
				values.put(file.getName(), value);
			} catch (Exception e) {
				e.printStackTrace();
				result = FAIL;
			}
			
			System.out.print(result);
			System.out.println(file.getName());
		}
		
		JSON obj = values.get("BasicObject.json");
		
		if (!obj.isMap()) {
			System.out.print(FAIL);
			System.out.println("Basic object wasn't an object!");
		} else {
			JSON.MapValue map = (JSON.MapValue)obj;
			
			if (!map.get("name").equals("Kristopher Ives")) {
				
			}
		}
		
		if (!values.get("HelloWorld.json").equals("Hello World")) {
			System.out.print(FAIL);
			System.out.println("Hello world string didn't match: " + values.get("HelloWorld.json"));
		}
	}
	
	public static JSON testFile(File file) throws Exception {
		JSON value = JSON.parse(file);
		
		return value;
	}
}
