package geoProject.mmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import geoProject.mmap.service.mService;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.opengis.feature.simple.SimpleFeatureType;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RefactoringImpl extends EgovAbstractServiceImpl implements mService {
    @Resource(name = "mDAO")
    private mDAO mDAO;

    @Resource(name = "globalProperties")
    Properties globalProperties;

    public Path unZip2(MultipartFile zipFile) throws IOException {

        Path shpFileTempPath = Files.createTempDirectory("shp-upload");

        ZipInputStream zis = new ZipInputStream(zipFile.getInputStream());
        ZipEntry zipEntry;
        while ((zipEntry = zis.getNextEntry()) != null) {
            Path filePath = shpFileTempPath.resolve(zipEntry.getName());
            Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        String shpFileName = Arrays.stream(shpFileTempPath.toFile().list())
                .filter(fileName -> fileName.toLowerCase().endsWith(".shp"))
                .findFirst()
                .orElse(null);
        if (shpFileName == null) {
            return null;
        }
        return shpFileTempPath;
    }

    public String getgetShapefileGeomType1(MultipartFile zipFile) throws IOException {
        String geomType = null;
        Path shpFilePath = null;
        FileDataStore dataStore = null;
        SimpleFeatureType schema;

        try {
            shpFilePath = unZip2(zipFile);
            if(shpFilePath == null){
                return "no .shp file";
            }
        } catch (IOException e) {
            return e.getMessage() + " unzip error";
        }

        try {
            dataStore = FileDataStoreFinder.getDataStore(shpFilePath.toFile());
        } catch (Exception e) {
            geomType = e.getMessage() + ", geotoolsError GetdataStore";
            return geomType;
        }

        try {
            schema = dataStore.getFeatureSource().getSchema();
            geomType = schema.getGeometryDescriptor().getType().getName().getLocalPart();
        } catch (Exception e) {
            geomType = e.getMessage() + ", geotoolsError Getschema";
        }

        deleteTemp(shpFilePath);
        return geomType;
    }

    // 디렉토리 내 파일 및 하위 디렉토리 삭제
    private void deleteTemp(Path tempDir) throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        Files.deleteIfExists(tempDir);
    }

    @Override
    public String[] getGeomType(MultipartFile shpFile, String layerId) throws IOException {
        return new String[0];
    }

    @Override
    public String getShapefileGeomType(Path shpFilePath) {
        return null;
    }

    @Override
    public String createTableByShp(Path shpFilePath, String layerId) throws IOException {
        return null;
    }

    @Override
    public int insertLayerInfo(Map<String, Object> params) {
        return 0;
    }

    @Override
    public int insertPublicDept(Map<String, Object> params) {
        return 0;
    }
}
