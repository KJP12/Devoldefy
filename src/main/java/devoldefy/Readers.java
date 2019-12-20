package devoldefy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class Readers {
	static Mappings tsrg(Scanner s, Map<String, String> fieldNames, Map<String, String> methodNames) {
		Map<String, String> classes = new LinkedHashMap<>();
		Map<String, String> fields = new LinkedHashMap<>();
		Map<String, String> methods = new LinkedHashMap<>();

		String currentClassA = null;
		String currentClassB = null;
		while (s.hasNextLine()) {
			String line = s.nextLine();

			if (!line.startsWith("\t")) {
				String[] parts = line.split(" ");
				classes.put(parts[0], parts[1]);
				currentClassA = parts[0];
				currentClassB = parts[1];
				continue;
			}

			line = line.substring(1);

			String[] parts = line.split(" ");

			if (parts.length == 2) {
				fields.put(currentClassA + ":" + parts[0], currentClassB + ":" + fieldNames.getOrDefault(parts[1], parts[1]));
			} else if (parts.length == 3) {
				methods.put(currentClassA + ":" + parts[0] + parts[1], currentClassB + ":" + methodNames.getOrDefault(parts[2], parts[2]) + parts[1]);
			}
		}

		Mappings mappings = new Mappings();
		mappings.classes.putAll(classes);
		mappings.fields.putAll(fields);
		methods.forEach((a, b) -> mappings.methods.put(a, Devoldefy.remapMethodDescriptor(b, classes)));

		s.close();
		return mappings;
	}

	static Map<String, String> csv(Scanner s) {
		Map<String, String> mappings = new LinkedHashMap<>();

		try (Scanner r = s) {
			r.nextLine();
			while (r.hasNextLine()) {
				String[] parts = r.nextLine().split(",");
				mappings.put(parts[0], parts[1]);
			}
		}

		s.close();
		return mappings;
	}

	static Mappings tiny(Scanner s, String from, String to) {
		String[] header = s.nextLine().split("\t");
		Map<String, Integer> columns = new HashMap<>();

		for (int i = 1; i < header.length; i++) {
			columns.put(header[i], i - 1);
		}

		int fromColumn = columns.get(from);
		int toColumn = columns.get(to);

		Map<String, String> classes = new LinkedHashMap<>();
		Map<String, String> fields = new LinkedHashMap<>();
		Map<String, String> methods = new LinkedHashMap<>();

		while (s.hasNextLine()) {
			String[] line = s.nextLine().split("\t");
			switch (line[0]) {
				case "CLASS": {
					classes.put(line[fromColumn + 1], line[toColumn + 1]);
					break;
				}

				case "FIELD": {
					fields.put(line[1] + ":" + line[fromColumn + 3], classes.get(line[1]) + ":" + line[toColumn + 3]);
					break;
				}

				case "METHOD": {
					methods.put(line[1] + ":" + line[fromColumn + 3] + line[2], classes.get(line[1]) + ":" + line[toColumn + 3] + line[2]);
					break;
				}
			}
		}

		Mappings mappings = new Mappings();
		mappings.classes.putAll(classes);
		mappings.fields.putAll(fields);
		methods.forEach((a, b) -> mappings.methods.put(a, Devoldefy.remapMethodDescriptor(b, classes)));

		s.close();
		return mappings;
	}
}
