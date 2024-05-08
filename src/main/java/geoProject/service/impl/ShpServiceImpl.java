package geoProject.service.impl;

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
import geoProject.service.ShpService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service("shpService")
public class ShpServiceImpl extends EgovAbstractServiceImpl implements ShpService {

    @Resource(name = "globalProperties")
    Properties globalProperties;

    @Resource(name = "shpDAO")
    private ShpDAO shpDAO;

    @Override
    public String[] getGeomType(MultipartFile zipFile) throws IOException, RuntimeException {
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
                String geomType = (schema != null) ? schema.getGeometryDescriptor().getType().getName().getLocalPart().toLowerCase() : null;
                return new String[]{geomType, prjExists ? "EXIST" : null};
            } else {
                throw new IOException("dataStore is null");
            }
        } finally {
            closeDataStore(dataStore);
            deleteTemp(tempDir);
        }
    }

    @Override
    public String[] createTableByShp(MultipartFile zipFile, String lyrId, String schema) throws IOException {
        String [] result = null;
        DataStore dataStore = null;
        ShapefileDataStore fileDataStore = null;
        Path tempDir = null;

        Map<String, Object> params = getPostgisInfo(globalProperties, schema);
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
            fileDataStore.setCharset(Charset.forName("utf-8"));
            SimpleFeatureType featureSchema = fileDataStore.getFeatureSource().getSchema();
            // geomType 재반환
            result = new String[]{featureSchema.getGeometryDescriptor().getType().getName().getLocalPart()};

            // 가져온 스키마 기반으로 피쳐타입 생성하기
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.setName(lyrId);
            builder.setCRS(CRS.decode("EPSG:5187")); // 목표 좌표계 설정
            // 기존 속성 추가
            for (AttributeDescriptor attribute : featureSchema.getAttributeDescriptors()) {
                if (attribute instanceof GeometryDescriptor) {
                    GeometryDescriptor geomDesc = (GeometryDescriptor) attribute;
                    builder.add(geomDesc.getLocalName(), geomDesc.getType().getBinding());
                } else {
                    String attributeName = attribute.getName().getLocalPart();
                    byte[] encodedValue = attributeName.getBytes(Charset.forName("utf-8"));
                    String key = new String(encodedValue, Charset.forName("utf-8"));
                    builder.add(key, attribute.getType().getBinding());
                }
            }

            // 추가한 속성들로 새로운 스키마 생성하고 테이블 만들기
            SimpleFeatureType newSchema = builder.buildFeatureType();
            dataStore.createSchema(newSchema);
            // 만든 테이블의 피쳐 스토어 얻어오기
            SimpleFeatureStore featureStore = (SimpleFeatureStore) dataStore.getFeatureSource(lyrId);
            featureStore.setTransaction(transaction);

            // 좌표계 변환 및 피쳐 스토어에 값 insert
            CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:5187", true);
            CoordinateReferenceSystem sourceCRS = featureSchema.getCoordinateReferenceSystem();
            String srs = CRS.toSRS(sourceCRS);

            boolean needTrans = false;
            if(sourceCRS != null){
                needTrans = !srs.equals("Korea_2000_Korea_East_Belt_2010") && (!srs.equals("EPSG:5187"));
            }
            if (needTrans) {
                MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
                List<SimpleFeature> newFeatures = new ArrayList<>();
                try (SimpleFeatureIterator iter = fileDataStore.getFeatureSource().getFeatures().features()) {
                    while (iter.hasNext()) {
                        SimpleFeature feature = iter.next();
                        SimpleFeatureBuilder newBuilder = new SimpleFeatureBuilder(newSchema);
                        for (AttributeDescriptor descriptor : featureSchema.getAttributeDescriptors()) {
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

            // id, geom 컬럼이 있을경우 컬럼명 변경
            chgColumn("id", lyrId, lyrId, schema);
            chgColumn("geom", lyrId+"g", lyrId, schema);
            // fid -> id, the_geom -> geom
            chgColumn("fid", "id", lyrId, schema);
            chgColumn("the_geom", "geom", lyrId, schema);

        } catch (FactoryException | TransformException | RuntimeException e) {
            result = null;
            transaction.rollback();
        } finally {
            transaction.close();
            closeDataStore(dataStore);
            closeDataStore(fileDataStore);
            deleteTemp(tempDir);
        }
        return result;
    }

    @Override
    public void chgColumn(String oriNm, String newNm, String lyrId, String schema) {
        Map<String, Object> data = new HashMap<>();
        data.put("schema", schema);
        data.put("lyr_id", lyrId);
        data.put("oriNm", oriNm);
        data.put("newNm", newNm);
        List<String> columnList = shpDAO.selectLyrColums(data);
        if (columnList != null && columnList.contains(oriNm) && !columnList.contains(newNm)){
            shpDAO.chgColumn(data);
        };
    }

    @Override
    public String downloadShpFile(String lyrId, HttpServletResponse response, String schema) {
        DataStore dataStore = null;
        Path tempDir = null;
        Map<String, Object> params = getPostgisInfo(globalProperties, schema);
        String result = null;
        try {
            dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore == null) {
                throw new IOException("dataStore is null");
            }

            // Define the table to download
            FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(lyrId);

            tempDir = Files.createTempDirectory("shp-download");
            Files.createDirectories(tempDir);

            // ShapefileDumper 설정
            ShapefileDumper dumper = new ShapefileDumper(tempDir.toFile());
            dumper.setCharset(Charset.forName("EUC-KR"));
            long maxSize = 9999L * 1024L * 1024L;
            dumper.setMaxDbfSize(maxSize);

            // 데이터 덤프
            SimpleFeatureCollection fc = (SimpleFeatureCollection) source.getFeatures();
            dumper.dump(fc);

            // ZIP 파일로 압축
            Path zipFilePath = tempDir.resolve(lyrId + "_output.zip");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
                Files.walk(tempDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> !Files.isDirectory(path) && !path.equals(zipFilePath)) // 무한루프방지
                    .forEach(file -> {
                        try (FileInputStream fis = new FileInputStream(file.toFile())) {
                            // 파일명 현재 시간으로
                            String originalFileName = file.getFileName().toString();
                            String fileExtension = originalFileName.substring(originalFileName.lastIndexOf('.'));
                            String newFileName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + fileExtension;
                            ZipEntry zipEntry = new ZipEntry(newFileName);
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
            response.setHeader("Content-Disposition", "attachment; filename=" + lyrId + "_output.zip");

            // 파일 내용을 HttpServletResponse로 출력
            OutputStream os = response.getOutputStream();
            Files.copy(zipFilePath, os);
            os.flush();
            // 성공시 null
        } catch (Exception e) {
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
        return shpDAO.getStyleList(geomType);
    }

    @Override
    public boolean existTable(String lyrId, String schema) {
        Map<String, Object> data = new HashMap<>();
        data.put("lyr_id", lyrId);
        data.put("schema", schema);
        return shpDAO.existTable(data);
    }

    @Override
    public boolean dropUldLyr(Map<String, Object> data) {
        boolean dropShpTbl = false;
        shpDAO.dropShpTbl(data);
        // 삭제한 후에 해당 테이블이 남아있는지 확인
        dropShpTbl = !existTable((String) data.get("lyr_id"), (String) data.get("schema"));
        // 삭제됐을 경우 return true
        return dropShpTbl;
    }

    @Override
    public int insertNewSty(Map<String, Object> data) {
        return shpDAO.insertNewSty(data);
    }

    @Override
    public boolean isStyDefault(String styId) {
        return shpDAO.isStyDefault(styId).equals("y");
    }

    @Override
    public boolean createUserLyr(Map<String, Object> data) {
        String lyrId = (String) data.get("lyr_id");
        String schema = getSchema(lyrId);
        data.put("lyr_id", lyrId);
        data.put("schema", schema);
        List<String> columns = (List<String>) data.get("columns");
        if (columns.contains("id")) {
            columns.remove("id");
            columns.add(lyrId);
            data.put("columns", columns);
        }
        if (columns.contains("geom")) {
            columns.remove("geom");
            columns.add(lyrId+"g");
            data.put("columns", columns);
        }
        shpDAO.createUserLyr(data);
        return existTable(lyrId, schema);
    }

    @Override
    public int insertUserLyrOne(Map<String, Object> data) {
        List<String> keyList = new ArrayList<>();
        List<String> valueList = new ArrayList<>();
        String lyrFileId = (String) data.get("lyr_file_id");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (!key.equals("corx") && !key.equals("cory") && !key.equals("user_id")
                    && !key.equals("lyr_id") && !key.equals("lyr_file_id")) {
                if (key.equals("id")){
                    key = lyrFileId;
                }
                if (key.equals("geom")){
                    key = lyrFileId +"g";
                }
                keyList.add(key);
                valueList.add(String.valueOf(value));
            }
        }
        data.put("schema", getSchema(lyrFileId, (String) data.get("user_id")));
        data.put("keyList", keyList);
        data.put("valueList", valueList);

        return shpDAO.insertUserLyrOne(data);
    }

    @Override
    public List<String> selectLyrColums(String lyrId) {
        Map<String, Object> data = new HashMap<>();
        data.put("schema", getSchema(lyrId));
        data.put("lyr_id", lyrId);
        return shpDAO.selectLyrColums(data);
    }

    @Override
    public Map<String, Object> selectUldLyrOne(String lyrId) {
        return shpDAO.selectUldLyrOne(lyrId);
    }

    @Override
    public int updateSty(Map<String, Object> data) {
        return shpDAO.updateSty(data);
    }

    @Override
    public String getStyIdByLyr(String lyrId) {
        return shpDAO.getStyIdByLyr(lyrId);
    }

    @Override
    public boolean deleteSty(String styId) {
        return shpDAO.deleteSty(styId) > 0;
    }

    @Override
    public int insertFileUldTable(String lyrId, String workspace, byte[] img, String styId, String userId, String geomType) {
        Map<String, Object> params = new HashMap<>();
        params.put("lyr_id", lyrId);
        params.put("task_se_nm", workspace);
        params.put("img", img);
        if (styId == null | styId.equals("null") || styId.isEmpty()){
            styId = null;
        }
        params.put("sty_id", styId);
        params.put("user_id", userId);
        params.put("geomType", geomType);
        return shpDAO.insertFileUldTable(params);
    }

    @Override
    public void alterTblNm(Map<String, Object> params) {
        for (String index : shpDAO.getIndexs(params)) {
            params.put("index", index);
            shpDAO.dropIndex(params);
        }
        shpDAO.alterTblNm(params);
    }

    @Override
    public int updateFilUldLyrId(Map<String, Object> params) {
        return shpDAO.updateFilUldLyrId(params);
    }

    @Override
    public int updateLyrPrvwImg(String lyrId, String userId, byte[] img) {
        Map<String, Object> params = new HashMap<>();
        params.put("img", img);
        params.put("lyr_id", lyrId);
        params.put("user_id", userId);
        return shpDAO.updateLyrPrvwImg(params);
    }

    @Override
    public boolean chkSchema(String schema) {
        if (shpDAO.chkSchema(schema)){
            return true;
        }else{
            shpDAO.createSchema(schema);
            return shpDAO.chkSchema(schema);
        }
    }

    @Override
    public String getSchema(String lyrId) {
        Map<String, Object> data = shpDAO.selectUldLyrOne(lyrId);
        if (data == null){
            return "public";
        }
        if (((String) data.get("task_se_nm")).equals("scdtw")){
            return "public";
        }else{
            return (String) data.get("reg_id");
        }
    }

    @Override
    public String getSchema(String workspace, String regId) {
        if (workspace.equals("scdtw")){
            return "public";
        }else{
            return regId;
        }
    }

    @Override
    public boolean chkAuth(HttpSession session, String lyrId) {
        String userId = ((String) session.getAttribute("LOGIN_ID")).toLowerCase();
        return chkAuth(userId, lyrId);
    }

    @Override
    public boolean chkAuth(String userId, String lyrId) {
        Map<String, Object> fileLyr = shpDAO.selectUldLyrOne(lyrId);
        String regId;
        if (fileLyr == null){
            regId = (String) shpDAO.selectPmapLyrOne(lyrId).get("reg_id");
        }else {
            regId = (String) fileLyr.get("reg_id");
        }
        return userId.equals(regId);
    }

    @Override
    public String deleteUldLyr(Map<String, Object> data) {
        return shpDAO.deleteLyrTbl(data) > 0 ? "성공" : "실패";
    }

    @Override
    public List<Map<String, Object>> selectUldPointLyrOne(String lyrId, String column) {
        Map<String, Object> data = new HashMap<>();
        data.put("lyr_id", lyrId);
        data.put("schema", getSchema(lyrId));
        if (column != null && shpDAO.selectLyrColums(data).contains((String)column)){
            if (column.equals("id")){
                column = lyrId;
            } else if (column.equals("geom")) {
                column = lyrId+"g";
            }
        }
        data.put("column", column);
        return shpDAO.selectUldPointLyrOne(data);
    }

    @Override
    public Map<String, Object> getLyrImg(String lyrId) {
        return shpDAO.getLyrImg(lyrId);
    }

    // datastore close
    private void closeDataStore(DataStore dataStore) {
        if (dataStore != null) {
            dataStore.dispose();
        }
    }

    private Map<String, Object> unZip(MultipartFile zipFile) throws IOException {
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
        } finally {
            if (convFile != null) {
                convFile.delete();
            }
        }
    }

    // 디렉토리 내 파일 및 하위 디렉토리 삭제
    private void deleteTemp(Path tempDir) throws IOException {
        if (tempDir == null) {
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
    private Map<String, Object> getPostgisInfo(Properties properties, String schema) {
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
        postgisInfo.put("schema", schema);
        postgisInfo.put("database", jdbcUrl.substring(slashIndex + 1));
        postgisInfo.put("user", username);
        postgisInfo.put("passwd", password);

        return postgisInfo;
    }

}
