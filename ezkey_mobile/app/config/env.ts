import Config from 'react-native-config';

const fallbackBaseUrl = 'https://goateed-katalina-monsoonal.ngrok-free.dev';
const fallbackTimeoutMs = 10000;

const parseNumber = (value: string | undefined, fallback: number) => {
  if (!value) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

export const env = {
  apiBaseUrl: Config.EZKEY_API_BASE_URL ?? fallbackBaseUrl,
  requestTimeoutMs: parseNumber(Config.EZKEY_REQUEST_TIMEOUT, fallbackTimeoutMs),
};
