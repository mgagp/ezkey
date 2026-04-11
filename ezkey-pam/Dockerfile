# ==================================================
# Dockerfile - Environnement de test PAM Ezkey
# ==================================================

FROM rockylinux/rockylinux:10

# Métadonnées
LABEL maintainer="Ezkey Development Team"
LABEL description="Environment de test pour le module PAM Ezkey (Rocky Linux 10)"
LABEL version="1.1.0"

# Variables d'environnement de base
ENV CONTAINER=docker
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Credentials M2M API (injecter via docker-compose ou docker run -e)
ENV EZKEY_M2M_API_URL=""
ENV EZKEY_INTEGRATION_KEY=""
ENV EZKEY_SECRET_KEY=""

# Installation des packages en une seule commande (réduit les layers)
RUN dnf update -y && \
    dnf install -y epel-release && \
    dnf install -y \
        openssh-server \
        openssh-clients \
        pam-devel \
        gcc \
        make \
        rsyslog \
        sudo \
        passwd \
        which \
        procps-ng \
        net-tools \
        wget \
        vim \
        less \
        grep \
        sed \
        gawk \
        libcurl-devel \
        cjson-devel && \
    dnf clean all
    
# Configuration SSH
RUN ssh-keygen -A && \
    mkdir -p /var/run/sshd

# Création d'un utilisateur de test
RUN useradd -m -s /bin/bash testuser && \
    echo "testuser:password123" | chpasswd && \
    usermod -aG wheel testuser

# Configuration sudo sans mot de passe pour testuser
RUN echo "testuser ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers

# Configuration SSH pour permettre les connexions par mot de passe
RUN sed -i 's/#PermitRootLogin yes/PermitRootLogin yes/' /etc/ssh/sshd_config && \
    sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config && \
    sed -i 's/PasswordAuthentication no/PasswordAuthentication yes/' /etc/ssh/sshd_config && \
    echo "UsePAM yes" >> /etc/ssh/sshd_config && \
    echo "ChallengeResponseAuthentication yes" >> /etc/ssh/sshd_config && \
    echo "KbdInteractiveAuthentication yes" >> /etc/ssh/sshd_config

# Copie des sources PAM dans le conteneur
COPY src/ /opt/pam-ezkey/src/
COPY config/ /opt/pam-ezkey/config/
COPY test/ /opt/pam-ezkey/test/
COPY Makefile /opt/pam-ezkey/
COPY install.sh /opt/pam-ezkey/
COPY README.md /opt/pam-ezkey/

# Build et installation du module PAM
WORKDIR /opt/pam-ezkey
RUN make clean && make all

# Installation manuelle du module (sans utiliser install.sh dans le build)
RUN cp build/pam_ezkey.so /lib64/security/ && \
    chmod 755 /lib64/security/pam_ezkey.so && \
    cp config/pam_ezkey.conf /etc/security/ && \
    chmod 644 /etc/security/pam_ezkey.conf

# Configuration PAM pour SSH
COPY sshd /etc/pam.d/sshd

# Script de démarrage
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Configuration rsyslog pour les logs PAM
RUN echo "authpriv.*    /var/log/secure" >> /etc/rsyslog.conf

# Création des répertoires de logs
RUN mkdir -p /var/log && \
    touch /var/log/secure && \
    chmod 640 /var/log/secure

# Permissions sur le répertoire de travail
RUN chown -R testuser:testuser /opt/pam-ezkey && \
    chmod +x /opt/pam-ezkey/test/test_pam.sh && \
    chmod +x /opt/pam-ezkey/install.sh

# Exposition du port SSH
EXPOSE 22

# Point d'entrée
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["/usr/sbin/sshd", "-D"]
