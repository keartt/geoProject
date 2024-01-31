package geoProject.mmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.geotools.data.*;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
                System.out.println("Coordinate Reference System: " + crs);
                crs = CRS.decode("EPSG:5187");
                System.out.println("espg -> " + crs);
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

}