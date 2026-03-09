import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { AuthProvider } from '@/context/auth-context';
import { DemoModeProvider } from '@/context/demo-mode-context';
import { ToastProvider } from '@/context/toast-context';
import { queryClient } from '@/lib/query-client';
import { router } from '@/routes';

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <DemoModeProvider>
          <ToastProvider>
            <RouterProvider router={router} />
          </ToastProvider>
        </DemoModeProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}
