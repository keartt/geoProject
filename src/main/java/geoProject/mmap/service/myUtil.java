package geoProject.mmap.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class myUtil {

    public static Map<String, Object> getPostgisInfo(Properties properties) {
        String jdbcUrl = properties.getProperty("Globals.postgresql.url");
        String user = properties.getProperty("Globals.postgresql.username");
        String password = properties.getProperty("Globals.postgresql.password");;

        Map<String, Object> PostgisInfo = new HashMap<>();
        int protocolIndex = jdbcUrl.indexOf("://");
        int slashIndex = jdbcUrl.indexOf("/", protocolIndex + 3);

        String[] hostPortArray = jdbcUrl.substring(protocolIndex + 3, slashIndex).split(":");
        String host = hostPortArray[0];
        int port = Integer.parseInt(hostPortArray[1]);

        String database = jdbcUrl.substring(slashIndex + 1);

        PostgisInfo.put("dbtype", "postgis");
        PostgisInfo.put("host", host);
        PostgisInfo.put("port", port);
        PostgisInfo.put("schema", "public");
        PostgisInfo.put("database", database);
        PostgisInfo.put("user", user);
        PostgisInfo.put("passwd", password);

        return PostgisInfo;
    }

    public static void unzip(InputStream zipInputStream, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                Path filePath = destDir.resolve(zipEntry.getName());
                Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
