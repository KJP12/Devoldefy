package devoldefy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class FileHelper {
	static Path download(String url, Path directory) throws IOException {
		Path file = directory.resolve(Devoldefy.hash(url));

		if (Files.notExists(file)) {
			try (InputStream in = new URL(url).openStream()) {
				Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
			}
		}

		return file;
	}

	/**
	 * Loads properties from user.dir
	 * */
	static Properties loadProperties() throws IOException {
		Properties properties = new Properties();
		Path path = Devoldefy.workDirctory.resolve("config.properties");
		if(Files.notExists(path)) {
			Files.createFile(path);
			properties.put("mappings.mcp.csv", "http://export.mcpbot.bspk.rs/mcp_${mcp.type}_nodoc/${mcp.build}-${mcp.version}/mcp_${mcp.type}_nodoc-${mcp.build}-${mcp.version}.zip");
			properties.put("mappings.mcp.srg", "https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/${source.version.type}/${mcp.config}/joined.tsrg");
			properties.put("mappings.yarn", "http://maven.modmuss50.me/net/fabricmc/yarn/${target.version}+build.${yarn.build}/yarn-${target.version}+build.${yarn.build}.jar");
			properties.put("forge.jar", "{home_dir}/.gradle/caches/minecraft/net/minecraftforge/forge/${source.version}-${forge.version}/${mcp.type}/${mcp.build}/forgeSrc-${source.version}-${forge.version}.jar");

			properties.put("forge.version", "See https://files.minecraftforge.net or your build file");
			properties.put("source.java.version", "1.8");
			properties.put("source.root", "./src/main/java");
			properties.put("source.version", "1.12");
			properties.put("source.version.type", "release");
			properties.put("mcp.config", "1.12.2");
			properties.put("mcp.version", "1.12");
			properties.put("mcp.type", "stable");
			properties.put("mcp.build", "39");

			properties.put("target.root", "./remappedSrc");
			properties.put("target.version", "1.14.4");
			properties.put("yarn.build", "8");

			try(OutputStream stream = Files.newOutputStream(path)) {
				properties.store(stream, "Please modify the variables to what you need.");
			}
			return null;
		} else {
			try(InputStream stream = Files.newInputStream(path)) {
				properties.load(stream);
			}
			return properties;
		}
	}
}
