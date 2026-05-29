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
          // Orval 8.10+: explicit global `useQuery: true` routes POST/PUT/PATCH/DELETE to
          // useQuery hooks (Query wins when both flags are set). Omit it so GET → query and
          // mutations → useMutation — see TB-2026-05-28 checkpoint 8.10 (Option B).
          useMutation: true,
          version: 5,
        },
      },
    },
  },
});
