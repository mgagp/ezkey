const reactNativeFlat = require('@react-native/eslint-config/flat');
const typescriptPlugin = require('@typescript-eslint/eslint-plugin');
const typescriptParser = require('@typescript-eslint/parser');

/** @type {import('eslint').Linter.Config[]} */
module.exports = [
  {
    ignores: [
      '**/*.js',
      '**/*.mjs',
      'scripts/**',
      'android/build/**',
      'android/app/build/**',
      'ios/build/**',
    ],
  },
  ...reactNativeFlat,
  {
    files: ['**/*.ts', '**/*.tsx'],
    languageOptions: {
      parser: typescriptParser,
      parserOptions: {
        project: './tsconfig.json',
        ecmaFeatures: {jsx: true},
      },
    },
    plugins: {
      '@typescript-eslint': typescriptPlugin,
    },
    rules: {
      '@typescript-eslint/no-unused-vars': [
        'error',
        {varsIgnorePattern: '^_', argsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_'},
      ],
    },
  },
];
