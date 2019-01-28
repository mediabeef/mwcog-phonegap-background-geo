#!/usr/bin/env bash
set -x #ECHO ON
cd /var/www/mwcog/platforms/android/src/com/mediabeef
cp -R ./* /var/www/mwcbggeo_cordova/android/plugin/src/main/java/com/mediabeef/
set +x #ECHO OFF