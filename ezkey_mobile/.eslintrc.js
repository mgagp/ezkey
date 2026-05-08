module.exports = {
  root: true,
  ignorePatterns: ['android/build/**', 'android/app/build/**', 'ios/build/**'],
  extends: ['@react-native', 'prettier'],
  rules: {
    // Allow the conventional _prefix to mark intentionally unused destructured variables.
    '@typescript-eslint/no-unused-vars': [
      'error',
      {varsIgnorePattern: '^_', argsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_'},
    ],
  },
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
  overrides: [
    {
      files: ['*.ts', '*.tsx'],
      parserOptions: {
        project: './tsconfig.json',
      },
    },
    {
      files: ['jest.setup.js'],
      env: {
        es2021: true,
        node: true,
        jest: true,
      },
    },
  ],
};
