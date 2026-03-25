module.exports = {
  preset: 'react-native',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],
  // Ship ESM from node_modules (e.g. @react-navigation/native); Jest must transform them.
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?|@react-navigation|@react-navigation/.*|react-native-screens|react-native-safe-area-context|react-native-gesture-handler|@tanstack/.*)/)',
  ],
};
