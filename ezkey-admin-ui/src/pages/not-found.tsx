import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { I18N_STORAGE_KEY } from '@/i18n';

export default function NotFoundPage() {
  const { t, i18n } = useTranslation('common');

  const setLanguage = (lng: 'en' | 'fr') => {
    i18n.changeLanguage(lng);
    window.localStorage.setItem(I18N_STORAGE_KEY, lng);
  };

  return (
    <div data-testid="not-found-page" className="min-h-screen bg-bg flex items-center justify-center p-4">
      <div className="text-center relative w-full max-w-md">
        <div className="absolute top-0 right-0 flex items-center gap-1 text-sm">
          <button
            type="button"
            onClick={() => setLanguage('en')}
            data-testid="not-found-language-en"
            className={i18n.language.startsWith('en') ? 'font-bold text-fg' : 'text-fg-muted hover:text-fg'}
            aria-label="English"
          >
            EN
          </button>
          <span className="text-fg/30">|</span>
          <button
            type="button"
            onClick={() => setLanguage('fr')}
            data-testid="not-found-language-fr"
            className={i18n.language.startsWith('fr') ? 'font-bold text-fg' : 'text-fg-muted hover:text-fg'}
            aria-label="Français"
          >
            FR
          </button>
        </div>
        <p className="font-black leading-none text-fg/10" style={{ fontSize: '10rem' }}>
          404
        </p>
        <h1 className="text-2xl font-black text-fg mt-2">{t('notFound.title')}</h1>
        <p className="text-fg-muted mt-2 mb-6">{t('notFound.description')}</p>
        <Link to="/dashboard">
          <Button data-testid="not-found-back-button">{t('buttons.backToDashboard')}</Button>
        </Link>
      </div>
    </div>
  );
}
