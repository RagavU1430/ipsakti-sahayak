package com.ipsakti.ip_sakti_backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IpSaktiBackendApplication {

	public static void main(String[] args) {
		loadDotenv();
		SpringApplication.run(IpSaktiBackendApplication.class, args);
	}

	private static void loadDotenv() {
		List<Path> searchPaths = List.of(
			Path.of(".env"),
			Path.of("../.env"),
			Path.of("../../.env")
		);

		for (Path p : searchPaths) {
			if (Files.isRegularFile(p)) {
				try {
					List<String> lines = Files.readAllLines(p);
					for (String line : lines) {
						String trimmed = line.trim();
						if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
						int idx = trimmed.indexOf('=');
						if (idx > 0) {
							String key = trimmed.substring(0, idx).trim();
							String val = trimmed.substring(idx + 1).trim();
							if (System.getProperty(key) == null && System.getenv(key) == null) {
								System.setProperty(key, val);
							}
						}
					}
					System.out.println("[IP-SAKTI] Loaded environment configuration from: " + p.toAbsolutePath());
					break;
				} catch (Exception ignored) {
				}
			}
		}
	}

}
