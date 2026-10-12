;(function (config) {
    let path = require('path');
    config.resolve.modules.push(path.resolve("../../../../src/jsMain/resources"));

    config.module.rules.push(
        {
            test: /\.(lua|txt)$/i,
            type: 'asset/source'
        },
        {
            test: /\.json$/i,
            include: /prototypes/,
            type: 'asset/source'
        }
    );

})(config);

