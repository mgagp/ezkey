import { useEffect } from 'react';
import { isRouteErrorResponse, useRouteError } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';

const CHUNK_RELOAD_KEY = 'ezkey_admin_ui_chunk_reload_path';

function getErrorMessage(error: unknown): string {
  if (isRouteErrorResponse(error)) {
    return `${error.status} ${error.statusText}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function isDynamicImportFailure(message: string): boolean {
  return (
    message.includes('Failed to fetch dynamically imported module') ||
    message.includes('Importing a module script failed') ||
    message.includes('error loading dynamically imported module')
  );
}

/** Root router error boundary, including stale lazy-route chunks after a UI rebuild. */
export function RouteErrorBoundary() {
  const { t } = useTranslation('common');
  const error = useRouteError();
  const message = getErrorMessage(error);
  const dynamicImportFailure = isDynamicImportFailure(message);

  useEffect(() => {
    if (!dynamicImportFailure) {
      return;
    }
    const currentPath = window.location.pathname + window.location.search;
    if (window.sessionStorage.getItem(CHUNK_RELOAD_KEY) === currentPath) {
      return;
    }
    window.sessionStorage.setItem(CHUNK_RELOAD_KEY, currentPath);
    window.location.reload();
  }, [dynamicImportFailure]);

  const reload = () => {
    window.sessionStorage.removeItem(CHUNK_RELOAD_KEY);
    window.location.reload();
  };

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center p-4">
      <div className="w-full max-w-lg bg-surface border-2 border-fg shadow-brutal-lg p-6">
        <p className="text-xs font-black uppercase tracking-[0.22em] text-accent">
          {dynamicImportFailure ? t('routeError.appUpdatedEyebrow') : t('routeError.errorEyebrow')}
        </p>
        <h1 className="text-2xl font-black text-fg mt-3">
          {dynamicImportFailure ? t('routeError.appUpdatedTitle') : t('routeError.genericTitle')}
        </h1>
        <p className="text-fg-muted mt-3">
          {dynamicImportFailure
            ? t('routeError.appUpdatedDescription')
            : t('routeError.genericDescription')}
        </p>
        {!dynamicImportFailure && (
          <pre className="mt-4 max-h-40 overflow-auto bg-bg border-2 border-fg/20 p-3 text-xs text-fg-muted whitespace-pre-wrap">
            {message}
          </pre>
        )}
        <div className="mt-6 flex justify-end">
          <Button type="button" onClick={reload}>
            {t('routeError.reload')}
          </Button>
        </div>
      </div>
    </div>
  );
}
