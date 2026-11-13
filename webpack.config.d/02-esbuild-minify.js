// The Kotlin/JS production build minifies the ~2.6 MiB bundle with webpack's Terser
// minimizer, and that minification dominates the webpack step. esbuild produces a
// comparable bundle much faster; the development build is not minified by either,
// so it is left untouched.
;(function (config) {
    if (config.mode !== 'production') {
        return;
    }

    const { EsbuildPlugin } = require('esbuild-loader');

    config.optimization = config.optimization || {};
    config.optimization.minimizer = [
        new EsbuildPlugin({
            target: 'es2015',
        }),
    ];
})(config);
