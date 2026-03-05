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

# Create non-root user and set up home/work directories with correct ownership
RUN useradd --system --create-home --shell /bin/bash --uid 1000 ezkey \
    && mkdir -p /work /home/ezkey/.ezkey \
    && chown -R ezkey:ezkey /work /home/ezkey/.ezkey /opt/ezkey-cli \
    && chmod 0775 /work /home/ezkey/.ezkey

COPY docker/cli-test/entrypoint.sh /usr/local/bin/ezkey-cli-entrypoint.sh
RUN chmod +x /usr/local/bin/ezkey-cli-entrypoint.sh

# Configure bash for better interactive experience
RUN echo 'set -o emacs' >> /home/ezkey/.bashrc \
    && echo 'set -o history' >> /home/ezkey/.bashrc \
    && echo 'export HISTFILE=/home/ezkey/.bash_history' >> /home/ezkey/.bashrc \
    && echo 'export HISTSIZE=1000' >> /home/ezkey/.bashrc \
    && echo 'export HISTFILESIZE=2000' >> /home/ezkey/.bashrc \
    && touch /home/ezkey/.bash_history \
    && chown -R ezkey:ezkey /home/ezkey

USER ezkey:ezkey

WORKDIR /work

ENTRYPOINT ["/usr/local/bin/ezkey-cli-entrypoint.sh"]
CMD ["tail", "-f", "/dev/null"]
