var PMAP ={

    /**
     * 단순 마커 여러개 / 심볼 3개 
     */
    simple: function (){

    },

    /**
     *  팝업 마커 - 위치에 내용 뜨고, 내용 elem 은 길이에 따라 다르게
     */
    popup: function () {
        
    },

    /**
     *  wms 레이어 wfs 처럼 내용 뜰 수 있게
     */
    LayerPopup: function () {

    },

    pointVector: function () {
        let feature = new ol.Feature(new ol.geom.Point([163806.37483485095, 623220.154365809]));
        var style = new ol.style.Style({
            image: new ol.style.Circle({
                radius: 3,
                fill: new ol.style.Fill({
                    color: 'blue' // 여기에 아무 원하는 색상을 지정할 수 있습니다.
                })
            })
        });
        feature.setStyle(style);
        feature.set('id', 'id');
        drawPointLayer.getSource().getVectorSource().addFeature(feature);
        map.addLayer(drawPointLayer);
    },

    test: function () {
        this.ajaxGeo();
        this.wfsVector();
    },

    ajaxGeo: function () {
        $.ajax({
            url : mapConst.proxy + mapConst.prefix2D + '/geoserver/wfs',
            type: 'GET',
            data: {
                service: 'WFS',
                version: '1.1.0',
                request: 'GetFeature',
                // typeName: 'pmap:ksh2_20240214174303',    // point
                typeName: 'pmap:ksh2_20240215175923',    // poly
                outputFormat: 'application/json'
            },
            success: function (res) {
                var geomType = res.features[0].geometry.type;

                /*console.log(geomType);
                console.log(res.features[0]);
                console.log(res.features[0].geometry.coordinates);   // geom
                console.log(res.features[0].geometry.coordinates[0]);   // geom
                console.log(res.features[0].properties);                // 각 key value*/

                var data = [];
                for (var i = 0; i < res.features.length; i++) {
                    var feature = res.features[i];

                    var geom = feature.geometry.coordinates[0];     // point 일떄는 [0] 빼야함
                    var properties = {};
                    for (var key in feature.properties) {
                        properties[key] = feature.properties[key];
                    }
                    var entry = {
                        geom: geom,
                        properties: properties
                    };
                    data.push(entry);
                }
                PMAP.wfsVector(data);
            },

        })
    },

    /**
     *  wfs 레이어 된다, 폴리곤 일단
     */
    wfsVector: function (data) {

        /**
         * example data
        var data =[
            {
                "geom" : [[
                    [163806.37483485095, 623220.154365809],
                    [163856.37483485095, 623220.154365809],
                    [163856.37483485095, 623270.154365809],
                    [163806.37483485095, 623270.154365809],
                    [163806.37483485095, 623220.154365809]
                ]],
                "properties": {
                    "data1" : "hi13",
                    "data2" : "bye23",
                }
            },
        ]
         */

        var styleP = new ol.style.Style({
            fill: new ol.style.Fill({
                color: "rgba(0, 0, 0, 0.5)"
            }),
            stroke: new ol.style.Stroke({
                color: "rgba(255, 204, 50, 1.0)",
                width: 3
            })
        });
        console.log(styleP)

        // 폴리곤 피처 생성
        var features = [];

        for (var i = 0; i < data.length; i++) {
            var feature = new ol.Feature(new ol.geom.Polygon(data[i].geom));
            for (var key in data[i].properties) {
                feature.set(key, data[i].properties[key]);
            }
            feature.setStyle(styleP);
            features.push(feature);
        }

        drawVectorLayer.getSource().getVectorSource().addFeatures(features);
        map.addLayer(drawVectorLayer);

        var selectedFeature;
        map.on('click', function (e) {
            map.forEachFeatureAtPixel(e.pixel, function (feature, layer) {
                if (layer == drawVectorLayer) {
                    if (selectedFeature) {
                        selectedFeature.setStyle(styleP);
                    }
                    selectedFeature = feature;
                    // 피쳐 프로퍼티 하나씩 뽑아서 표로 만들어서 클릭시 보여주면 댐
                    for (var key in feature.getProperties()) {
                        if(key !== 'geometry'){
                            console.log('key:', key, ', value:', feature.getProperties()[key]);
                        }
                    }
                    var clcikStyle = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: "rgba(68,183,187,0.55)"
                        }),
                        stroke: new ol.style.Stroke({
                            color: "rgb(2,31,234)",
                            width: 3
                        })
                    });
                    feature.setStyle(clcikStyle);
                }
            });
        });
    },


    sldParsing: function () {
        async function convertSLDToOlStyle(sldString) {
            const sldParser = new GeoStylerSldParser.SldStyleParser();
            const geostylerStyle = await sldParser.readStyle(sldString);
            const olParser = new GeoStylerOpenLayersParser.OlStyleParser();
            const openLayersStyle = await olParser.writeStyle(geostylerStyle.output);
            return openLayersStyle;
        }

        return new Promise((resolve, reject) => {
            $.ajax({
                url: '/pmap/testsld.do',
                type: 'post',
                dataType: 'json',
                success: function (res) {
                    console.log(res.sld);
                    convertSLDToOlStyle(res.sld).then(openLayersStyle => {
                        console.log('Converted OpenLayers Style:', openLayersStyle);
                        resolve(openLayersStyle);
                    }).catch(error => {
                        reject(error);
                    });
                },
                error: function (xhr, status, error) {
                    reject(error);
                }
            });
        });
    },
}