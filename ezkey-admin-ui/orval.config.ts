import { defineConfig } from 'orval';

/**
 * Orval configuration — generates TypeScript types and TanStack Query v5 hooks
 * from the Admin API OpenAPI spec.
 *
 * Spec source: specs/admin-api/openapi-spec.json (canonical)
 * Copied locally via: scripts/update-specs.sh --admin-only
 *
 * Run codegen: npm run generate:api
 * Output:      src/generated/admin-api/  (gitignored, regenerated at build time)
 */
export default defineConfig({
  adminApi: {
    input: {
      target: './openapi-spec.json',
    },
    output: {
      target: './src/generated/admin-api',
      schemas: './src/generated/admin-api/model',
      client: 'react-query',
      mode: 'tags-split',
      clean: true,
      override: {
        mutator: {
          path: './src/lib/orval-mutator.ts',
          name: 'customInstance',
        },
        query: {
          useQuery: true,
          useMutation: true,
          version: 5,
        },
      },
    },
  },
});
