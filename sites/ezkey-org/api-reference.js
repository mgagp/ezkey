/**
 * Bootstrap for the public API reference pages (Admin, Auth, Integration; EN and FR).
 *
 * External on purpose: Content-Security-Policy for this site does not allow
 * script-src 'unsafe-inline'. Each reference page points here with data-spec-url,
 * data-api-name, and data-lang. ReDoc CE stays the pinned bundle
 * https://cdn.redoc.ly/redoc/v2.5.2/bundles/redoc.standalone.js.
 *
 * style-src still allows 'unsafe-inline' (page <style> blocks, style attributes,
 * and styles ReDoc injects). See _headers.
 */
(function () {
  "use strict";

  var current = document.currentScript;
  if (!current) {
    return;
  }

  var specUrl = current.getAttribute("data-spec-url");
  var apiName = current.getAttribute("data-api-name");
  var french = current.getAttribute("data-lang") === "fr";
  var statusEl = document.getElementById("spec-status");
  var redocEl = document.getElementById("redoc-container");

  var copy = french
    ? {
        unavailableTitle: "Référence indisponible",
        expectedPrefix: "Actif attendu : ",
        loadingPrefix: "Chargement de l’actif OpenAPI depuis ",
        httpErrorMiddle: " lors du chargement de ",
        loadedPrefix: "Actif OpenAPI chargé depuis ",
        failurePrefix: "Impossible de charger la référence " + apiName + ". ",
        redocMissing: "ReDoc CE n’a pas pu être chargé."
      }
    : {
        unavailableTitle: "Reference unavailable",
        expectedPrefix: "Expected asset: ",
        loadingPrefix: "Loading OpenAPI asset from ",
        httpErrorMiddle: " while loading ",
        loadedPrefix: "Loaded OpenAPI asset from ",
        failurePrefix: "Could not load the " + apiName + " reference. ",
        redocMissing: "ReDoc CE did not load."
      };

  function showFailure(message) {
    statusEl.textContent = message;
    statusEl.classList.add("error");

    // textContent, not innerHTML: message includes error.message, and specUrl
    // and the title come from the page. CodeQL treats those as untrusted.
    var fallback = document.createElement("div");
    fallback.className = "redoc-fallback";

    var heading = document.createElement("h2");
    heading.textContent = copy.unavailableTitle;

    var detail = document.createElement("p");
    detail.textContent = message;

    var expected = document.createElement("p");
    expected.textContent = copy.expectedPrefix;
    var code = document.createElement("code");
    code.textContent = specUrl == null ? "" : specUrl;
    expected.appendChild(code);

    fallback.appendChild(heading);
    fallback.appendChild(detail);
    fallback.appendChild(expected);
    redocEl.replaceChildren(fallback);
  }

  async function loadReference() {
    statusEl.textContent = copy.loadingPrefix + specUrl + "...";
    try {
      var response = await fetch(specUrl, { cache: "no-store" });
      if (!response.ok) {
        throw new Error("HTTP " + response.status + copy.httpErrorMiddle + specUrl);
      }

      var spec = await response.json();
      statusEl.textContent = copy.loadedPrefix + specUrl;

      window.Redoc.init(
        spec,
        {
          scrollYOffset: 12,
          hideDownloadButton: false,
          nativeScrollbars: true,
          theme: {
            colors: {
              primary: {
                main: "#ea580c"
              }
            },
            typography: {
              fontFamily:
                '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, sans-serif',
              headings: {
                fontFamily:
                  '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, sans-serif'
              }
            }
          }
        },
        redocEl
      );
    } catch (error) {
      showFailure(copy.failurePrefix + error.message);
    }
  }

  if (window.Redoc && window.Redoc.init) {
    loadReference();
  } else {
    showFailure(copy.redocMissing);
  }
})();
