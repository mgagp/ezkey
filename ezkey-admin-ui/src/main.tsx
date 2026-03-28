import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './i18n';
import './index.css';
// Initialize help context before the router so lazy route chunks reuse the same HelpContext
// instance as HelpProvider (avoids duplicate-module / null context in dev and prod).
import '@/context/help-context';
import App from './App';

const rootEl = document.getElementById('root');
if (!rootEl) throw new Error('Root element not found');

createRoot(rootEl).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

