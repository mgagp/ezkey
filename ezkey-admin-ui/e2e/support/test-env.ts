export const testEnv = {
  adminUiUrl: process.env.EZKEY_ADMIN_UI_URL ?? 'http://127.0.0.1:4173',
  demoDeviceUrl: process.env.EZKEY_DEMO_DEVICE_URL ?? 'http://127.0.0.1:8083',
  /** When set, Playwright targets this enrollment row on the Demo Device (stable multi-enrollment stacks). */
  demoDeviceTestEnrollmentId:
    process.env.EZKEY_DEMO_DEVICE_TEST_ENROLLMENT_ID?.trim() || undefined,
  adminUsername: process.env.EZKEY_ADMIN_UI_TEST_USERNAME ?? 'admin.docker',
};
