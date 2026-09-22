# Ezkey PAM module — Docker build (compile) + SSH demo runtime.
# All compilation happens in builder stages. Runtime images are SSH
# virtual machines that load pam_ezkey.so at login time.
#
# Targets:
#   builder / runtime           Rocky Linux 10 (lab demo)
#   builder-al2023 / runtime-al2023
#                               Amazon Linux 2023 (EXP1 ABI mirror)
#   smoke-al2023                Minimal AL2023 load check (no sshd)

ARG ROCKY_IMAGE=rockylinux/rockylinux:10
ARG AL2023_IMAGE=amazonlinux:2023
ARG CJSON_VERSION=1.7.18

# ---------------------------------------------------------------------------
# Builder — Rocky Linux 10 (lab demo)
# ---------------------------------------------------------------------------
FROM ${ROCKY_IMAGE} AS builder

LABEL org.ezkey.component="pam-builder"
LABEL org.ezkey.version="2.1.0"

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
# Runtime — Rocky Linux SSH demo VM
# ---------------------------------------------------------------------------
FROM ${ROCKY_IMAGE} AS runtime

LABEL org.ezkey.component="pam-ssh-demo"
LABEL org.ezkey.version="2.1.0"
LABEL description="Rocky Linux SSH demo VM authenticated by Ezkey PAM"

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV EZKEY_INTEGRATION_API_URL=http://integration-api:7080
ENV EZKEY_SSH_USER=testuser
ENV EZKEY_WAIT_TIMEOUT=90
ENV EZKEY_DEBUG=true

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

# ---------------------------------------------------------------------------
# Builder — Amazon Linux 2023 (cJSON built from pinned source; not in AL2023 repos)
# ---------------------------------------------------------------------------
FROM ${AL2023_IMAGE} AS builder-al2023

LABEL org.ezkey.component="pam-builder-al2023"
LABEL org.ezkey.version="2.1.0"

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

ARG CJSON_VERSION

RUN dnf -y install gcc make cmake pam-devel libcurl-devel \
    && dnf clean all \
    && curl -fsSL \
        "https://github.com/DaveGamble/cJSON/archive/refs/tags/v${CJSON_VERSION}.tar.gz" \
        | tar -xz -C /tmp \
    && cmake -S "/tmp/cJSON-${CJSON_VERSION}" -B /tmp/cjson-build \
        -DENABLE_CJSON_UTILS=OFF \
        -DBUILD_SHARED_LIBS=ON \
        -DCMAKE_INSTALL_PREFIX=/usr \
    && cmake --build /tmp/cjson-build --target install \
    && ldconfig \
    && rm -rf /tmp/cJSON-${CJSON_VERSION} /tmp/cjson-build

WORKDIR /src
COPY src/ /src/src/
COPY Makefile /src/Makefile

RUN make clean && make all \
    && test -f /src/build/pam_ezkey.so \
    && ldd /src/build/pam_ezkey.so

# ---------------------------------------------------------------------------
# Runtime — Amazon Linux 2023 SSH mirror (approve/reject/timeout parity)
# ---------------------------------------------------------------------------
FROM ${AL2023_IMAGE} AS runtime-al2023

LABEL org.ezkey.component="pam-ssh-demo-al2023"
LABEL org.ezkey.version="2.1.0"
LABEL description="Amazon Linux 2023 SSH mirror authenticated by Ezkey PAM"

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV EZKEY_INTEGRATION_API_URL=http://integration-api:7080
ENV EZKEY_SSH_USER=testuser
ENV EZKEY_WAIT_TIMEOUT=90
ENV EZKEY_DEBUG=true

RUN dnf -y install \
        openssh-server \
        openssh-clients \
        rsyslog \
        passwd \
        procps-ng \
        iproute \
        libcurl \
    && dnf clean all

# cJSON is not in AL2023 repos; reuse the shared library from the builder stage.
COPY --from=builder-al2023 /usr/lib64/libcjson.so* /usr/lib64/
RUN ldconfig

RUN ssh-keygen -A \
    && mkdir -p /var/run/sshd /run/sshd /usr/lib64/security /lib64/security

ARG EZKEY_SSH_USER=testuser
RUN useradd -m -s /bin/bash "${EZKEY_SSH_USER}" \
    && usermod -aG wheel "${EZKEY_SSH_USER}" || true

# Install path: AL2023 commonly uses /usr/lib64/security/
COPY --from=builder-al2023 /src/build/pam_ezkey.so /usr/lib64/security/pam_ezkey.so
RUN ln -sf /usr/lib64/security/pam_ezkey.so /lib64/security/pam_ezkey.so \
    && chmod 755 /usr/lib64/security/pam_ezkey.so

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

# ---------------------------------------------------------------------------
# Smoke — Amazon Linux 2023 module load check (no SSH, no Ezkey stack)
# ---------------------------------------------------------------------------
FROM ${AL2023_IMAGE} AS smoke-al2023

LABEL org.ezkey.component="pam-smoke-al2023"
LABEL org.ezkey.version="2.1.0"

RUN dnf -y install libcurl pam binutils gcc \
    && dnf clean all

COPY --from=builder-al2023 /usr/lib64/libcjson.so* /usr/lib64/
RUN ldconfig \
    && mkdir -p /usr/lib64/security

COPY --from=builder-al2023 /src/build/pam_ezkey.so /usr/lib64/security/pam_ezkey.so
COPY test/smoke_al2023.sh /opt/pam-ezkey/test/smoke_al2023.sh
RUN chmod +x /opt/pam-ezkey/test/smoke_al2023.sh \
    && chmod 755 /usr/lib64/security/pam_ezkey.so

CMD ["/opt/pam-ezkey/test/smoke_al2023.sh"]
