#!/usr/bin/env bash
export cdv="/var/www/lib/node_modules/cordova/bin/cordova"

cd /var/www/mwcog
${cdv} plugin rm cordova-plugin-mediabeef-background-geolocation && ${cdv} plugin add /var/www/mwcbggeo_cordova
chmod -R 777 /var/www/mwcog/platforms/android/assets/www/.git
${cdv} run android