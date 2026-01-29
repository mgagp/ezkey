# Ezkey CLI Test Container
# This file will be in UTF-8 without BOM.

FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      bash \
      ca-certificates \
      curl \
      git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/ezkey-cli

COPY ezkey-cli-python/ /opt/ezkey-cli/

RUN python -m pip install --upgrade pip \
    && python -m pip install --no-cache-dir -r requirements.txt \
    && python -m pip install --no-cache-dir pexpect \
    && python -m pip install --no-cache-dir -e .

RUN mkdir -p /work /root/.ezkey \
    && chmod -R 0775 /work /root/.ezkey

# Configure bash for better interactive experience
RUN echo 'set -o emacs' >> /root/.bashrc \
    && echo 'set -o history' >> /root/.bashrc \
    && echo 'export HISTFILE=/root/.bash_history' >> /root/.bashrc \
    && echo 'export HISTSIZE=1000' >> /root/.bashrc \
    && echo 'export HISTFILESIZE=2000' >> /root/.bashrc \
    && touch /root/.bash_history

WORKDIR /work

CMD ["tail", "-f", "/dev/null"]
