/*
 * M2 interim security before ezkey-lab-auth is installed (#972).
 * HudsonPrivateSecurityRealm + break-glass admin so the instance is never unsecured.
 * After ./scripts/configure-mock-auth.sh, EzkeyMockSecurityRealm takes over with the
 * same break-glass username/password.
 */
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import hudson.security.HudsonPrivateSecurityRealm
import hudson.security.SecurityRealm
import jenkins.model.Jenkins

def instance = Jenkins.get()
def realm = instance.getSecurityRealm()

// Do not clobber Ezkey mock realm once configure-mock-auth.sh has applied it.
if (realm != null && realm.class.name.contains('EzkeyMockSecurityRealm')) {
    println 'ezkey-jenkins M2: EzkeyMockSecurityRealm already active — skip interim bootstrap'
    return
}

def adminUser = System.getenv('EZKEY_JENKINS_ADMIN_USER') ?: 'admin'
def adminPass = System.getenv('EZKEY_JENKINS_ADMIN_PASSWORD') ?: 'ezkey-lab-admin'

def privateRealm = new HudsonPrivateSecurityRealm(false)
instance.setSecurityRealm(privateRealm)

def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
instance.setAuthorizationStrategy(strategy)

if (privateRealm.getUser(adminUser) == null) {
    privateRealm.createAccount(adminUser, adminPass)
    println "ezkey-jenkins M2: created break-glass user '${adminUser}'"
} else {
    println "ezkey-jenkins M2: break-glass user '${adminUser}' already exists"
}

instance.save()
println 'ezkey-jenkins M2: interim HudsonPrivateSecurityRealm applied (break-glass ready)'
