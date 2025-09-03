# ==================================================
# Dockerfile - Environnement de test PAM Ezkey
# ==================================================

FROM rockylinux/rockylinux:9.6

# Métadonnées
LABEL maintainer="Ezkey Development Team"
LABEL description="Environment de test pour le module PAM Ezkey"
LABEL version="1.0.0"

# Variables d'environnement
ENV CONTAINER=docker
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Installation des packages de base (un par un pour debug)
RUN dnf update -y && \
    dnf install -y openssh-server || exit 1 && \
    dnf install -y openssh-clients || exit 1 && \
    dnf install -y pam-devel || exit 1 && \
    dnf install -y gcc || exit 1 && \
    dnf install -y make || exit 1 && \
    dnf install -y rsyslog || exit 1 && \
    dnf install -y sudo || exit 1 && \
    dnf install -y passwd || exit 1 && \
    dnf install -y which || exit 1 && \
    dnf install -y procps-ng || exit 1 && \
    dnf install -y net-tools || exit 1 && \
    dnf install -y wget || exit 1 && \
    dnf install -y vim || exit 1 && \
    dnf install -y less || exit 1 && \
    dnf install -y grep || exit 1 && \
    dnf install -y sed || exit 1 && \
    dnf install -y gawk || exit 1 && \
    dnf install -y systemd || exit 1 && \
    dnf clean all

RUN dnf install -y libcurl-devel

RUN dnf install epel-release -y && dnf clean all

#RUN dnf install -y jansson jansson-devel
RUN dnf -y install cjson-devel
    
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

# Configuration PAM pour SSH (insertion après pam_sepermit.so)
#RUN cp /etc/pam.d/sshd /etc/pam.d/sshd.backup && \
#    sed -i '/^auth[[:space:]]\+required[[:space:]]\+pam_sepermit.so/a auth       required     pam_ezkey.so debug mock_delay=3' /etc/pam.d/sshd
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
