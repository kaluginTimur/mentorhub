// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = defineConfig([
  { ignores: ['src/app/core/api/**'] },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    languageOptions: {
      parserOptions: { projectService: true, tsconfigRootDir: __dirname },
    },
    rules: {
      '@typescript-eslint/no-deprecated': 'error',
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'app',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'app',
          style: 'kebab-case',
        },
      ],
      '@angular-eslint/prefer-host-metadata-property': 'error',
      '@angular-eslint/prefer-service-decorator': 'error',
    },
  },
  {
    // Type-checked rules for the application code only; tests and their data stay on the rules above.
    files: ['**/*.ts'],
    ignores: ['**/*.spec.ts', '**/test-data.ts', 'src/app/core/testing/**', 'src/app/shared/ui/**'],
    extends: [tseslint.configs.recommendedTypeChecked],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              regex: '^\\.\\.?/((.*/)?core/)?testing(/|$)',
              message: 'Test helpers under core/testing may only be imported in tests.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.spec.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      'no-restricted-syntax': [
        'error',
        {
          selector: 'ObjectExpression > Property[key.name="provide"][value.name="Api"]',
          message:
            'Use provideApiStub(createApiStub()) from core/testing/api-stub instead of mocking Api directly.',
        },
        {
          selector:
            'NewExpression[callee.name="HttpErrorResponse"]:has(Property[key.name="error"])',
          message:
            'Use problemError(...) from core/testing/problem-error instead of constructing HttpErrorResponse with an untyped error body.',
        },
        {
          selector: 'TSAsExpression[typeAnnotation.type="TSNeverKeyword"]',
          message: 'Do not cast with `as never` in tests.',
        },
      ],
    },
  },
  {
    files: ['src/app/shared/ui/**/*.ts'],
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'hlm',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'hlm',
          style: 'kebab-case',
        },
      ],
      '@typescript-eslint/consistent-type-definitions': 'off',
      '@angular-eslint/no-input-rename': 'off',
      '@angular-eslint/prefer-service-decorator': 'off',
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {
      '@angular-eslint/template/prefer-class-binding': 'error',
      '@angular-eslint/template/prefer-style-binding': 'error',
    },
  },
]);
