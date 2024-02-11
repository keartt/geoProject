package geoProject.mmap.service;

import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class myUtil {

    // postgres db info set
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

    // unzip process
    public static void unzip(InputStream zipInputStream, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                Path filePath = destDir.resolve(zipEntry.getName());
                Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // 디렉토리 내 파일 및 하위 디렉토리 삭제
    public static void delDir(Path Dir) throws IOException {
        Files.walk(Dir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        Files.deleteIfExists(Dir);
    }

    // json array input process
    public static Map<String, Object> extractParams(MultipartHttpServletRequest multiRequest) {
        Map<String, Object> params = new HashMap<>();

        Enumeration<String> paramNames = multiRequest.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            // 값이 배열 형태이면 모든 값을 배열로 가져옴
            String[] paramValues = multiRequest.getParameterValues(paramName);

            // 값이 배열 형태일 경우 배열 그대로, 아니면 첫 번째 값만 저장
            Object paramValue = (paramValues != null && paramValues.length > 1) ? paramValues : (paramValues != null && paramValues.length == 1) ? paramValues[0] : null;

            params.put(paramName, paramValue);
        }
        return params;
    }

    //resize img 1/4
    public static byte[] resizeImage(byte[] originalImage) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(originalImage);
        BufferedImage bufferedImage = ImageIO.read(bis);

        int newWidth = bufferedImage.getWidth() / 4;
        int newHeight = bufferedImage.getHeight() / 4;
        Image scaledImage = bufferedImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "png", bos);
        byte[] compressedImage = bos.toByteArray();
        bos.close();
        return compressedImage;
    }
}
