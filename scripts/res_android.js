#!/usr/bin/env node

/*
*  This hook copies various resource files from our version control system directories into the appropriate platform specific location
*/


/* Configure all the files to copy.
*   Key of object is the source file, value is the destination location. eg- { "source": "destination" }
*   It's fine to put all platform's icons and splash screen files here, even if we don't build for all platforms on each developer's box.
*/

var filestocopy = [
    // android
    { "res/icons/mappointer_small.png": "platforms/android/res/drawable/mappointer_small.png" },
    { "res/icons/mappointer_large.png": "platforms/android/res/drawable/mappointer_large.png" },
    { "res/icon.png": "platforms/android/res/mipmap/icon.png" }
];

var fs = require('fs');
var path = require('path');

// no need to configure below
var rootdir = '';//process.argv[2];

// create drawable directory in res
if (!fs.existsSync("platforms/android/res/drawable")){
    fs.mkdirSync("platforms/android/res/drawable");
}

// create mipmap directory in res
if (!fs.existsSync("platforms/android/res/mipmap")){
    fs.mkdirSync("platforms/android/res/mipmap");
}

filestocopy.forEach(function(obj) {
    Object.keys(obj).forEach(function(key) {
        var val = obj[key];
        var srcfile = path.join(rootdir, key);
        var destfile = path.join(rootdir, val);
        console.log("copying "+srcfile+" to "+destfile);
        var destdir = path.dirname(destfile);
        if (fs.existsSync(srcfile) && fs.existsSync(destdir)) {
            fs.createReadStream(srcfile).pipe(fs.createWriteStream(destfile));
        }
    });
});
