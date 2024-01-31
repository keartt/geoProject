$( document ).ready(function() {
    const map = new ol.Map({
        target: 'map',
        layers: [
            new ol.layer.Tile({
                source: new ol.source.OSM(),
            }),
        ],
        view: new ol.View({
            center: ol.proj.fromLonLat([126.9770, 37.5665]),
            zoom: 2,
        }),
    });
});

var SHP = {
        init: function () {
            $('.fileAttach.mmap').click(() => $('#mmapFile').click())
            var geometryType;

            // 파일 선택 시 이벤트 처리
            $('#mmapFile').change(function (e) {
                var fileInput = $(this)[0];
                if (fileInput.files.length > 0) {
                    var fileSize = fileInput.files[0].size;
                    var maxSize = [30 * 1024 * 1024, '30MB' ];

                    if (fileSize > maxSize[0]) {
                        alert('파일 크기 ' +maxSize[1] + '초과!! <br>다른 파일을 선택해주세요.');
                        return;
                    }

                    var fileName = fileInput.files[0].name;
                    $('.upload-name').val(fileName);

                    geometryType = 'noShp';
                    MMAP.SHP.preview(e).then((result) => geometryType = result);
                }
            });

            $('.btn_next').click(function () {
                var ok = true;
                if (!$('.upload-name').val()) {
                    alert('파일을 선택해주세요');
                    ok = false;
                }
                if (geometryType == 'noShp') {
                    alert('파일을 다시 선택해주세요');
                    ok = false;
                }

                if(ok) {
                    var atchfile = $('#mmapFile')[0].files[0];
                    MMAP.SHP.create(atchfile);
                }
            });


        },
        /**
         * 미리보기
         * @param 파일이벤트
         * @returns {Promise<Point/Polygon/LineString>}
         */
        preview: async function (e) {
            CMMLAYER.removeLayerOnMap('shpLayerPreview');
            const files = e.target.files;
            let features = [];
            let geometryType =''; // LineString, Polygon, Point

            // Polygon & Line
            var style = new ol.style.Style({
                fill: new ol.style.Fill({
                    color: "rgba(0, 0, 0, 0.5)"
                }),
                stroke: new ol.style.Stroke({
                    color: "rgba(255, 204, 50, 1.0)",
                    width: 3
                })
            });

            for (let i = 0, ii = files.length; i < ii; ++i) {
                const file = files.item(i);
                await loadshpAsync(file);
            }

            function loadshpAsync(file) {
                return new Promise((resolve, reject) => {
                    loadshp({url: file, encoding: 'EUC-KR', EPSG: 5187}, (geojson) => {

                        const fileFeatures = new ol.format.GeoJSON().readFeatures(geojson, {
                            featureProjection: 'EPSG:5187'
                        });

                        for (const feature of fileFeatures) {
                            geometryType = feature.getGeometry().getType();
                            // console.log('피쳐 프로퍼티 ',feature.getProperties())

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

                                // style = new ol.style.Style({
                                //     image: new ol.style.Icon({
                                //         src: '/images/poi/lprice_apt.svg',
                                //         anchor: [0.5, 1],
                                //         scale: 2,
                                //     }),
                                // });
                            } else {
                                features.push(feature);
                            }
                        }

                        var shpLayer = new ol.layer.Tile({
                            name: 'shpLayerPreview',
                            source: new ol.source.TileImageVector({
                                source: new ol.source.Vector({
                                    features: features,
                                    useSpatialIndex: true,
                                    wrapX: false
                                }),
                                style: style
                            })
                        });
                        map.addLayer(shpLayer);

                        const vectorSource = new ol.source.Vector({
                            features: features
                        });
                        map.getView().fit(vectorSource.getExtent());

                        // 레이어 피쳐 클릭 이벤트
                        // 문제 : 폴리곤만 댐
                        map.on('click', function (e) {
                            console.log(e.coordinate)
                            map.forEachFeatureAtPixel(e.pixel, function (feature, layer) {
                                console.log(1)
                                if (layer === shpLayer) {
                                    console.log(feature.getGeometry().getType())
                                    // 피쳐 프로퍼티 하나씩 뽑아서 표로 만들어서 클릭시 보여주면 댐
                                    for (var key in feature.getProperties()) {
                                        console.log('key:', key, ', value:', feature.getProperties()[key]);
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

                        resolve();
                    });
                });
            }

            return geometryType;
        },

        /**
         * 레이어 정보 등록, 레이어 공개범위 등록, 파일 DB 에 저장
         * @table scdtw_mmap_layer_info, scdtw_mmap_layer_pubdep, ids, scdtw_atchfile_mng
         * @param atchfile
         */
        create: function (atchfile) {
            var data = {
                user_id: MAIN.USER.id,
                title: '제목',
                contents: '내용',
                dept_id: ['부서1', '부서2'],
            }

            $.ajax({
                url: '/mmap/insertShpLayer.do',
                type: 'POST',
                dataType: 'json',
                contentType: 'application/json; charset=utf-8',
                data: JSON.stringify(data)
            }).then(function(res) {
                console.log('레이어 테이블 생성 후 아이디 반환 =>', res.layerId);

                let formData = new FormData();
                formData.append('userId', MAIN.USER.id);
                formData.append('workType', 'mmap');
                formData.append('atchfile', atchfile);
                formData.append('atchfileId', res.layerId);

                // 해당 아이디랑 파일 가지고 파일 디비에 인서트
                return $.ajax({
                    url: '/file/insertFileUpload.do',
                    type: 'POST',
                    async: false,
                    processData: false,
                    contentType: false,
                    data: formData,
                }).then(function(res2) {
                    console.log('파일저장완료');
                }).fail(function(jqXHR, textStatus, errorThrown) {
                    console.error('파일저장 실패:', textStatus, errorThrown);
                });
            }).fail(function(jqXHR, textStatus, errorThrown) {
                console.error('레이어 생성 실패:', textStatus, errorThrown);
            });
        },

        read: function () {
        },
        update: function () {
        },
        delete: function () {
        },
    }
