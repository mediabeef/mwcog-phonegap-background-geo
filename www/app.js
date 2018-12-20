var map,
    previousLocation,
    locationMarkers = [],
    stationaryCircles = [],
    currentLocationMarker,
    locationAccuracyCircle,
    path,
    userStartIntent,
    isStarted = false,
    isLocationEnabled = false,
    configHasChanges = false;

var bgOptions = {
    stationaryRadius: 1,
    distanceFilter: 30,
    desiredAccuracy: 10,
    debug: true,
    notificationTitle: 'Background tracking',
    notificationText: 'enabled',
    notificationIconColor: '#FEDD1E',
    notificationIconLarge: 'mappointer_large',
    notificationIconSmall: 'mappointer_small',
    locationProvider: 0,//backgroundGeolocation.provider.ANDROID_DISTANCE_FILTER_PROVIDER,
    interval: 10,
    fastestInterval: 5,
    activitiesInterval: 10,
    stopOnTerminate: false,
    startOnBoot: true,
    startForeground: true,
    stopOnStillActivity: false,
    activityType: 'Other',
    url: 'http://192.168.168.136/v1/geolocation',//brianserver
    // url: 'http://192.168.1.3/v1/geolocation',//brianmsi
    //syncUrl: 'http://192.168.3.185:3000/sync',
    syncThreshold: 10,
    mwc_username: 'mwc_usernametest',
    httpHeaders: {
        'X-FOO': 'bar'
    },
    pauseLocationUpdates: false,
    saveBatteryOnBackground: false,
    maxLocations: 100
};

var mapOptions = {
    center: { lat: 37.3318907, lng: -122.0318303 },
    zoom: 12,
    disableDefaultUI: false
};

try {
    Object.assign(bgOptions, JSON.parse(localStorage.getItem('bgOptions')));
}
catch (err) {
    console.log(err.message);
}


// Initialize app
var myApp = new Framework7({
    init: false,
    animateNavBackIcon: true,
    precompileTemplates: true,
    domCache: true,
    material: window.isAndroid,
    // fastclick: false
});

// We need to use custom DOM library, let's save it to $$ variable:
var $$ = Dom7;

var mainView = myApp.addView('.view-main');
var view1 = myApp.addView('#view-1');
var view2 = myApp.addView('#view-2');

myApp.onPageInit('map', function (page) {
    renderTabBar(isStarted);

    if (typeof google !== 'undefined') {
        map = new google.maps.Map(Dom7('#mapcanvas')[0], mapOptions);
    }

    if (typeof backgroundGeolocation === 'undefined') {
        myApp.alert('Plugin has not been initialized properly!', 'Error');
        return;
    }

    bgConfigure();
    backgroundGeolocation.onStationary(setStationary);
    backgroundGeolocation.watchLocationMode(
        function (enabled) {
            isLocationEnabled = enabled;
            if (enabled && userStartIntent) {
                startTracking();
            }
            else if (isStarted) {
                stopTracking();
                myApp.alert('Location tracking has been stopped');
            }
        },
        function (error) {
            myApp.alert(error, 'Error watching location mode');
        }
    );

    $$('#tabbar').on('click', '[data-action="tracking"]', function() {
        userStartIntent = !(isStarted & userStartIntent);
        toggleTracking(userStartIntent);
    });

});

myApp.onPageInit('settings', function (page) {
    var options = Object.assign({}, bgOptions);
    var locationProviders = [
        { name: 'ANDROID_DISTANCE_FILTER_PROVIDER', value: 0, selected: false, index: 0 },
        { name: 'ANDROID_ACTIVITY_PROVIDER', value: 1, selected: false, index: 1 },
    ];
    var selectedProvider = 0;

    if (options.locationProvider) {
        selectedProvider = Number(options.locationProvider);
        locationProviders[Number(options.locationProvider)].selected = true;
    }
    options.locationProvider = locationProviders[selectedProvider].name;
    options.locationProviders = locationProviders;

    $$('#settings').html(Template7.templates.settingsTemplate(options));

    $$('[data-action="back"]').click(function(ev) {
        var config = Array.prototype.reduce.call(
            $$('[data-page="settings"] [data-type="config"]'),
            function(values, el) {
                if (el.type === 'checkbox') {
                    values[el.name] = el.checked;
                }
                else {
                    if(!isNaN(parseInt(el.value))) {
                        values[el.name] = parseInt(el.value);
                    }
                    else {
                        values[el.name] = el.value;
                    }
                }
                return values;
            },
        {});
        bgConfigure(config);
        configHasChanges = false;
    });
});

$$('[data-page="settings"]').on('keyup keydown change', '[data-type="config"]', function(ev) {
    console.log('changed', this.name, this.checked, this.value);
    configHasChanges = true;
});

function toggleTracking(shouldStart) {
    if (shouldStart) {
        startTracking();
    }
    else {
        stopTracking();
    }
}

function bgConfigure(config) {
    Object.assign(bgOptions, config);
    localStorage.setItem('bgOptions', JSON.stringify(bgOptions));

    var options = Object.assign({}, bgOptions);
    if (options.interval) { options.interval *= 1000; }
    if (options.fastestInterval) { options.fastestInterval *= 1000; }
    if (options.activitiesInterval) { options.activitiesInterval *= 1000; }

    if (isStarted) {
        stopTracking();
        backgroundGeolocation.configure(
            setCurrentLocation,
            function (err) { console.log('Error occured', err); },
            options
        );
        startTracking();
    }
    else {
        backgroundGeolocation.configure(
            setCurrentLocation,
            function (err) { console.log('Error occured', err); },
            options
        );
    }
}

function startTracking() {
    if (isStarted) { return; }

    backgroundGeolocation.isLocationEnabled (
    function (enabled) {
        isLocationEnabled = enabled;
        if (enabled) {
            backgroundGeolocation.start(
                null,
                function (error) {
                    stopTracking();
                    if (error.code === 2) {
                        myApp.confirm('Would you like to open app settings?', 'Permission denied', function() {
                            backgroundGeolocation.showAppSettings();
                        });
                    }
                    else {
                        myApp.alert(error.message, 'Start failed');
                    }
                }
            );
            isStarted = true;
            renderTabBar(isStarted);
            }
            else {
                myApp.confirm('Would you like to open settings?', 'Location Services are disabled', function() {
                    backgroundGeolocation.showLocationSettings();
                });
            }
        },
        function (error) {
            myApp.alert(error, 'Error detecting status of location settings');
        }
    );
}

function stopTracking() {
    if (!isStarted) { return; }

    backgroundGeolocation.stop();
    isStarted = false;
    renderTabBar(isStarted);
}

function renderTabBar(isStarted) {
    $$('#tabbar').html(Template7.templates.tabbarTemplate({isStarted: isStarted}));
}

function setStationary (location) {
    console.log('[DEBUG] stationary recieved', location);
    var latlng = new google.maps.LatLng(Number(location.latitude), Number(location.longitude));
    var stationaryCircle = new google.maps.Circle({
        fillColor: 'pink',
        fillOpacity: 0.4,
        strokeOpacity: 0,
        map: map,
    });
    stationaryCircle.setCenter(latlng);
    stationaryCircle.setRadius(location.radius);
    stationaryCircles.push(stationaryCircle);
    backgroundGeoLocation.finish();
}

function setCurrentLocation (location) {
    console.log('[DEBUG] location recieved', location);
    if (!currentLocationMarker) {
        currentLocationMarker = new google.maps.Marker({
            map: map,
            icon: {
                path: google.maps.SymbolPath.CIRCLE,
                scale: 10,
                fillColor: '#ec0070', //'gold',
                fillOpacity: 1,
                strokeColor: '#22e2c5',
                strokeWeight: 3
            }
        });
        locationAccuracyCircle = new google.maps.Circle({
            fillColor: 'purple',
            fillOpacity: 0.4,
            strokeOpacity: 0,
            map: map
        });
    }
    if (!path) {
        path = new google.maps.Polyline({
            map: map,
            strokeColor: 'blue',
            fillOpacity: 0.4
        });
    }
    var latlng = new google.maps.LatLng(Number(location.latitude), Number(location.longitude));

    if (previousLocation) {
        // Drop a breadcrumb of where we've been.
        locationMarkers.push(new google.maps.Marker({
            icon: {
                path: google.maps.SymbolPath.CIRCLE,
                scale: 7,
                fillColor: 'green',
                fillOpacity: 1,
                strokeColor: 'white',
                strokeWeight: 3
            },
            map: map,
            position: new google.maps.LatLng(previousLocation.latitude, previousLocation.longitude)
        }));
    } else {
        if (map.getZoom() < 15) {
            map.setZoom(15);
        }
    }
    map.setCenter(latlng);

    // Update our current position marker and accuracy bubble.
    currentLocationMarker.setPosition(latlng);
    locationAccuracyCircle.setCenter(latlng);
    locationAccuracyCircle.setRadius(location.accuracy);

    // Add breadcrumb to current Polyline path.
    path.getPath().push(latlng);
    previousLocation = location;

    backgroundGeoLocation.finish();
}

function onDeviceReady() {
    backgroundGeolocation = window.backgroundGeolocation || window.backgroundGeoLocation || window.universalGeolocation;
    backgroundGeolocation.getLocations(function(locs) {
        var now = Date.now();
        var sameDayDiffInMillis = 24 * 3600 * 1000;
        locs.forEach(function (loc) {
            if ((now - loc.time) <= sameDayDiffInMillis) {
                setCurrentLocation(loc);
            }
        });
    });
    myApp.init();
}

document.addEventListener('deviceready', onDeviceReady, false);
