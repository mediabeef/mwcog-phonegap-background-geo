//
//  Location.h
//  CDVBackgroundGeolocation
//
//  Created by Marian Hello on 10/06/16.
//

#ifndef Location_h
#define Location_h

#import <CoreLocation/CoreLocation.h>
#import "Config.h"

@interface Location : NSObject <NSCopying>

@property (nonatomic, retain) NSNumber *id;
@property (nonatomic, retain) NSDate *time;
@property (nonatomic, retain) NSNumber *accuracy;
@property (nonatomic, retain) NSNumber *altitudeAccuracy;
@property (nonatomic, retain) NSNumber *speed;
@property (nonatomic, retain) NSNumber *heading;
@property (nonatomic, retain) NSNumber *altitude;
@property (nonatomic, retain) NSNumber *latitude;
@property (nonatomic, retain) NSNumber *longitude;
@property (nonatomic, retain) NSString *provider;
@property (nonatomic, retain) NSNumber *serviceProvider;
@property (nonatomic, retain) NSString *type;
@property (nonatomic) BOOL isValid;
@property (nonatomic, retain) Config *config;
@property (nonatomic) BOOL is_end_of_trip;

+ (instancetype) fromCLLocation:(CLLocation*)location;
+ (NSTimeInterval) locationAge:(CLLocation*)location;
+ (NSMutableDictionary*) toDictionary:(CLLocation*)location;
+ (NSData *)sendSynchronousRequest:(NSURLRequest *)request
                 returningResponse:(__autoreleasing NSURLResponse **)responsePtr
                             error:(__autoreleasing NSError **)errorPtr;

- (void) setConfig:(Config*)input_config;
- (NSTimeInterval) locationAge;
- (NSMutableDictionary*) toDictionary;
- (NSMutableDictionary*) toDictionaryWithId;
- (CLLocationCoordinate2D) coordinate;
- (BOOL) hasAccuracy;
- (BOOL) hasTime;
- (double) distanceFromLocation:(Location*)location;
- (BOOL) isBetterLocation:(Location*)location;
- (BOOL) isBeyond:(Location*)location radius:(NSInteger)radius;
- (BOOL) postAsJSON:(NSString*)url withHttpHeaders:(NSMutableDictionary*)httpHeaders withConfig:(NSObject*)config error:(NSError * __autoreleasing *)outError;
- (BOOL) getPost:(NSString*)url withHttpHeaders:(NSMutableDictionary*)httpHeaders
    withConfig:(Config*)config withLocation:(Location*)location error:(NSError * __autoreleasing *)outError;
- (id) copyWithZone: (NSZone *)zone;

@end

#endif /* Location_h */
