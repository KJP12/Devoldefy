package devoldefy;

import java.util.LinkedHashMap;
import java.util.Map;

class Mappings {
	public final Map<String, String> classes = new LinkedHashMap<>();
	public final Map<String, String> fields = new LinkedHashMap<>();
	public final Map<String, String> methods = new LinkedHashMap<>();

	public Mappings chain(Mappings mappings) {
		Mappings result = new Mappings();

		classes.forEach((a, b) -> result.classes.put(a, mappings.classes.getOrDefault(b, b)));
		fields.forEach((a, b) -> result.fields.put(a, mappings.fields.getOrDefault(b, b)));
		methods.forEach((a, b) -> result.methods.put(a, mappings.methods.getOrDefault(b, b)));

		return result;
	}

	public Mappings invert() {
		Mappings result = new Mappings();

		classes.forEach((a, b) -> result.classes.put(b, a));
		fields.forEach((a, b) -> result.fields.put(b, a));
		methods.forEach((a, b) -> result.methods.put(b, a));

		return result;
	}
}
