package devoldefy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileHelper {
	static File download(String url, File directory) throws IOException {
		directory.mkdirs();
		File file = new File(directory, Devoldefy.hash(url));

		if (!file.exists()) {
			try (InputStream in = new URL(url).openStream()) {
				Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}

		return file;
	}

	static File extract(File zip, String path, File directory) throws IOException {
		directory.mkdirs();
		File file = new File(directory, Devoldefy.hash(zip.getName() + path));

		try (ZipFile zipFile = new ZipFile(zip)) {
			InputStream is = null;

			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry zipEntry = entries.nextElement();
				if (zipEntry.getName().equals(path)) {
					is = zipFile.getInputStream(zipEntry);
					break;
				}
			}

			if (is == null) {
				return null;
			}

			Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		return file;
	}
}
