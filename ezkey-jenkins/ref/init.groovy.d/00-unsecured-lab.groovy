/*
 * Lab-only security posture for M0 (#972).
 * Unsecured so smoke scripts and Marc can open the UI without a setup wizard.
 * Replace with a proper security realm before any auth-plugin work (M2+).
 */
import hudson.security.AuthorizationStrategy
import hudson.security.SecurityRealm
import jenkins.model.Jenkins

def instance = Jenkins.get()
instance.setSecurityRealm(SecurityRealm.NO_AUTHENTICATION)
instance.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED)
instance.save()
println 'ezkey-jenkins M0: unsecured lab realm applied'
