package devoldefy;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.impl.MappingSetImpl;
import org.cadixdev.lorenz.impl.MappingSetModelFactoryImpl;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Devoldefy {
    static final FileSystem fileSystem = FileSystems.getDefault();
    static final Path workDirctory = fileSystem.getPath(System.getProperty("user.dir"));

    public static void main(String[] args) throws Exception {
        Properties properties = FileHelper.loadProperties();
        if(properties == null) {
            System.out.println("Please fill out ./config.properties to fit your needs.");
            System.exit(0);
            throw new AssertionError("JVM should've exited?!");
        }
        String forgeJar = properties.getProperty("forge.jar");
        devoldefy(fileSystem.getPath("devoldefy-tmp"),
                fileSystem.getPath(properties.getProperty("source.root")),
                fileSystem.getPath(properties.getProperty("target.root")),
                replaceProperties(properties.getProperty("mappings.mcp.csv"), properties),
                replaceProperties(properties.getProperty("mappings.mcp.srg"), properties),
                replaceProperties(properties.getProperty("mappings.yarn"), properties),
                forgeJar == null ? null : fileSystem.getPath(replaceProperties(forgeJar.replace("{home_dir}", workDirctory.toString()), properties)),
                properties.getProperty("source.version"));
    }

    public static void devoldefy(Path files,
                                 Path sourceRoot, Path targetRoot,
                                 String csvUrl, String srgUrl, String yarnUrl, Path forgeLocation,
                                 String javaVersion) throws Exception {
        Files.createDirectories(files);
        Mappings srg;
        try(FileSystem fileSystem = FileSystems.newFileSystem(URI.create("jar:" + FileHelper.download(csvUrl, files).toUri().toString()), Collections.emptyMap())) {
            srg = Readers.tsrg(
                    new Scanner(FileHelper.download(srgUrl, files)),
                    Readers.csv(new Scanner(fileSystem.getPath("fields.csv"))),
                    Readers.csv(new Scanner(fileSystem.getPath("methods.csv")))
            );
        }

        Mappings yarn;
        try(FileSystem fileSystem = FileSystems.newFileSystem(URI.create("jar:" + FileHelper.download(yarnUrl, files).toUri().toString()), Collections.emptyMap())) {
            yarn = Readers.tiny(
                    new Scanner(fileSystem.getPath("mappings/mappings.tiny")),
                    "official",
                    "named"
            );
        }

        Mappings mappings = srg.invert().chain(yarn);
        if(Files.exists(targetRoot)) {
            Files.walk(targetRoot).sorted(Comparator.reverseOrder()).forEach(it -> {
                try {
                    Files.delete(it);
                } catch (IOException ioe) {
                    throw new IOError(ioe);
                }
            });
        } else {
            Files.createDirectories(targetRoot);
        }

        List<Path> classpath = new ArrayList<>();
        if(forgeLocation != null) {
            if(Files.notExists(forgeLocation))
                throw new IllegalStateException("Forge jar not found at " + forgeLocation.toAbsolutePath());
            classpath.add(forgeLocation);
        } else {
            System.err.println("Warning: A remapped Forge/Minecraft isn't defined.\nYou may want to define either one of them to ensure that remapping goes as intended.");
        }

        remap(sourceRoot, targetRoot, classpath, mappings, javaVersion);
    }

    static String hash(String s) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static void remap(Path source, Path target, List<Path> classpath, Mappings mappings, String javaVersion) throws Exception {
        Mercury mercury = new Mercury();
        // Used to allow missing Forge/Minecraft.
        // This may unexpectedly break stuff.
        mercury.setGracefulClasspathChecks(true);
        mercury.setSourceCompatibility(javaVersion);
        mercury.getClassPath().addAll(classpath);

        MappingSet mappingSet = new MappingSetImpl(new MappingSetModelFactoryImpl());
        mappings.classes.forEach((a, b) -> mappingSet
                .getOrCreateClassMapping(a)
                .setDeobfuscatedName(b)
        );

        mappings.fields.forEach((a, b) -> mappingSet
                .getOrCreateClassMapping(a.split(":")[0])
                .getOrCreateFieldMapping(a.split(":")[1])
                .setDeobfuscatedName(b.split(":")[1])
        );

        mappings.methods.forEach((a, b) -> mappingSet
                .getOrCreateClassMapping(a.split(":")[0])
                .getOrCreateMethodMapping(a.split(":")[1], getDescriptor(a.split(":")[1]))
                .setDeobfuscatedName(b.split(":")[1])
        );

        mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

        mercury.rewrite(source, target);
    }

    private static String getDescriptor(String method) {
        try {
            StringBuilder result = new StringBuilder();

            Reader r = new StringReader(method);
            boolean started = false;
            while (true) {
                int c = r.read();

                if (c == -1) {
                    break;
                }

                if (c == '(') {
                    started = true;
                }

                if (started) {
                    result.append((char) c);
                }
            }

            return result.toString();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static String remapMethodDescriptor(String method, Map<String, String> classMappings) {
        try {
            Reader r = new StringReader(method);
            StringBuilder result = new StringBuilder();
            boolean started = false;
            boolean insideClassName = false;
            StringBuilder className = new StringBuilder();
            while (true) {
                int c = r.read();
                if (c == -1) {
                    break;
                }

                if (c == ';') {
                    insideClassName = false;
                    result.append(classMappings.getOrDefault(className.toString(), className.toString()));
                }

                if (insideClassName) {
                    className.append((char) c);
                } else {
                    result.append((char) c);
                }

                if (c == '(') {
                    started = true;
                }

                if (started && c == 'L') {
                    insideClassName = true;
                    className.setLength(0);
                }
            }

            return result.toString();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    static String replaceProperties(String string, Properties properties) {
        StringBuilder builder = new StringBuilder(string);
        int l = 0;
        while((l = builder.indexOf("${", l)) > 0) {
            int f = builder.indexOf("}", l);
            String prop = builder.substring(l + 2, f);
            String str = properties.getProperty(prop);
            if(str == null) throw new NullPointerException(prop);
            builder.replace(l, f + 1, str);
            l += str.length();
        }
        return builder.toString();
    }
}
