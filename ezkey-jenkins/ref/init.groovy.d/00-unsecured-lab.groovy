/*
 * M0 used an unsecured realm. M2 replaces that posture.
 * This file is kept as a no-op so existing volumes that already copied it do not
 * resurrect NO_AUTHENTICATION. Active bootstrap: 10-secured-breakglass.groovy and
 * scripts/configure-mock-auth.sh (Ezkey mock realm after plugin install).
 */
println 'ezkey-jenkins: 00-unsecured-lab.groovy is intentionally a no-op (M2+)'
