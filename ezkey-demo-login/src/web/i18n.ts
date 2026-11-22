// Demo Login — EN/FR strings (a small app: one dictionary, no i18n library).

export type Lang = 'en' | 'fr';

const en = {
  brand: 'Demo Login',
  tagline: 'A sample application that signs its users in with Ezkey',
  username: 'Username',
  usernamePlaceholder: 'e.g. alice',
  requireChallenge: 'Show a challenge code to pick on my device',
  submit: 'Sign in with Ezkey',
  notConfigured: 'This application is not connected to Ezkey yet: its API key is missing. See the README.',
  waitingTitle: 'Approve on your device',
  waitingTitleChallenge: 'Pick this code on your device',
  waitingHint: 'Open the Ezkey app (or the Demo Device) and approve the request.',
  waitingRead: 'Your device has opened the request.',
  challengeCode: 'Challenge code',
  timeRemaining: 'Time remaining',
  cancel: 'Cancel',
  tryAgain: 'Try again',
  refusedTitle: 'Sign-in refused',
  refusedMessage: 'The request was refused on the device.',
  invalidTitle: 'Sign-in refused',
  invalidMessage: 'The device answer did not match (wrong challenge code or invalid signature).',
  expiredTitle: 'Request expired',
  expiredMessage: 'Nobody answered on the device in time. Start again when your device is at hand.',
  cancelledTitle: 'Request cancelled',
  cancelledMessage: 'You cancelled the sign-in. The request was withdrawn from your device.',
  errorTitle: 'Sign-in unavailable',
  errorCode: 'Reference',
  errors: {
    'invalid-request': 'Enter a username.',
    'account-unavailable': 'This account cannot sign in to this application.',
    'enrollment-unavailable': 'No active Ezkey device is linked to this account. Contact your administrator.',
    'enrollment-other-integration': 'The Ezkey device linked to this account belongs to another application.',
    'attempt-pending': 'A sign-in request for this account is already waiting on the device. Answer or let it expire, then try again.',
    'cooldown': 'Too many sign-in requests for this account. Wait a moment, then try again.',
    'rate-limited': 'Too many attempts. Wait a minute, then try again.',
    'not-configured': 'This application is not connected to Ezkey yet.',
    'configuration-error': 'This application cannot reach Ezkey with its current settings. Contact your administrator.',
    'service-unavailable': 'Ezkey cannot be reached right now. Try again in a moment.',
    'login-not-found': 'This sign-in request is no longer known. Start again.',
    'unexpected': 'Something went wrong. Try again.',
    'network': 'This application cannot be reached. Check your connection.',
  } as Record<string, string>,
  homeTitle: 'You are signed in',
  homeWelcome: 'Welcome, {name}.',
  homeSession: 'Your session',
  homeSignedInAt: 'Signed in at',
  homeExpiresAt: 'Session expires at',
  homeUsername: 'Username',
  howTitle: 'What just happened',
  how: [
    'You typed your username. This application checked that it knows you (its own user table).',
    'Its back end asked Ezkey, with its API key, to create a sign-in request for you (TypeScript SDK, Integration API).',
    'Your device showed the request; you approved it with a key that never leaves the device.',
    'Ezkey told the back end the request was accepted. The back end opened a session (an opaque token in an HttpOnly cookie).',
  ],
  logout: 'Sign out',
  footer: 'Demo Login — reference integration for evaluating Ezkey. Test data only.',
};

const fr: typeof en = {
  brand: 'Demo Login',
  tagline: 'Une application exemple qui connecte ses usagers avec Ezkey',
  username: "Nom d'usager",
  usernamePlaceholder: 'p. ex. alice',
  requireChallenge: 'Afficher un code à choisir sur mon appareil',
  submit: 'Se connecter avec Ezkey',
  notConfigured: "Cette application n'est pas encore reliée à Ezkey : sa clé API manque. Voir le README.",
  waitingTitle: 'Approuvez sur votre appareil',
  waitingTitleChallenge: 'Choisissez ce code sur votre appareil',
  waitingHint: "Ouvrez l'app Ezkey (ou le Demo Device) et approuvez la demande.",
  waitingRead: 'Votre appareil a ouvert la demande.',
  challengeCode: 'Code de vérification',
  timeRemaining: 'Temps restant',
  cancel: 'Annuler',
  tryAgain: 'Réessayer',
  refusedTitle: 'Connexion refusée',
  refusedMessage: "La demande a été refusée sur l'appareil.",
  invalidTitle: 'Connexion refusée',
  invalidMessage: "La réponse de l'appareil ne correspond pas (mauvais code ou signature invalide).",
  expiredTitle: 'Demande expirée',
  expiredMessage: "Personne n'a répondu sur l'appareil à temps. Recommencez quand votre appareil est à portée de main.",
  cancelledTitle: 'Demande annulée',
  cancelledMessage: 'Vous avez annulé la connexion. La demande a été retirée de votre appareil.',
  errorTitle: 'Connexion impossible',
  errorCode: 'Référence',
  errors: {
    'invalid-request': "Entrez un nom d'usager.",
    'account-unavailable': 'Ce compte ne peut pas se connecter à cette application.',
    'enrollment-unavailable': "Aucun appareil Ezkey actif n'est lié à ce compte. Contactez votre administrateur.",
    'enrollment-other-integration': "L'appareil Ezkey lié à ce compte appartient à une autre application.",
    'attempt-pending': "Une demande de connexion attend déjà sur l'appareil pour ce compte. Répondez-y ou laissez-la expirer, puis réessayez.",
    'cooldown': 'Trop de demandes de connexion pour ce compte. Patientez un moment, puis réessayez.',
    'rate-limited': 'Trop de tentatives. Patientez une minute, puis réessayez.',
    'not-configured': "Cette application n'est pas encore reliée à Ezkey.",
    'configuration-error': 'Cette application ne peut pas joindre Ezkey avec sa configuration actuelle. Contactez votre administrateur.',
    'service-unavailable': 'Ezkey est injoignable pour le moment. Réessayez dans un instant.',
    'login-not-found': "Cette demande de connexion n'est plus connue. Recommencez.",
    'unexpected': "Une erreur s'est produite. Réessayez.",
    'network': 'Cette application est injoignable. Vérifiez votre connexion.',
  },
  homeTitle: 'Vous êtes connecté',
  homeWelcome: 'Bienvenue, {name}.',
  homeSession: 'Votre session',
  homeSignedInAt: 'Connecté à',
  homeExpiresAt: 'La session expire à',
  homeUsername: "Nom d'usager",
  howTitle: "Ce qui vient de se passer",
  how: [
    "Vous avez entré votre nom d'usager. L'application a vérifié qu'elle vous connaît (sa propre table d'usagers).",
    'Son back-end a demandé à Ezkey, avec sa clé API, de créer une demande de connexion pour vous (SDK TypeScript, Integration API).',
    "Votre appareil a affiché la demande ; vous l'avez approuvée avec une clé qui ne quitte jamais l'appareil.",
    "Ezkey a confirmé au back-end que la demande était acceptée. Le back-end a ouvert une session (un jeton opaque dans un cookie HttpOnly).",
  ],
  logout: 'Se déconnecter',
  footer: "Demo Login — intégration de référence pour évaluer Ezkey. Données de test seulement.",
};

export const dictionaries: Record<Lang, typeof en> = { en, fr };
export type Strings = typeof en;

const STORAGE_KEY = 'demo-login-lang';

export function initialLang(): Lang {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (saved === 'en' || saved === 'fr') return saved;
  } catch {
    /* storage unavailable: fall through */
  }
  return navigator.language?.toLowerCase().startsWith('fr') ? 'fr' : 'en';
}

export function saveLang(lang: Lang): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, lang);
  } catch {
    /* storage unavailable: language just won't persist */
  }
}
