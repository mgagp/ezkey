# Ezkey PAM module — Docker build (compile) + SSH demo runtime.
# All compilation happens in the builder stage. The runtime image is an
# SSH virtual machine that loads pam_ezkey.so at login time.

ARG ROCKY_IMAGE=rockylinux/rockylinux:10

# ---------------------------------------------------------------------------
# Builder — dedicated compile image (same idea as the repo BuildKit pack)
# ---------------------------------------------------------------------------
FROM ${ROCKY_IMAGE} AS builder

LABEL org.ezkey.component="pam-builder"
LABEL org.ezkey.version="2.0.0"

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

RUN dnf -y install epel-release \
    && dnf -y install gcc make pam-devel libcurl-devel cjson-devel \
    && dnf clean all

WORKDIR /src
COPY src/ /src/src/
COPY Makefile /src/Makefile

RUN make clean && make all \
    && test -f /src/build/pam_ezkey.so

# ---------------------------------------------------------------------------
# Runtime — SSH demo VM with the compiled PAM module
# ---------------------------------------------------------------------------
FROM ${ROCKY_IMAGE} AS runtime

LABEL org.ezkey.component="pam-ssh-demo"
LABEL org.ezkey.version="2.0.0"
LABEL description="Rocky Linux SSH demo VM authenticated by Ezkey PAM"

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV EZKEY_INTEGRATION_API_URL=http://integration-api:7080
ENV EZKEY_SSH_USER=testuser
ENV EZKEY_WAIT_TIMEOUT=90

RUN dnf -y install epel-release \
    && dnf -y install --allowerasing \
        openssh-server \
        openssh-clients \
        rsyslog \
        passwd \
        procps-ng \
        iproute \
        libcurl \
        cjson \
    && dnf clean all

RUN ssh-keygen -A \
    && mkdir -p /var/run/sshd /run/sshd

# Demo Linux account. userIdentifier on the Ezkey enrollment must match.
ARG EZKEY_SSH_USER=testuser
RUN useradd -m -s /bin/bash "${EZKEY_SSH_USER}" \
    && usermod -aG wheel "${EZKEY_SSH_USER}" || true

COPY --from=builder /src/build/pam_ezkey.so /lib64/security/pam_ezkey.so
RUN chmod 755 /lib64/security/pam_ezkey.so

COPY config/pam_ezkey.conf /etc/security/pam_ezkey.conf
RUN chmod 600 /etc/security/pam_ezkey.conf

COPY sshd /etc/pam.d/sshd
COPY sshd_config /etc/ssh/sshd_config
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY test/test_pam.sh /opt/pam-ezkey/test/test_pam.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh /opt/pam-ezkey/test/test_pam.sh \
    && echo "authpriv.*    /var/log/secure" >> /etc/rsyslog.conf \
    && mkdir -p /var/log \
    && touch /var/log/secure \
    && chmod 640 /var/log/secure

EXPOSE 22

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/usr/sbin/sshd", "-D", "-e"]
