import {defineConfig} from 'orval';

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
          useQuery: true,
          useMutation: true,
          version: 5,
        },
      },
    },
  },
});