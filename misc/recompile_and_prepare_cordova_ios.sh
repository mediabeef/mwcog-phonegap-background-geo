#!/usr/bin/env bash
export cdv="/usr/local/bin/cordova"

cd /var/www/mwcog
${cdv} plugin rm cordova-plugin-mediabeef-background-geolocation && ${cdv} plugin add /var/www/mwcbggeo_cordova
#chmod -R 777 /var/www/mwcog/platforms/android/assets/www/.git
${cdv} prepare ios
