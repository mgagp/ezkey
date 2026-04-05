/**
 * Jest stub for SVG imports (react-native-svg-transformer).
 */
const React = require('react');
const {View} = require('react-native');

module.exports = {
  __esModule: true,
  default: function SvgMock(props) {
    return React.createElement(View, {...props, testID: 'svg-mock'});
  },
};
