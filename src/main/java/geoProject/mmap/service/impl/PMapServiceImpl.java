package geoProject.mmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.geotools.data.*;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
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
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static geoProject.mmap.service.myUtil.getPostgisInfo;

public class PMapServiceImpl extends EgovAbstractServiceImpl {

    @Resource(name = "globalProperties")
    Properties globalProperties;

    public String[] getGeomType(MultipartFile zipFile) throws IOException {
        String[] fail = {null, null};

        Path shpFileTempPath = unZip(zipFile);
        if (shpFileTempPath == null) {
            return fail;
        }

        String shpFileName = Arrays.stream(shpFileTempPath.toFile().list())
                .filter(fileName -> fileName.toLowerCase().endsWith(".shp"))
                .findFirst()
                .orElse(null);
        if (shpFileName == null) {
            return fail;
        }

        boolean prjExists = Files.exists(shpFileTempPath.resolveSibling(shpFileName.replace(".shp", ".prj")));
        try {
            FileDataStore dataStore = FileDataStoreFinder.getDataStore(shpFileTempPath.toFile());
            if (dataStore != null) {
                SimpleFeatureType schema = dataStore.getFeatureSource().getSchema();
                String geomType = (schema != null) ? schema.getGeometryDescriptor().getType().getName().getLocalPart() : null;
                return new String[]{geomType, prjExists ? "EXIST" : null};
            } else {
                return fail;
            }
        } finally {
            deleteTemp(shpFileTempPath);
        }
    }

    public String createTableByShp(MultipartFile zipFile, String layerId) throws IOException {
        Map<String, Object> params = getPostgisInfo(globalProperties);
        Path shpFilePath = unZip(zipFile);
        DefaultTransaction transaction = new DefaultTransaction("createTableTransaction");
        try {
            DataStore dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore == null) {
                return "no dataStore";
            }
            // 파일을 통해 데이터 스토어 만들고 인코딩 설정후 스키마 가져옴
            ShapefileDataStore fileDataStore = new ShapefileDataStore(shpFilePath.toFile().toURI().toURL());
            fileDataStore.setCharset(Charset.forName("euc-kr"));
            SimpleFeatureType schema = fileDataStore.getFeatureSource().getSchema();

            // 가져온 스키마 기반으로 피쳐타입 생성하기
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.setName(layerId);
            builder.setCRS(CRS.decode("EPSG:5187")); // 목표 좌표계 설정
            // 기존 속성 추가
            for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
                if (attribute instanceof GeometryDescriptor) {
                    GeometryDescriptor geomDesc = (GeometryDescriptor) attribute;
                    builder.add(geomDesc.getLocalName(), geomDesc.getType().getBinding());
                } else {
                    builder.add(attribute.getName().getLocalPart(), attribute.getType().getBinding());
                }
            }

            // 추가한 속성들로 새로운 스키마 생성하고 테이블 만들기
            SimpleFeatureType newSchema = builder.buildFeatureType();
            dataStore.createSchema(newSchema);
            // 만든 테이블의 피쳐 스토어 얻어오기
            SimpleFeatureStore featureStore = (SimpleFeatureStore) dataStore.getFeatureSource(layerId);
            featureStore.setTransaction(transaction);

            // 좌표계 변환 및 피쳐 스토어에 값 insert
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
                            if (value instanceof Geometry) {
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
            return "SUCCESS";
        }
        catch (IOException | FactoryException | TransformException | RuntimeException e ) {
            return e.getMessage();
        }
        finally {
            transaction.close();
        }
    }
    


    private Path unZip(MultipartFile zipFile) throws IOException {
        Path shpFileTempPath = Files.createTempDirectory("shp-upload");
        System.out.println(shpFileTempPath);

        ZipInputStream zis = new ZipInputStream(zipFile.getInputStream());
        ZipEntry zipEntry;
        while ((zipEntry = zis.getNextEntry()) != null) {
            Path filePath = shpFileTempPath.resolve(zipEntry.getName());
            Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        return shpFileTempPath;
    }

    // 디렉토리 내 파일 및 하위 디렉토리 삭제
    private void deleteTemp(Path tempDir) throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        Files.deleteIfExists(tempDir);
    }


}
