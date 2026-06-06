import {defineConfig} from 'orval';

/**
 * Orval configuration — generates TypeScript types and TanStack Query v5 hooks
 * from the Auth API OpenAPI spec.
 *
 * Spec source: specs/auth-api/openapi-spec.json (canonical)
 * Copied locally via: scripts/update-specs.sh
 *
 * Run codegen: yarn generate:api
 * Output:      app/services/api/generated/auth-api/  (committed)
 */
export default defineConfig({
  authApi: {
    input: {
      target: './openapi-spec.json',
    },
    output: {
      target: './app/services/api/generated/auth-api',
      schemas: './app/services/api/generated/auth-api/model',
      client: 'react-query',
      mode: 'tags-split',
      clean: true,
      override: {
        mutator: {
          path: './app/services/api/orval-mutator.ts',
          name: 'customInstance',
        },
        query: {
          // Orval 8.10+: explicit global `useQuery: true` routes non-GET to useQuery hooks.
          // Orval 8.11+: explicit global `useMutation: true` routes GET to useMutation hooks.
          // Keep verb-aware defaults (GET → query, mutations → mutation): only set version here.
          // See TB-2026-05-28-admin-ui-orval-upgrade checkpoints 8.10 / 8.11 (Option B).
          version: 5,
        },
      },
    },
  },
});