;(function () {
    const context = require.context('./', false, /\.json$/);
    module.exports = context.keys().reduce((dumps, key) => {
        dumps[key.replace(/^\.\//, '').replace(/\.json$/, '')] = context(key);
        return dumps;
    }, {});
})();