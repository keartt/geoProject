<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="path" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd"><html>
<head>
    <title>Title</title>
    <link rel="stylesheet" href="https://openlayers.org/en/v6.3.1/css/ol.css" type="text/css">
    <script src="https://openlayers.org/en/v6.3.1/build/ol.js"></script>
    <script src="${path}/js/lib/jQuery/jquery.min.js"></script>
    <script src="${path}/js/lib/jQuery/jquery-ui.js"></script>
    <script src="${path}/js/lib/jQuery/jquery.mousewheel.min.js"></script>
    <script src="${path}/js/lib/jQuery/jquery.bootpag.min.js"></script>
    <style>
        #map {
            width: 100%;
            height: 800px;
        }
    </style>
    <script type="text/javascript" src="<c:url value="/js/lib/ol/proj4.js" />"></script>
    <script type="text/javascript" src="<c:url value="/js/shp2geoJS/jszip.js" />"></script>
    <script type="text/javascript" src="<c:url value="/js/shp2geoJS/preprocess.js" />"></script>
    <script type="text/javascript" src="<c:url value="/js/shp2geoJS/preview.js" />"></script>
    <script type="text/javascript" src="<c:url value="/js/shp2geoJS/preview_drop.js" />"></script>
</head>
<body>

    <div id="map"></div>
</body>
</html>
