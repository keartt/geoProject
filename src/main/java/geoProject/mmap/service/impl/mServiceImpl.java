package geoProject.mmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.geotools.data.*;
import org.geotools.data.shapefile.ShapefileDumper;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import geoProject.mmap.service.mService;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static geoProject.mmap.service.myUtil.*;

@Service("mService")
public class mServiceImpl extends EgovAbstractServiceImpl implements mService {
    @Resource(name = "mDAO")
    private mDAO mDAO;

    @Resource(name = "globalProperties")
    Properties globalProperties;

    @Override
    public String[] getGeomType(MultipartFile zipFile, String layerId) throws IOException {
        String geomType;
        String ctResult;
        Path tempDir = null;
        try {
            // 유저이름까지 겹쳐서 중복없게 수정바람
            tempDir = Files.createTempDirectory("shp-upload");
            unzip(zipFile.getInputStream(), tempDir);
            String shpFileName = Arrays.stream(tempDir.toFile().list())
                    .filter(fileName -> fileName.toLowerCase().endsWith(".shp"))
                    .findFirst()
                    .orElse(null);
            geomType = "no .shp file";
            ctResult = geomType;
            if (shpFileName != null) {
                Path shpFilePath = tempDir.resolve(shpFileName);
                geomType = getShapefileGeomType(shpFilePath);
                ctResult = createTableByShp(shpFilePath, layerId);
            }
        } finally {
            // 디렉토리 내 파일 및 하위 디렉토리 삭제
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            Files.deleteIfExists(tempDir);
        }
        return new String[]{geomType, ctResult};
    }

    @Override
    public String getShapefileGeomType(Path shpFilePath) {
        String geomType = null;

        try {
            FileDataStore dataStore = FileDataStoreFinder.getDataStore(shpFilePath.toFile());
            if (dataStore != null) {
                FeatureSource<SimpleFeatureType, SimpleFeature> featureSource = dataStore.getFeatureSource();
                SimpleFeatureType schema = featureSource.getSchema();
                try {
                    if (schema == null) {
                        geomType = "error get schema";
                    } else {
                        geomType = schema.getGeometryDescriptor().getType().getName().getLocalPart();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else {
                geomType = "error create datastore";
            }
        } catch (Exception e) {
            e.getMessage();
            e.printStackTrace();
        }

        return geomType;
    }

    @Override
    public String createTableByShp(Path shpFilePath, String layerId) throws IOException {
        String ctResult = "fail"; // 초기값을 실패로 설정

        DefaultTransaction transaction = new DefaultTransaction("createTableTransaction");

        try {
            Map<String, Object> params = getPostgisInfo(globalProperties);

            DataStore dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore != null) {
                FileDataStore fileDataStore = FileDataStoreFinder.getDataStore(shpFilePath.toFile());
                SimpleFeatureType schema = fileDataStore.getSchema();

                SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
                builder.setName(layerId);
                // 스키마에서 가져온 css 동작 여부 확인
                // 예외처리로 기본 5187 제공 코드 작성
                CoordinateReferenceSystem crs = schema.getCoordinateReferenceSystem();
                String srs = CRS.toSRS(crs);
                if (!("EPSG:5187".equals(srs) || "Korea_2000_Korea_East_Belt_2010".equals(srs))) {
                //    crs to 5187 logic
                }
                builder.setCRS(crs);

                // builder.add("geometry", Polygon.class);
                for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
                    if (attribute instanceof GeometryDescriptor) {
                        GeometryDescriptor geomDesc = (GeometryDescriptor) attribute;
                        builder.add(geomDesc.getLocalName(), geomDesc.getType().getBinding());
                    } else {
                        builder.add(attribute.getName().getLocalPart(), attribute.getType().getBinding());
                    }
                }

                SimpleFeatureType newSchema = builder.buildFeatureType();

                // 새로운 스키마를 사용하여 테이블 생성
                dataStore.createSchema(newSchema);

                // 새로 생성한 테이블에 대한 SimpleFeatureStore를 얻음
                SimpleFeatureStore featureStore = (SimpleFeatureStore) dataStore.getFeatureSource(layerId);
                featureStore.setTransaction(transaction);

/*    // start
                // SHP 파일의 Feature를 새로 생성한 테이블에 추가
                SimpleFeatureCollection features = fileDataStore.getFeatureSource().getFeatures();

                // ListFeatureCollection 대신에 SimpleFeatureBuilder를 사용하여 List<SimpleFeature>를 생성
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(newSchema);
                List<SimpleFeature> featureList = new ArrayList<>();
                try (SimpleFeatureIterator iterator = features.features()) {
                    while (iterator.hasNext()) {
                        SimpleFeature originalFeature = iterator.next();
                        featureBuilder.init(originalFeature);
                        featureList.add(featureBuilder.buildFeature(originalFeature.getID()));
                    }
                }

                // List<SimpleFeature>를 사용하여 SimpleFeatureCollection을 생성
                SimpleFeatureCollection collection = new ListFeatureCollection(newSchema, featureList);

                // featureStore에 데이터 추가
                featureStore.addFeatures(collection);
    // end*/
                // SHP 파일의 Feature를 새로 생성한 테이블에 추가
                featureStore.addFeatures(fileDataStore.getFeatureSource().getFeatures());

                // 트랜잭션 커밋
                transaction.commit();
                ctResult = "SUCCESS";
            } else {
                ctResult = "dataStore is Nul Fail";
            }
        } catch (Exception e) {
            ctResult = e.getMessage();
            e.printStackTrace();
            // 트랜잭션 롤백
            transaction.rollback();
        } finally {
            transaction.close();
        }
        return ctResult;
    }

    @Override
    public int insertLayerInfo(Map<String, Object> params) {
        return mDAO.insertLayerInfo(params);
    }

    @Override
    public int insertPublicDept(Map<String, Object> params) {
        return mDAO.insertPublicDept(params);
    }

    @Override
    public byte[] returnShpZip(String layerName) {
        Map<String, Object> params = getPostgisInfo(globalProperties);
        try {
            DataStore dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore != null) {
                // Shapefile 덤프 및 압축
                return dumpAndZipShapefile(dataStore, layerName, "D:/scdtw/output.zip");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private static byte[] dumpAndZipShapefile(DataStore dataStore, String inputTypeName, String zipFilePath) throws IOException {
        // Create a temporary directory using Files.createTempDirectory
        Path tempDir = Paths.get(zipFilePath).getParent();

        // ShapefileDumper 설정
        ShapefileDumper dumper = new ShapefileDumper(tempDir.toFile());
        dumper.setCharset(Charset.forName("EUC-KR"));
        int maxSize = 100 * 1024 * 1024;
        dumper.setMaxDbfSize(maxSize);

        // 데이터 덤프
        SimpleFeatureCollection fc = (SimpleFeatureCollection) dataStore.getFeatureSource(inputTypeName).getFeatures();
        dumper.dump(fc);

        // Shapefile 디렉토리를 ZIP 파일로 압축
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (File file : tempDir.toFile().listFiles()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zos.putNextEntry(zipEntry);
                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = fis.read(bytes)) >= 0) {
                        zos.write(bytes, 0, length);
                    }
                    zos.closeEntry();
                }
            }
        } finally {
            deleteDirectory(tempDir.toFile());
        }

        return baos.toByteArray();
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }


}