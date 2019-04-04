//
//  BackgroundGeolocationDelegate.h
//
//  Created by Marian Hello on 04/06/16.
//  Version 2.0.0
//
//  According to apache license
//
//  This is class is using code from christocracy cordova-plugin-background-geolocation plugin
//  https://github.com/christocracy/cordova-plugin-background-geolocation
/*
 Brian 03/08:
 iOS plugin:
 - add get_is_end_of_trip, reset end of trip, get_is_service_running, get_is_service_recording
 - add timer - when BG process kills itself due to timeout, it gives a timeout notification. Clicking on timeout notification will jump to mobile app, go to Start Flextime Trip page, and show another popup
 + mobile app keeps polling for BG process running state
 
 Main functions:
 LocationManager.h: 
 - (BOOL) configure:(Config*)config error:(NSError * __autoreleasing *)outError;
 - (BOOL) start:(NSError * __autoreleasing *)outError;
 - (BOOL) stop:(NSError * __autoreleasing *)outError;
 - (BOOL) finish;
 - (BOOL) isLocationEnabled;
 - (void) showAppSettings;
 - (void) showLocationSettings;
 - (void) switchMode:(BGOperationMode)mode;
 - (NSMutableDictionary*)getStationaryLocation
 - (NSArray<NSMutableDictionary*>*) getLocations;
 - (NSArray<NSMutableDictionary*>*) getValidLocations;
 - (BOOL) deleteLocation:(NSNumber*)locationId;
 - (BOOL) deleteAllLocations;
 - (void) onAppTerminate;
 - (void) startMonitoringLocationWhenSuspended;
 - (void) notify:(NSString*)message;
 - (BOOL) getIsEndOfTrip;
  didUpdateLocation: line 549
 
 
 
 */

#import <Cordova/CDVPlugin.h>
#import "LocationManager.h"

@interface CDVBackgroundGeolocation : CDVPlugin <LocationManagerDelegate>

- (void) configure:(CDVInvokedUrlCommand*)command;
- (void) start:(CDVInvokedUrlCommand*)command;
- (void) stop:(CDVInvokedUrlCommand*)command;
- (void) finish:(CDVInvokedUrlCommand*)command;
- (void) switchMode:(CDVInvokedUrlCommand*)command;
- (void) isLocationEnabled:(CDVInvokedUrlCommand*)command;
- (void) showAppSettings:(CDVInvokedUrlCommand*)command;
- (void) showLocationSettings:(CDVInvokedUrlCommand*)command;
- (void) addStationaryRegionListener:(CDVInvokedUrlCommand*)command;
- (void) watchLocationMode:(CDVInvokedUrlCommand*)command;
- (void) stopWatchingLocationMode:(CDVInvokedUrlCommand*)command;
- (void) getStationaryLocation:(CDVInvokedUrlCommand *)command;
- (void) getLocations:(CDVInvokedUrlCommand*)command;
- (void) getValidLocations:(CDVInvokedUrlCommand*)command;
- (void) deleteLocation:(CDVInvokedUrlCommand*)command;
- (void) deleteAllLocations:(CDVInvokedUrlCommand*)command;
- (void) getLogEntries:(CDVInvokedUrlCommand*)command;
- (void) getIsEndOfTrip:(CDVInvokedUrlCommand*)command;
- (void) resetIsEndOfTrip;
- (void) getIsServiceRunning:(CDVInvokedUrlCommand*)command;
- (void) getIsServiceRecording:(CDVInvokedUrlCommand*)command;
- (void) onPause:(NSNotification *)notification;
- (void) onResume:(NSNotification *)notification;
- (void) onAppTerminate;

@end
