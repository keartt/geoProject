$( document ).ready(function() {
    const map = new ol.Map({
        target: 'map',
        layers: [
            new ol.layer.Tile({
                source: new ol.source.OSM(),
            }),
        ],
        view: new ol.View({
            center: ol.proj.fromLonLat([0, 0]),
            zoom: 2,
        }),
    });
    testDropShp();

    function testDropShp () {
        map.getViewport().addEventListener('dragover', function (event) {
            event.preventDefault();
        });

        map.getViewport().addEventListener('drop', function (event) {
            event.preventDefault();
            const files = event.dataTransfer.files;

            let features = [];
            let geometryType; // LineString, Polygon, Point

            // Polygon & Line
            var style = new ol.style.Style({
                fill: new ol.style.Fill({
                    color: "rgba(0, 0, 0, 0.55)"
                }),
                stroke: new ol.style.Stroke({
                    color: "rgba(255, 204, 50, 1.0)",
                    width: 3
                })
            });

            for (let i = 0, ii = files.length; i < ii; ++i) {
                const file = files.item(i);

                const loadShpPromise = new Promise((resolve, reject) => {
                    loadshp({url: file, encoding: 'utf-8'}, function (geojson) {
                        resolve(geojson);
                    }, function (error) {
                        reject(error);
                    });
                });

                loadShpPromise.then((geojson) => {
                    const fileFeatures = new ol.format.GeoJSON().readFeatures(geojson);

                    // fileFeatures.forEach(function (feature) {
                    for (const feature of fileFeatures) {
                        geometryType = feature.getGeometry().getType();

                        if (geometryType === 'Point') {
                            const coordinates = feature.getGeometry().getCoordinates();
                            features.push(new ol.Feature(new ol.geom.Point(coordinates)));

                            style = new ol.style.Style({
                                image: new ol.style.Circle({
                                    radius: 5,
                                    fill: new ol.style.Fill({
                                        color: 'red',
                                    }),
                                    stroke: new ol.style.Stroke({
                                        color: 'white',
                                        width: 2
                                    })
                                })
                            });
                        } else {
                            features.push(feature);
                        }
                    }

                    var shpLayer = new ol.layer.Vector({
                        name: 'shpLayer',
                        source: new ol.source.Vector({
                            features: features,
                            useSpatialIndex: true,
                            wrapX: false
                        }),
                    });
                    map.addLayer(shpLayer);

                    const vectorSource = new ol.source.Vector({
                        features: features
                    });
                    map.getView().fit(vectorSource.getExtent());
                }).catch((error) => {
                    console.error(error);
                });

            }
            // 드롭된 파일들을 모두 처리한 후 files 배열 초기화
            event.dataTransfer.clearData();
        });

    }
});

