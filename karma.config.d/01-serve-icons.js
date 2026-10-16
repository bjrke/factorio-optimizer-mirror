// Serves the jsMain icon resources to the jsBrowserTest Karma server.
//
// The Kotlin/JS Gradle plugin only feeds the compiled test bundle to Karma, so
// the `icons/<name>.png` URLs the Icon component renders (relative to the test
// page) have no matching file and Karma logs `404: /icons/...` for each one.
// This inline middleware maps those requests back to `src/jsMain/resources/icons`.
;(function () {
  const fs = require('fs');
  const path = require('path');
  const iconsDir = path.resolve(__dirname, '../../../../src/jsMain/resources/icons');

  config.plugins.push({
    'middleware:serveIcons': [
      'factory',
      function () {
        return function (request, response, next) {
          const url = (request.url || '').split('?')[0];
          if (!url.startsWith('/icons/')) {
            return next();
          }
          const file = path.join(iconsDir, url.slice('/icons/'.length));
          fs.readFile(file, function (error, data) {
            if (error) {
              return next();
            }
            response.writeHead(200, { 'Content-Type': 'image/png' });
            response.end(data);
          });
        };
      },
    ],
  });
  config.middleware = (config.middleware || []).concat('serveIcons');
})();
