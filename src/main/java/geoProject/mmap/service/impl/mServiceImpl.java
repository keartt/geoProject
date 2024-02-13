package geoProject.mmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import geoProject.mmap.service.mService;
import org.geotools.data.*;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDumper;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

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
        String geomType = null;
        String ctResult = null;
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
                if (prjExists) {
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
        FileDataStore dataStore = null;
        try {
            dataStore = FileDataStoreFinder.getDataStore(shpFilePath.toFile());
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
        } finally {
            if (dataStore != null) {
                dataStore.dispose();
            }
        }

        return geomType;
    }

    @Override
    public String createTableByShp(Path shpFilePath, String layerId) throws IOException {
        String ctResult = "fail";
        DefaultTransaction transaction = new DefaultTransaction("createTableTransaction");
        Map<String, Object> params = getPostgisInfo(globalProperties);
        try {
            DataStore dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore != null) {
                ShapefileDataStore fileDataStore = new ShapefileDataStore(shpFilePath.toFile().toURI().toURL());
                fileDataStore.setCharset(Charset.forName("euc-kr"));
                SimpleFeatureSource featureSource = fileDataStore.getFeatureSource();
                SimpleFeatureType schema = featureSource.getSchema();

                // 스키마를 기반으로 새로운 SimpleFeatureType 생성
                SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
                builder.setName(layerId);
                builder.setCRS(CRS.decode("EPSG:5187")); // 목표 좌표계 설정
                // 기존 속성들을 새 스키마에 추가
                for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
                    if (attribute instanceof GeometryDescriptor) {
                        GeometryDescriptor geomDesc = (GeometryDescriptor) attribute;
                        builder.add(geomDesc.getLocalName(), geomDesc.getType().getBinding());
                    } else {
                        builder.add(attribute.getName().getLocalPart(), attribute.getType().getBinding());
                    }
                }

                SimpleFeatureType newSchema = builder.buildFeatureType();
                dataStore.createSchema(newSchema); // 새 스키마로 테이블 생성
                // 새로 생성한 테이블에 대한 SimpleFeatureStore를 얻음
                SimpleFeatureStore featureStore = (SimpleFeatureStore) dataStore.getFeatureSource(layerId);
                featureStore.setTransaction(transaction);

                // 변환 작업
                CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:5187", true);
                CoordinateReferenceSystem sourceCRS = schema.getCoordinateReferenceSystem();
                String srs = CRS.toSRS(sourceCRS);
                boolean needTrans = ((sourceCRS != null) && (!srs.equals("Korea_2000_Korea_East_Belt_2010")) && (!srs.equals("EPSG:5187")));
                if (needTrans) {
                    MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
                    List<SimpleFeature> newFeatures = new ArrayList<>();
                    try (SimpleFeatureIterator iter = fileDataStore.getFeatureSource().getFeatures().features()) {
                        while (iter.hasNext()) {
                            SimpleFeature feature = iter.next();
                            SimpleFeatureBuilder newBuilder = new SimpleFeatureBuilder(newSchema);

                            for (AttributeDescriptor descriptor : schema.getAttributeDescriptors()) {
                                Object value = feature.getAttribute(descriptor.getName());
                                if (value instanceof Geometry && needTrans) {
                                    value = JTS.transform((Geometry) value, transform);
                                }
                                newBuilder.set(descriptor.getName(), value);
                            }

                            SimpleFeature newFeature = newBuilder.buildFeature(null);
                            newFeatures.add(newFeature);
                        }
                    }
                    SimpleFeatureCollection collection = new ListFeatureCollection(newSchema, newFeatures);
                    featureStore.addFeatures(collection);
                } else {
                    featureStore.addFeatures(fileDataStore.getFeatureSource().getFeatures());
                }

                transaction.commit();
                ctResult = "SUCCESS";
                dataStore.dispose();
                fileDataStore.dispose();
            } else {
                ctResult = "dataStore is Null Fail";
            }
        }
        catch (Exception e) {
            ctResult = e.getMessage();
            e.printStackTrace();
            transaction.rollback();
        }
        finally {
            transaction.close();
        }

        return ctResult;
    }

    // 원본 되던거
    public String createTableByShp0(Path shpFilePath, String layerId) throws IOException {
        String ctResult = "fail"; // 초기값을 실패로 설정
        DataStore dataStore = null;
        ShapefileDataStore fileDataStore = null;

        DefaultTransaction transaction = new DefaultTransaction("createTableTransaction");

        try {
            Map<String, Object> params = getPostgisInfo(globalProperties);
            dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore != null) {
                fileDataStore = new ShapefileDataStore(shpFilePath.toFile().toURI().toURL());
                fileDataStore.setCharset(Charset.forName("euc-kr"));

                SimpleFeatureType schema = fileDataStore.getSchema();

                SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
                builder.setName(layerId);

                // 좌표계가 정의되어 있는데 그게 5187 아닐때만 변환실행
                CoordinateReferenceSystem sourceCRS = schema.getCoordinateReferenceSystem();
                String srs = CRS.toSRS(sourceCRS);
                if ((sourceCRS != null) && (!srs.equals("Korea_2000_Korea_East_Belt_2010")) && (!srs.equals("EPSG:5187"))) {
                    CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:5187");
                    MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS);

                    SimpleFeatureIterator featureIterator = fileDataStore.getFeatureSource().getFeatures().features();
                    while (featureIterator.hasNext()) {
                        SimpleFeature feature = featureIterator.next();
                        Geometry originalGeometry = (Geometry) feature.getDefaultGeometry();

                        // 지오메트리를 5187 좌표계로 변환합니다.
                        Geometry transformedGeometry = JTS.transform(originalGeometry, transform);
                        // 변환된 지오메트리를 해당 feature에 설정합니다.
                        feature.setDefaultGeometry(transformedGeometry);
                    }
                    featureIterator.close();
                }
                builder.setCRS(CRS.decode("EPSG:5187"));

                for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
                    if (attribute instanceof GeometryDescriptor) {
                        GeometryDescriptor geomDesc = (GeometryDescriptor) attribute;
                        builder.add(geomDesc.getLocalName(), geomDesc.getType().getBinding());
                    } else {
                        // 속성 추가 예제
                        String attributeName = attribute.getName().getLocalPart();
                       /* byte[] encodedValue = attributeName.getBytes(Charset.forName("EUC-KR"));
                        String key = new String(encodedValue, Charset.forName("EUC-KR"));
                        builder.add(key, attribute.getType().getBinding());*/
                        builder.add(attributeName, attribute.getType().getBinding());
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
            if (dataStore != null) {
                dataStore.dispose();
            }
            if (fileDataStore != null) {
                fileDataStore.dispose();
            }
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
        DataStore dataStore = null;
        // Database connection parameters
        Map<String, Object> params = getPostgisInfo(globalProperties);
        try {
            // Getting DataStore
            dataStore = DataStoreFinder.getDataStore(params);

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
            } finally {
                delDir(tempDir);
                dataStore.dispose();
            }

        } catch (IOException e) {
            e.printStackTrace();
            // 예외 처리 로직 추가
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public int insertBlob(Map<String, Object> params) {
        return mDAO.insertBlob(params);
    }

    @Override
    public byte[] getBlob(String layerName) {
        return (byte[]) mDAO.getBlob(layerName).get("bdata");
    }

    @Override
    public void insertSty(String[] successArray) {
        mDAO.insertSty(successArray);
    }


}