/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Client-side QR image import for demo-device enrollment. Parses payloads compatible with
 * org.ezkey.admin.service.QrCodePayloadService and ezkey_mobile parseQrPayload.
 */
(function () {
  'use strict';

  var DEV_HTTP_HOSTS = ['localhost', '127.0.0.1', '10.0.2.2'];

  /**
   * Validates Auth API URL from QR (aligned with ezkey_mobile urlValidation rules).
   *
   * @param {string|undefined|null} value raw URL
   * @returns {string|undefined} normalized origin+path or undefined
   */
  function validateAuthUrl(value) {
    if (value == null || typeof value !== 'string') {
      return undefined;
    }
    var trimmed = value.trim();
    if (!trimmed) {
      return undefined;
    }
    var u;
    try {
      u = new URL(trimmed);
    } catch {
      return undefined;
    }
    if (u.protocol !== 'http:' && u.protocol !== 'https:') {
      return undefined;
    }
    var host = u.hostname;
    var isHttps = u.protocol === 'https:';
    var isDevHttp = u.protocol === 'http:' && DEV_HTTP_HOSTS.indexOf(host) !== -1;
    if (!isHttps && !isDevHttp) {
      return undefined;
    }
    var path = u.pathname;
    if (path.length > 1) {
      path = path.replace(/\/+$/, '');
    }
    if (path && path !== '/') {
      return u.origin + path;
    }
    return u.origin;
  }

  /**
   * Parses enrollment QR text (JSON or pipe-delimited).
   *
   * @param {string} raw decoded QR string
   * @returns {{ enrollmentId: string, enrollmentProofToken: string, language?: string, authUrl?: string }}
   */
  function parseQrPayload(raw) {
    var trimmed = String(raw).trim();
    if (!trimmed) {
      throw new Error('Empty payload');
    }
    try {
      var json = JSON.parse(trimmed);
      if (json.enrollmentId != null && json.enrollmentProofToken != null) {
        return {
          enrollmentId: String(json.enrollmentId),
          enrollmentProofToken: String(json.enrollmentProofToken),
          language: json.language ? String(json.language) : undefined,
          authUrl: validateAuthUrl(json.authUrl),
        };
      }
    } catch {
      // try pipe format
    }
    var pipeParts = trimmed.split('|');
    if (pipeParts.length >= 2) {
      return {
        enrollmentId: pipeParts[0],
        enrollmentProofToken: pipeParts.slice(1).join('|'),
      };
    }
    throw new Error('Unsupported QR format');
  }

  /**
   * @param {string} configuredBase demo-device ezkey.auth.api.url
   * @param {string|undefined} qrAuthUrl from payload
   * @returns {string|null} warning message or null
   */
  function authUrlMismatchWarning(configuredBase, qrAuthUrl) {
    if (!qrAuthUrl || !configuredBase) {
      return null;
    }
    try {
      var a = new URL(configuredBase.trim());
      var b = new URL(qrAuthUrl);
      if (a.origin !== b.origin) {
        return (
          'This QR targets a different Auth API host than this demo device (QR: '
          + b.origin
          + ', device: '
          + a.origin
          + '). Enrollment may fail unless both reach the same Auth API.'
        );
      }
    } catch {
      return null;
    }
    return null;
  }

  function decodeQrFromImageData(imageData) {
    if (typeof jsQR !== 'function') {
      throw new Error('QR decoder not loaded');
    }
    return jsQR(imageData.data, imageData.width, imageData.height, {
      inversionAttempts: 'attemptBoth',
    });
  }

  /**
   * @param {HTMLImageElement} img loaded image
   * @returns {string|null} decoded data or null
   */
  function scanImageElement(img) {
    var w = img.naturalWidth;
    var h = img.naturalHeight;
    if (w < 1 || h < 1) {
      return null;
    }
    var scales = [1, 2, 3];
    for (var i = 0; i < scales.length; i++) {
      var s = scales[i];
      var canvas = document.createElement('canvas');
      canvas.width = w * s;
      canvas.height = h * s;
      var ctx = canvas.getContext('2d');
      if (!ctx) {
        return null;
      }
      ctx.imageSmoothingEnabled = true;
      ctx.drawImage(img, 0, 0, w * s, h * s);
      var imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
      var code = decodeQrFromImageData(imageData);
      if (code && code.data) {
        return code.data;
      }
    }
    return null;
  }

  /**
   * @param {Blob} blob image blob
   * @returns {Promise<HTMLImageElement>}
   */
  function loadImageFromBlob(blob) {
    return new Promise(function (resolve, reject) {
      var url = URL.createObjectURL(blob);
      var img = new Image();
      img.onload = function () {
        URL.revokeObjectURL(url);
        resolve(img);
      };
      img.onerror = function () {
        URL.revokeObjectURL(url);
        reject(new Error('Could not load image'));
      };
      img.src = url;
    });
  }

  /**
   * @param {Blob} blob
   * @returns {Promise<void>}
   */
  function processImageBlob(blob) {
    var enrollmentIdEl = document.getElementById('enrollmentId');
    var tokenEl = document.getElementById('enrollmentProofToken');
    var errEl = document.getElementById('qrImportError');
    var warnEl = document.getElementById('qrImportWarning');
    var successEl = document.getElementById('qrImportSuccess');
    var previewEl = document.getElementById('qrImportPreview');
    var body = document.body;
    var configuredBase = body.getAttribute('data-auth-api-base') || '';

    if (errEl) {
      errEl.textContent = '';
      errEl.style.display = 'none';
    }
    if (warnEl) {
      warnEl.textContent = '';
      warnEl.style.display = 'none';
    }
    if (successEl) {
      successEl.textContent = '';
      successEl.style.display = 'none';
    }

    return loadImageFromBlob(blob)
      .then(function (img) {
        if (previewEl) {
          previewEl.innerHTML = '';
          img.className = 'qr-import-preview-img';
          img.alt = 'Pasted enrollment QR preview';
          previewEl.appendChild(img);
        }
        var data = scanImageElement(img);
        if (!data) {
          throw new Error(
            'No QR code found in the image. Try a sharper screenshot or enter the token manually.',
          );
        }
        var payload = parseQrPayload(data);
        if (enrollmentIdEl) {
          enrollmentIdEl.value = payload.enrollmentId;
        }
        if (tokenEl) {
          tokenEl.value = payload.enrollmentProofToken;
        }
        if (successEl) {
          successEl.textContent =
            'QR decoded. Review the fields below, then tap Start Enrollment.';
          successEl.style.display = 'block';
        }
        var warn = authUrlMismatchWarning(configuredBase, payload.authUrl);
        if (warn && warnEl) {
          warnEl.textContent = warn;
          warnEl.style.display = 'block';
        }
      })
      .catch(function (e) {
        if (errEl) {
          errEl.textContent = e.message || String(e);
          errEl.style.display = 'block';
        }
      });
  }

  function preventDefaults(e) {
    e.preventDefault();
    e.stopPropagation();
  }

  function init() {
    var zone = document.getElementById('qrDropZone');
    var fileInput = document.getElementById('qrFileInput');
    if (!zone || !fileInput) {
      return;
    }

    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function (eventName) {
      zone.addEventListener(eventName, preventDefaults, false);
    });

    zone.addEventListener('drop', function (e) {
      var dt = e.dataTransfer;
      if (!dt || !dt.files || dt.files.length === 0) {
        return;
      }
      var f = dt.files[0];
      if (!f.type || f.type.indexOf('image') !== 0) {
        var errEl = document.getElementById('qrImportError');
        if (errEl) {
          errEl.textContent = 'Please drop an image file (PNG, JPEG, …).';
          errEl.style.display = 'block';
        }
        return;
      }
      processImageBlob(f);
    });

    zone.addEventListener('click', function () {
      fileInput.click();
    });

    fileInput.addEventListener('change', function () {
      if (!fileInput.files || fileInput.files.length === 0) {
        return;
      }
      processImageBlob(fileInput.files[0]);
      fileInput.value = '';
    });

    document.addEventListener('paste', function (e) {
      if (!e.clipboardData || !e.clipboardData.items) {
        return;
      }
      var found = false;
      for (var i = 0; i < e.clipboardData.items.length; i++) {
        var item = e.clipboardData.items[i];
        if (item.type && item.type.indexOf('image') === 0) {
          var blob = item.getAsFile();
          if (blob) {
            found = true;
            processImageBlob(blob);
          }
          break;
        }
      }
      if (found) {
        e.preventDefault();
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
