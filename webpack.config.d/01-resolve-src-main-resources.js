;(function (config) {
    let path = require('path');
    config.resolve.modules.push(path.resolve("../../../../src/jsMain/resources"));

    config.module.rules.push(
        {
            test: /\.(lua)$/i,
            type: 'asset/source'
        }
    );

})(config);

