;(function () {
    const context = require.context('./', true, /\.lua$/);
    module.exports = context.keys().map((key) => [key.replace(/^\.\//, ''), context(key)]);
})();