package geoProject.mmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import geoProject.mmap.service.mService;
import org.geotools.data.*;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
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

import static geoProject.mmap.service.myUtil.getPostgisInfo;

public class RefactoringImpl extends EgovAbstractServiceImpl implements mService {
    @Resource(name = "mDAO")
    private mDAO mDAO;

    @Resource(name = "globalProperties")
    Properties globalProperties;

    private Path unZip2(MultipartFile zipFile) throws IOException {
        Path shpFileTempPath = Files.createTempDirectory("shp-upload");
        System.out.println(shpFileTempPath);

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
            deleteTemp(shpFileTempPath);
            return null;
        }
        return shpFileTempPath;
    }

    public String getShapefileGeomType2(MultipartFile zipFile) throws IOException {
        Path shpFilePath = null;
        try {
            shpFilePath = unZip2(zipFile);
            if (shpFilePath == null) {
                return "no .shp file / zip file error";
            }
            FileDataStore dataStore = FileDataStoreFinder.getDataStore(shpFilePath.toFile());
            if (dataStore != null) {
                SimpleFeatureType schema = dataStore.getFeatureSource().getSchema();
                return (schema != null) ? schema.getGeometryDescriptor().getType().getName().getLocalPart() : "geotoolsError Getschema";
            } else {
                return "geotoolsError GetdataStore";
            }
        } catch (IOException e) {
            return "I/O error: " + e.getMessage();
        } finally {
            if (shpFilePath != null) {
                deleteTemp(shpFilePath);
            }
        }
    }

    public String createTableByShp2(MultipartFile zipFile, String layerId) throws IOException {
        DefaultTransaction transaction = new DefaultTransaction("createTableTransaction");
        try {
            Path shpFilePath = unZip2(zipFile);
            if (shpFilePath == null) {
                return "no .shp file / zip file error";
            }

            Map<String, Object> params = getPostgisInfo(globalProperties);
            DataStore dataStore = DataStoreFinder.getDataStore(params);

            if (dataStore == null) {
                return "dataStore is Null error";
            }

            FileDataStore fileDataStore = FileDataStoreFinder.getDataStore(shpFilePath.toFile());
            SimpleFeatureType schema = fileDataStore.getSchema();

            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.setName(layerId);

            CoordinateReferenceSystem crs = schema.getCoordinateReferenceSystem();
            String srs = CRS.toSRS(crs);
            if (!("EPSG:5187".equals(srs) || "Korea_2000_Korea_East_Belt_2010".equals(srs))) {
                //    crs to 5187 logic
            }
            builder.setCRS(crs);

            for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
                if (attribute instanceof GeometryDescriptor) {
                    GeometryDescriptor geomDesc = (GeometryDescriptor) attribute;
                    builder.add(geomDesc.getLocalName(), geomDesc.getType().getBinding());
                } else {
                    builder.add(attribute.getName().getLocalPart(), attribute.getType().getBinding());
                }
            }

            SimpleFeatureType newSchema = builder.buildFeatureType();
            dataStore.createSchema(newSchema);

            SimpleFeatureStore featureStore = (SimpleFeatureStore) dataStore.getFeatureSource(layerId);
            featureStore.setTransaction(transaction);
            featureStore.addFeatures(fileDataStore.getFeatureSource().getFeatures());

            transaction.commit();
            return "SUCCESS";
        } catch (Exception e) {
            transaction.rollback();
            return e.getMessage() + "FAIL error";
        } finally {
            transaction.close();
        }
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
