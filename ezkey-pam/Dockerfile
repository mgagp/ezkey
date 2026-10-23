# Ezkey PAM module — Rocky Linux 10 Docker build (compile) + SSH demo runtime.
# All compilation happens in the builder stage. The runtime image is an
# SSH virtual machine that loads pam_ezkey.so at login time.
#
# Amazon Linux 2023 targets live in Dockerfile.al2023 (separate so classic
# docker build --target does not walk Rocky stages).

ARG ROCKY_IMAGE=rockylinux/rockylinux:10

# ---------------------------------------------------------------------------
# Builder — Rocky Linux 10 (lab demo)
# ---------------------------------------------------------------------------
FROM ${ROCKY_IMAGE} AS builder

LABEL org.ezkey.component="pam-builder"
LABEL org.ezkey.version="2.2.6"

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Pin EPEL to Fedora CDN: metalink can hand CI a campus mirror with broken TLS
# (repomd SSL / checksum flake). Rocky 10 only — hardcode path (no $releasever).
RUN dnf -y install epel-release ca-certificates \
    && printf '%s\n' \
        '[epel]' \
        'name=Extra Packages for Enterprise Linux 10 - x86_64' \
        'baseurl=https://download.fedoraproject.org/pub/epel/10/Everything/x86_64/' \
        'gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-EPEL-10' \
        'gpgcheck=1' \
        'repo_gpgcheck=0' \
        'metadata_expire=24h' \
        'enabled=1' \
        > /etc/yum.repos.d/epel.repo \
    && dnf -y install gcc make pam-devel libcurl-devel cjson-devel \
    && dnf clean all

WORKDIR /src
COPY src/ /src/src/
COPY Makefile /src/Makefile
COPY test/ /src/test/
# Sample conf for check-source (#995 challenge_requested=true guard).
COPY config/ /src/config/
# Shared MaxAuthTries helper + awk + unit test (Patrick #908 / #934).
COPY install.sh /src/install.sh
COPY docker-entrypoint.sh /src/docker-entrypoint.sh
COPY sshd_config /src/sshd_config
COPY scripts/sshd-warn-global-maxauthtries-1.awk /src/scripts/sshd-warn-global-maxauthtries-1.awk
COPY scripts/sshd-warn-global-maxauthtries-1.sh /src/scripts/sshd-warn-global-maxauthtries-1.sh
COPY scripts/test-install-sshd-warn.sh /src/scripts/test-install-sshd-warn.sh

RUN make clean && make all test \
    && test -f /src/build/pam_ezkey.so \
    && test -f /src/build/test_no_conv_client

# ---------------------------------------------------------------------------
# Runtime — Rocky Linux SSH demo VM
# ---------------------------------------------------------------------------
FROM ${ROCKY_IMAGE} AS runtime

LABEL org.ezkey.component="pam-ssh-demo"
LABEL org.ezkey.version="2.2.6"
LABEL description="Rocky Linux SSH demo VM authenticated by Ezkey PAM"

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV EZKEY_INTEGRATION_API_URL=http://integration-api:7080
ENV EZKEY_SSH_USER=testuser
ENV EZKEY_WAIT_TIMEOUT=90
ENV EZKEY_DEBUG=true

# Same EPEL CDN pin as builder (cjson is the only EPEL package here).
RUN dnf -y install epel-release ca-certificates \
    && printf '%s\n' \
        '[epel]' \
        'name=Extra Packages for Enterprise Linux 10 - x86_64' \
        'baseurl=https://download.fedoraproject.org/pub/epel/10/Everything/x86_64/' \
        'gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-EPEL-10' \
        'gpgcheck=1' \
        'repo_gpgcheck=0' \
        'metadata_expire=24h' \
        'enabled=1' \
        > /etc/yum.repos.d/epel.repo \
    && dnf -y install --allowerasing \
        openssh-server \
        openssh-clients \
        rsyslog \
        passwd \
        procps-ng \
        iproute \
        libcurl \
        cjson \
        python3 \
    && dnf clean all

RUN ssh-keygen -A \
    && mkdir -p /var/run/sshd /run/sshd

# Demo Linux account. userIdentifier on the Ezkey enrollment must match.
ARG EZKEY_SSH_USER=testuser
RUN useradd -m -s /bin/bash "${EZKEY_SSH_USER}" \
    && usermod -aG wheel "${EZKEY_SSH_USER}" || true

COPY --from=builder /src/build/pam_ezkey.so /lib64/security/pam_ezkey.so
COPY --from=builder /src/build/test_no_conv_client /opt/pam-ezkey/test/test_no_conv_client
RUN chmod 755 /lib64/security/pam_ezkey.so \
    && chmod 755 /opt/pam-ezkey/test/test_no_conv_client

COPY config/pam_ezkey.conf /etc/security/pam_ezkey.conf
RUN chmod 600 /etc/security/pam_ezkey.conf

COPY sshd /etc/pam.d/sshd
COPY sshd.chal-noconv /etc/pam.d/ezkey-chal-noconv
COPY sshd_config /etc/ssh/sshd_config
COPY sshd_config.f9-multitry /opt/pam-ezkey/sshd_config.f9-multitry
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY test/test_pam.sh /opt/pam-ezkey/test/test_pam.sh
COPY test/test_conf_security.sh /opt/pam-ezkey/test/test_conf_security.sh
COPY scripts/integration-api-stub.py /opt/pam-ezkey/scripts/integration-api-stub.py
COPY scripts/test-f9-retry.sh /opt/pam-ezkey/scripts/test-f9-retry.sh
COPY scripts/sshd-warn-global-maxauthtries-1.awk /opt/pam-ezkey/scripts/sshd-warn-global-maxauthtries-1.awk
COPY scripts/sshd-warn-global-maxauthtries-1.sh /opt/pam-ezkey/scripts/sshd-warn-global-maxauthtries-1.sh
# rsyslog authpriv routing is fixed at runtime by docker-entrypoint.sh (imuxsock
# SysSock.Use=on; no journald in the container). Stock conf already has authpriv.*.
RUN chmod +x /usr/local/bin/docker-entrypoint.sh /opt/pam-ezkey/test/test_pam.sh \
        /opt/pam-ezkey/test/test_conf_security.sh \
        /opt/pam-ezkey/scripts/integration-api-stub.py \
        /opt/pam-ezkey/scripts/test-f9-retry.sh \
    && mkdir -p /var/log /var/log/ezkey-pam \
    && chmod 700 /var/log/ezkey-pam \
    && touch /var/log/secure \
    && chmod 640 /var/log/secure \
    && chown root:root /etc/security/pam_ezkey.conf

EXPOSE 22

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/usr/sbin/sshd", "-D", "-e"]
