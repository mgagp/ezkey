package org.ezkey.jenkins.auth;

import hudson.Extension;
import hudson.model.PageDecorator;

/**
 * Adds a link from the Jenkins login page to the Ezkey MFA confirm flow.
 *
 * @since 2026
 */
@Extension
public class EzkeyLabLoginLinkDecorator extends PageDecorator {
    // footer.jelly under this class package renders the link when SecurityRealm matches.
}
