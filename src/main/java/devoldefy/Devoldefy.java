package devoldefy;

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.impl.MappingSetImpl;
import org.cadixdev.lorenz.impl.MappingSetModelFactoryImpl;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Devoldefy {
    private static final String CSV = "http://export.mcpbot.bspk.rs/mcp_{csv_type}_nodoc/{csv_build}-{mc_version}/mcp_{csv_type}_nodoc-{csv_build}-{mc_version}.zip";
    private static final String SRG = "https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/{mc_version}/joined.tsrg";
    private static final String YARN = "http://maven.modmuss50.me/net/fabricmc/yarn/{target_minecraft_version}+build.{yarn_build}/yarn-{target_minecraft_version}+build.{yarn_build}.jar";
    private static final String FORGE_JAR = "{home_dir}/.gradle/caches/minecraft/net/minecraftforge/forge/{mc_version}-{forge_version}/{csv_type}/{csv_build}/forgeSrc-{mc_version}-{forge_version}.jar";

    public static void main(String[] args) throws Exception {
        File files = new File("files");

        String sourceMinecraftVersion = ask("Source Minecraft version", "1.13.2");
        String sourceMappingsVersion = ask("Source mappings version", "1.13.2");
        String sourceForgeVersion = ask("Source forge version", "14.23.4.2703");
        String sourceMappingsType = ask("Source mappings type", "snapshot");
        String sourceMappingsBuild = ask("Source mappings build", "20190424");

        String targetMinecraftVersion = ask("Target Minecraft version", "1.14");
        String targetYarnBuild = ask("Target Yarn build", "2");

        String sourceRoot = ask("Path to source root", "./src/main/java");
        String targetRoot = ask("Path to target root", "./updated_src/main/java");

        devoldefy(files, sourceMinecraftVersion, sourceMappingsVersion, sourceForgeVersion, sourceMappingsType, sourceMappingsBuild, targetMinecraftVersion, targetYarnBuild, sourceRoot, targetRoot);
    }

    public static void devoldefy(File files, String sourceMinecraftVersion, String sourceMappingsVersion, String sourceForgeVersion,
                                 String sourceMappingsType, String sourceMappingsBuild, String targetMinecraftVersion, String targetYarnBuild,
                                 String sourceRoot, String targetRoot) throws Exception {
        String csvUrl = CSV.replace("{mc_version}", sourceMappingsVersion).replace("{csv_type}", sourceMappingsType).replace("{csv_build}", sourceMappingsBuild);
        String srgUrl = SRG.replace("{mc_version}", targetMinecraftVersion);
        String yarnUrl = YARN.replace("{target_minecraft_version}", targetMinecraftVersion).replace("{yarn_build}", targetYarnBuild);
        String forgeLocation = FORGE_JAR.replace("{home_dir}", System.getProperty("user.home")).replace("{mc_version}", sourceMinecraftVersion).replace("{forge_version}", sourceForgeVersion).replace("{csv_type}", sourceMappingsType).replace("{csv_build}", sourceMappingsBuild);

        Mappings srg = Readers.tsrg(
            new Scanner(FileHelper.download(srgUrl, files)),
            Readers.csv(new Scanner(FileHelper.extract(FileHelper.download(csvUrl, files), "fields.csv", files))),
            Readers.csv(new Scanner(FileHelper.extract(FileHelper.download(csvUrl, files), "methods.csv", files)))
        );

        Mappings yarn = Readers.tiny(
            new Scanner(FileHelper.extract(FileHelper.download(yarnUrl, files), "mappings/mappings.tiny", files)),
            "official",
            "named"
        );

        Mappings mappings = srg.invert().chain(yarn);

        File sourceDir = new File(sourceRoot);
        File targetDir = new File(targetRoot);
        Files.walk(targetDir.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        targetDir.mkdirs();

        List<Path> classpath = new ArrayList<>();
        File forgeJar = new File(forgeLocation);
        if (!forgeJar.exists()) {
            throw new IllegalStateException("Forge jar not found at " + forgeJar.getCanonicalPath());
        }
        classpath.add(forgeJar.toPath());

        remap(sourceDir.toPath(), targetDir.toPath(), classpath, mappings);
    }

    private static String ask(String message, String fallback) {
        System.out.print(message + (fallback == null ? "" : " (or blank for " + fallback + ")") + ": ");
        String result = new Scanner(System.in).nextLine().trim();
        return result.isEmpty() ? fallback : result;
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

    private static void remap(Path source, Path target, List<Path> classpath, Mappings mappings) throws Exception {
        Mercury mercury = new Mercury();
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

}
