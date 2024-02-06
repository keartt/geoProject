package geoProject.mmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import geoProject.mmap.service.mService;
import org.geotools.data.*;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDumper;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
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
        String geomType = "no .shp file";
        String ctResult= geomType;
        String hasPrj = null;
        Path tempDir = null;
        try {
            // 유저이름까지 겹쳐서 중복없게 수정바람
            tempDir = Files.createTempDirectory("shp-upload");
            unzip(zipFile.getInputStream(), tempDir);
            String shpFileName = Arrays.stream(tempDir.toFile().list())
                    .filter(fileName -> fileName.toLowerCase().endsWith(".shp"))
                    .findFirst()
                    .orElse(null);
            if (shpFileName != null) {
                Path shpFilePath = tempDir.resolve(shpFileName);

                boolean prjExists = Files.exists(shpFilePath.resolveSibling(shpFileName.replace(".shp", ".prj")));
                if(prjExists) {
                    hasPrj = "EXIST";
                }
                geomType = getShapefileGeomType(shpFilePath);
                ctResult = createTableByShp(shpFilePath, layerId);
            }
        } finally {
            delDir(tempDir);
        }
        return new String[]{geomType, ctResult, hasPrj};
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
                ShapefileDataStore fileDataStore = new ShapefileDataStore(shpFilePath.toFile().toURI().toURL());
                fileDataStore.setCharset(Charset.forName("euc-kr"));

                SimpleFeatureType schema = fileDataStore.getSchema();

                SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
                builder.setName(layerId);
                builder.setCRS(CRS.decode("EPSG:5187"));

                for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
                    if (attribute instanceof GeometryDescriptor) {
                        GeometryDescriptor geomDesc = (GeometryDescriptor) attribute;
                        builder.add(geomDesc.getLocalName(), geomDesc.getType().getBinding());
                    } else {
                        // 속성 추가 예제
                        String attributeName = attribute.getName().getLocalPart();
                        byte[] encodedValue = attributeName.getBytes(Charset.forName("EUC-KR"));
                        String key = new String(encodedValue, Charset.forName("EUC-KR"));

                        Class<?> attributeType = attribute.getType().getBinding();

                        builder.add(key, attributeType);
                    }
                }

                SimpleFeatureType newSchema = builder.buildFeatureType();

                // 새로운 스키마를 사용하여 테이블 생성
                dataStore.createSchema(newSchema);

                // 새로 생성한 테이블에 대한 SimpleFeatureStore를 얻음
                SimpleFeatureStore featureStore = (SimpleFeatureStore) dataStore.getFeatureSource(layerId);
                featureStore.setTransaction(transaction);

                // SHP 파일의 Feature를 새로 생성한 테이블에 추가
                featureStore.addFeatures(fileDataStore.getFeatureSource().getFeatures());

                // 트랜잭션 커밋
                transaction.commit();
                ctResult = "SUCCESS";
            } else {
                ctResult = "dataStore is Null Fail";
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
    public void returnShpZip(String layerName, HttpServletResponse response) {

        // Database connection parameters
        Map<String, Object> params = getPostgisInfo(globalProperties);
        try {
            // Getting DataStore
            DataStore dataStore = DataStoreFinder.getDataStore(params);

            // Define the table to download
            FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(layerName);

            Path tempDir = Files.createTempDirectory("shp-download");
            Files.createDirectories(tempDir);

            // ShapefileDumper 설정
            ShapefileDumper dumper = new ShapefileDumper(tempDir.toFile());
            dumper.setCharset(Charset.forName("EUC-KR"));
            int maxSize = 100 * 1024 * 1024;
            dumper.setMaxDbfSize(maxSize);

            // 데이터 덤프
            SimpleFeatureCollection fc = (SimpleFeatureCollection) source.getFeatures();
            dumper.dump(fc);

            // ZIP 파일로 압축
            Path zipFilePath = tempDir.resolve(layerName + "_output.zip");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
                Files.walk(tempDir)
                        .filter(Files::isRegularFile)
                        .filter(path -> !Files.isDirectory(path) && !path.equals(zipFilePath)) // 무한루프방지
                        .forEach(file -> {
                            try (FileInputStream fis = new FileInputStream(file.toFile())) {
                                ZipEntry zipEntry = new ZipEntry(file.toFile().getName());
                                zos.putNextEntry(zipEntry);
                                byte[] bytes = new byte[1024];
                                int length;
                                while ((length = fis.read(bytes)) >= 0) {
                                    zos.write(bytes, 0, length);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }

            // HTTP 응답 헤더 설정
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=" + layerName + "_output.zip");

            // 파일 내용을 HttpServletResponse로 출력
            try (OutputStream os = response.getOutputStream()) {
                Files.copy(zipFilePath, os);
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
                // 예외 처리 로직 추가
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }finally {
                delDir(tempDir);
            }

        } catch (IOException e) {
            e.printStackTrace();
            // 예외 처리 로직 추가
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }



}