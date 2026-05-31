module.exports = {
  preset: '@react-native/jest-preset',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],
  moduleNameMapper: {
    '\\.svg$': '<rootDir>/__mocks__/svgMock.js',
  },
  // Ship ESM from node_modules (e.g. @react-navigation/native); Jest must transform them.
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?|@react-native-async-storage/async-storage|@react-navigation|@react-navigation/.*|react-native-screens|react-native-safe-area-context|react-native-gesture-handler|@tanstack/.*)/)',
  ],
};
