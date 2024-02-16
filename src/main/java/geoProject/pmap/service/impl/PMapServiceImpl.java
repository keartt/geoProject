package geoProject.pmap.service.impl;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.geotools.data.*;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDumper;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import geoProject.pmap.service.PMapService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service("pmapService")
public class PMapServiceImpl extends EgovAbstractServiceImpl implements PMapService {

    @Resource(name = "globalProperties")
    Properties globalProperties;

    @Resource(name = "pmapDAO")
    private PMapDAO pMapDAO;

    @Override
    public String[] getGeomType(MultipartFile zipFile) throws IOException, RuntimeException{
        FileDataStore dataStore = null;
        Path tempDir = null;

        try {
            Map<String, Object> fileData = unZip(zipFile);
            tempDir = (Path) fileData.get("tempDir");

            String shpFileName = (String) fileData.get("shpFileName");
            if (shpFileName == null) {
                throw new IOException("shpFile is null");
            }
            Path shpFilePath = (Path) fileData.get("shpFilePath");
            boolean prjExists = Files.exists(shpFilePath.resolveSibling(shpFileName.replace(".shp", ".prj")));

            dataStore = FileDataStoreFinder.getDataStore(shpFilePath.toFile());
            if (dataStore != null) {
                SimpleFeatureType schema = dataStore.getFeatureSource().getSchema();
                String geomType = (schema != null) ? schema.getGeometryDescriptor().getType().getName().getLocalPart() : null;
                return new String[]{geomType, prjExists ? "EXIST" : null};
            }else{
                throw new IOException("dataStore is null");
            }
        } catch (IOException | RuntimeException e) {
            throw e;
        } finally {
            closeDataStore(dataStore);
            deleteTemp(tempDir);
        }
    }

    @Override
    public String createTableByShp(MultipartFile zipFile, String lyr_id) {
        String result = null;
        DataStore dataStore = null;
        ShapefileDataStore fileDataStore = null;
        Path tempDir = null;

        Map<String, Object> params = getPostgisInfo(globalProperties);
        DefaultTransaction transaction = new DefaultTransaction("createTableTransaction");

        try {
            Map<String, Object> fileData = unZip(zipFile);
            tempDir = (Path) fileData.get("tempDir");
            Path shpFilePath = (Path) fileData.get("shpFilePath");

            dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore == null) {
                throw new IOException("dataStore is null");
            }
            // 파일을 통해 데이터 스토어 만들고 인코딩 설정후 스키마 가져옴
            fileDataStore = new ShapefileDataStore(shpFilePath.toFile().toURI().toURL());
            fileDataStore.setCharset(Charset.forName("euc-kr"));
            SimpleFeatureType schema = fileDataStore.getFeatureSource().getSchema();

            // 가져온 스키마 기반으로 피쳐타입 생성하기
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.setName(lyr_id);
            builder.setCRS(CRS.decode("EPSG:5187")); // 목표 좌표계 설정
            // 기존 속성 추가
            for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
                if (attribute instanceof GeometryDescriptor) {
                    GeometryDescriptor geomDesc = (GeometryDescriptor) attribute;
                    builder.add(geomDesc.getLocalName(), geomDesc.getType().getBinding());
                } else {
                    String attributeName = attribute.getName().getLocalPart();
                    byte[] encodedValue = attributeName.getBytes(Charset.forName("euc-kr"));
                    String key = new String(encodedValue, Charset.forName("euc-kr"));
                    builder.add(key, attribute.getType().getBinding());
                }
            }

            // 추가한 속성들로 새로운 스키마 생성하고 테이블 만들기
            SimpleFeatureType newSchema = builder.buildFeatureType();
            dataStore.createSchema(newSchema);
            // 만든 테이블의 피쳐 스토어 얻어오기
            SimpleFeatureStore featureStore = (SimpleFeatureStore) dataStore.getFeatureSource(lyr_id);
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
            transaction.commit();
        } catch (IOException | FactoryException | TransformException | RuntimeException e) {
            result = e.toString();
            try {
                transaction.rollback();
            } catch (IOException ex) {
                result += ","+ex.toString();
            }
        } finally {
            transaction.close();
            closeDataStore(dataStore);
            closeDataStore(fileDataStore);
            try {
                deleteTemp(tempDir);
            } catch (IOException e) {
                result += ","+e.toString();
            }
        }
        return result;
    }

    @Override
    public int insertFileUldTable(MultipartHttpServletRequest multiRequest, String lyr_id, byte[] img) throws ParseException {
        Map<String, Object> params = extractParams(multiRequest);
        params.put("lyr_id", lyr_id);
        params.put("img", img);

        Timestamp reg_dt = new Timestamp(
                new SimpleDateFormat("yyyyMMddHHmmss")
                        .parse(lyr_id.split("_")[1])
                        .getTime());

        params.put("reg_dt", reg_dt);
        // SCDTW_FILE_ULD_LYR_MNG insert
        int result = pMapDAO.insertFileUldTable(params);

        // PBADMS_MAP_SHRN_DEPT_MNG insert
        if (params.get("task_se_nm").equals("pmap")) {
            result += pMapDAO.insertShrDeptTable(params);
        }
        return result;
    }

    @Override
    public String downloadShpFile(String lyr_id, HttpServletResponse response) {
        DataStore dataStore = null;
        Path tempDir = null;
        Map<String, Object> params = getPostgisInfo(globalProperties);
        String result = null;
        try {
            dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore == null) {
                throw new IOException("dataStore is null");
            }

            // Define the table to download
            FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(lyr_id);

            tempDir = Files.createTempDirectory("shp-download");
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
            Path zipFilePath = tempDir.resolve(lyr_id + "_output.zip");
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
                                throw new RuntimeException("Error processing file ", e);
                            }
                        });
            }

            // HTTP 응답 헤더 설정
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=" + lyr_id + "_output.zip");

            // 파일 내용을 HttpServletResponse로 출력
            OutputStream os = response.getOutputStream();
            Files.copy(zipFilePath, os);
            os.flush();
            // 성공시 null
        } catch (IOException e) {
            result = e.toString();
        } finally {
            closeDataStore(dataStore);
            try {
                deleteTemp(tempDir);
            } catch (IOException e) {
                result += "," + e.toString();
            }
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getStyleList(String geomType) {
        return pMapDAO.getStyleList(geomType);
    }

    @Override
    public int insertStyList(List<Map<String, Object>> styList) {
        return pMapDAO.insertStyList(styList);
    }

    @Override
    public boolean existTable(String lyrId) {
        return pMapDAO.existTable(lyrId);
    }

    @Override
    public boolean dropShpTable(String lyrId, String userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("lyr_id", lyrId);
        params.put("reg_id", userId);

        boolean deleteLyrTbl = pMapDAO.deleteLyrTbl(params) > 0;
        boolean dropShpTbl  = false;

        if (deleteLyrTbl){
            pMapDAO.dropShpTbl(lyrId);
            // 삭제한 후에 해당 테이블이 남아있는지 확인
            dropShpTbl = !pMapDAO.existTable(lyrId);
        }
        // 둘 다 삭제됐을 경우 return true
        return deleteLyrTbl && dropShpTbl;
    }

    // datastore close
    private void closeDataStore(DataStore dataStore) {
        if (dataStore != null) {
            dataStore.dispose();
        }
    }

    public Map<String, Object> unZip(MultipartFile zipFile) throws IOException {
        Path tempPath = null;
        File convFile = null;
        try {
            tempPath = Files.createTempDirectory("shp-upload");

            convFile = File.createTempFile("temp", null);
            zipFile.transferTo(convFile);

            String shpFileName = null;
            Path shpFilePath = null;

            try (InputStream is = new FileInputStream(convFile);
                 ArchiveInputStream ais = new ZipArchiveInputStream(is, "EUC-KR")) {

                ArchiveEntry entry;
                while ((entry = ais.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    Path filePath = tempPath.resolve(entryName);
                    if (entry.isDirectory()) {
                        Files.createDirectories(filePath);
                    } else {
                        try (OutputStream os = Files.newOutputStream(filePath)) {
                            IOUtils.copy(ais, os);
                        }
                    }
                    if (entryName.toLowerCase().endsWith(".shp")) {
                        shpFileName = filePath.getFileName().toString();
                        shpFilePath = tempPath.resolve(shpFileName);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("tempDir", tempPath);
            result.put("shpFilePath", shpFilePath);
            result.put("shpFileName", shpFileName);

            return result;
        } catch (IOException | RuntimeException e) {
            deleteTemp(tempPath);
            throw e;
        }finally {
            if (convFile != null) {
                convFile.delete();
            }
        }
    }

    // 디렉토리 내 파일 및 하위 디렉토리 삭제
    private void deleteTemp(Path tempDir) throws IOException {
        if(tempDir == null){
            return;
        }
        // 디렉토리가 빈 디렉토리인지 확인
        try (Stream<Path> stream = Files.list(tempDir)) {
            if (stream.findFirst().isPresent()) {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        Files.deleteIfExists(tempDir);
    }

    // postgis 데이터 파싱
    private Map<String, Object> getPostgisInfo(Properties properties) {
        String jdbcUrl = properties.getProperty("Globals.postgresql.url");
        String username = properties.getProperty("Globals.postgresql.username");
        String password = properties.getProperty("Globals.postgresql.password");

        int protocolIndex = jdbcUrl.indexOf("://");
        int slashIndex = jdbcUrl.indexOf("/", protocolIndex + 3);
        String[] hostPortArray = jdbcUrl.substring(protocolIndex + 3, slashIndex).split(":");

        Map<String, Object> postgisInfo = new HashMap<>();
        postgisInfo.put("dbtype", "postgis");
        postgisInfo.put("host", hostPortArray[0]);
        postgisInfo.put("port", Integer.parseInt(hostPortArray[1]));
        postgisInfo.put("schema", "public");
        postgisInfo.put("database", jdbcUrl.substring(slashIndex + 1));
        postgisInfo.put("user", username);
        postgisInfo.put("passwd", password);

        return postgisInfo;
    }

    // json array input process
    private Map<String, Object> extractParams(MultipartHttpServletRequest multiRequest) {
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

}
