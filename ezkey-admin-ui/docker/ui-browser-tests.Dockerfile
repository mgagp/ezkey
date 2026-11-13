# Tag must match @playwright/test resolved in package-lock.json (currently 1.64.1).
FROM mcr.microsoft.com/playwright:v1.65.0-noble

WORKDIR /work

ENV NPM_CONFIG_UPDATE_NOTIFIER=false \
    NPM_CONFIG_FUND=false

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

CMD ["bash"]
