/**
 * EZKey Login JavaScript
 * 
 * Frontend-first integration with EZKey Admin API for passwordless authentication.
 * Handles login initiation, polling for device approval, and session management.
 * 
 * @author Ezkey contributors
 * @since 2025
 */

// Session storage keys
const STORAGE_KEYS = {
  TOKEN: 'ezkey_token',
  USERNAME: 'ezkey_username',
  ADMIN_TYPE: 'ezkey_admin_type',
  EXPIRES_AT: 'ezkey_expires_at'
};

// Polling configuration
const POLL_CONFIG = {
  TIMEOUT_MS: 120000,  // 2 minutes max wait
  INTERVAL_MS: 2000    // Poll every 2 seconds
};

/**
 * Initialize the login page
 */
document.addEventListener('DOMContentLoaded', function() {
  // Check for logout parameter
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.get('logout') === 'true') {
    clearSession();
  }
  
  // Setup form handler
  const loginForm = document.getElementById('login-form');
  if (loginForm) {
    loginForm.addEventListener('submit', handleLogin);
  }
});

/**
 * Handle login form submission
 * @param {Event} event - Form submit event
 */
async function handleLogin(event) {
  event.preventDefault();
  
  const username = document.getElementById('username').value.trim();
  const challengeRequested = document.getElementById('challengeRequested').checked;
  
  if (!username || username.length < 3) {
    showError('Username must be at least 3 characters');
    return;
  }
  
  // Disable form and show pending state
  setFormDisabled(true);
  showPending();
  
  try {
    // Step 1: Initiate login
    const loginResponse = await initiateLogin(username, challengeRequested);
    
    if (loginResponse.status === 'accepted') {
      // Direct approval (no challenge, device auto-approved)
      handleSuccess(loginResponse);
      return;
    }
    
    if (loginResponse.status === 'pending') {
      // Show challenge code if present
      if (loginResponse.challengeCode) {
        showChallengeCode(loginResponse.challengeCode);
      }
      
      // Step 2: Wait for device approval
      const waitResponse = await waitForApproval(
        loginResponse.authAttemptId,
        loginResponse.challengeCode
      );
      
      if (waitResponse.status === 'accepted') {
        handleSuccess(waitResponse);
      } else {
        showError(waitResponse.message || 'Authentication rejected');
      }
    } else {
      showError(loginResponse.message || 'Authentication failed');
    }
  } catch (error) {
    console.error('Login error:', error);
    showError(error.message || 'Connection error. Please try again.');
  }
}

/**
 * Call Admin API to initiate login
 * @param {string} username - Username to authenticate
 * @param {boolean} challengeRequested - Whether to request challenge verification
 * @returns {Promise<Object>} Login response
 */
async function initiateLogin(username, challengeRequested) {
  const response = await fetch(`${EZKEY_CONFIG.adminApiUrl}/api/v1/admin/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      username: username,
      challengeRequested: challengeRequested
    })
  });
  
  const data = await response.json();
  
  if (!response.ok && !data.status) {
    throw new Error(data.message || `HTTP ${response.status}`);
  }
  
  return data;
}

/**
 * Poll Admin API waiting for device approval
 * @param {number} authAttemptId - Auth attempt ID to wait for
 * @param {number} challengeCode - Challenge code for verification (if any)
 * @returns {Promise<Object>} Wait response
 */
async function waitForApproval(authAttemptId, challengeCode) {
  const startTime = Date.now();
  
  while (Date.now() - startTime < POLL_CONFIG.TIMEOUT_MS) {
    try {
      let url = `${EZKEY_CONFIG.adminApiUrl}/api/v1/admin/auth/wait`;
      
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          authAttemptId: authAttemptId,
          challengeCode: challengeCode
        })
      });
      
      const data = await response.json();
      
      // Check if completed (accepted or rejected)
      if (data.status === 'accepted' || data.status === 'rejected') {
        return data;
      }
      
      // Still pending, wait and retry
      await sleep(POLL_CONFIG.INTERVAL_MS);
      
    } catch (error) {
      console.error('Poll error:', error);
      // Continue polling on network errors
      await sleep(POLL_CONFIG.INTERVAL_MS);
    }
  }
  
  // Timeout
  throw new Error('Authentication timeout. Please try again.');
}

/**
 * Handle successful authentication
 * @param {Object} response - Success response with token
 */
function handleSuccess(response) {
  // Store session data
  sessionStorage.setItem(STORAGE_KEYS.TOKEN, response.token);
  sessionStorage.setItem(STORAGE_KEYS.USERNAME, response.username);
  sessionStorage.setItem(STORAGE_KEYS.ADMIN_TYPE, response.adminType);
  if (response.expiresAt) {
    sessionStorage.setItem(STORAGE_KEYS.EXPIRES_AT, response.expiresAt);
  }
  
  // Redirect to dashboard
  window.location.href = '/dashboard';
}

/**
 * Show pending/waiting state
 */
function showPending() {
  hideElement('status-error');
  showElement('status-area');
  showElement('status-pending');
  hideElement('challenge-display');
}

/**
 * Show challenge code
 * @param {number} code - Challenge code to display
 */
function showChallengeCode(code) {
  document.getElementById('challenge-value').textContent = code;
  showElement('challenge-display');
}

/**
 * Show error state
 * @param {string} message - Error message to display
 */
function showError(message) {
  hideElement('status-pending');
  showElement('status-area');
  showElement('status-error');
  document.getElementById('error-message').textContent = message;
  setFormDisabled(false);
}

/**
 * Reset form to initial state
 */
function resetForm() {
  hideElement('status-area');
  hideElement('status-pending');
  hideElement('status-error');
  hideElement('challenge-display');
  setFormDisabled(false);
  document.getElementById('username').focus();
}

/**
 * Enable/disable form inputs
 * @param {boolean} disabled - Whether to disable
 */
function setFormDisabled(disabled) {
  document.getElementById('username').disabled = disabled;
  document.getElementById('challengeRequested').disabled = disabled;
  const button = document.querySelector('.login-button');
  if (button) {
    button.disabled = disabled;
    button.textContent = disabled ? 'AUTHENTICATING...' : 'LOGIN WITH EZKEY';
  }
}

/**
 * Clear session data
 */
function clearSession() {
  Object.values(STORAGE_KEYS).forEach(key => {
    sessionStorage.removeItem(key);
  });
}

/**
 * Check if user is authenticated
 * @returns {boolean} True if authenticated
 */
function isAuthenticated() {
  const token = sessionStorage.getItem(STORAGE_KEYS.TOKEN);
  const expiresAt = sessionStorage.getItem(STORAGE_KEYS.EXPIRES_AT);
  
  if (!token) return false;
  
  // Check expiration if present
  if (expiresAt) {
    const expiry = new Date(expiresAt);
    if (expiry < new Date()) {
      clearSession();
      return false;
    }
  }
  
  return true;
}

/**
 * Get current session data
 * @returns {Object|null} Session data or null if not authenticated
 */
function getSession() {
  if (!isAuthenticated()) return null;
  
  return {
    token: sessionStorage.getItem(STORAGE_KEYS.TOKEN),
    username: sessionStorage.getItem(STORAGE_KEYS.USERNAME),
    adminType: sessionStorage.getItem(STORAGE_KEYS.ADMIN_TYPE),
    expiresAt: sessionStorage.getItem(STORAGE_KEYS.EXPIRES_AT)
  };
}

// Utility functions
function showElement(id) {
  const el = document.getElementById(id);
  if (el) el.classList.remove('hidden');
}

function hideElement(id) {
  const el = document.getElementById(id);
  if (el) el.classList.add('hidden');
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Export for dashboard page
window.EZKeyAuth = {
  isAuthenticated,
  getSession,
  clearSession,
  STORAGE_KEYS
};
