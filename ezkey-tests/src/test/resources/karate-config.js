function fn() {
  // Read system properties set by KarateTestRunner
  var adminUrl = java.lang.System.getProperty('admin.url');
  var authUrl = java.lang.System.getProperty('auth.url');

  // Fallback to defaults if properties not set
  if (!adminUrl) {
    adminUrl = 'http://localhost:9080';
  }
  if (!authUrl) {
    authUrl = 'http://localhost:8080';
  }

  var config = {
    // Base URLs for APIs
    adminUrl: adminUrl,
    authUrl: authUrl,
    adminBaseUrl: adminUrl + '/api/v1',
    authBaseUrl: authUrl + '/api/v1',

    // Common headers
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    },

    // Common wait times (milliseconds)
    shortWait: 1000,
    mediumWait: 3000,
    longWait: 5000,

    // Retry configuration
    retryConfig: {
      count: 3,
      interval: 1000
    }
  };

  // Log configuration
  karate.log('Karate configuration loaded:');
  karate.log('  Admin URL:', config.adminUrl);
  karate.log('  Auth URL:', config.authUrl);

  return config;
}
