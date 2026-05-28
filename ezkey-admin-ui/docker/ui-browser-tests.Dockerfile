FROM mcr.microsoft.com/playwright:v1.60.0-noble

WORKDIR /work

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

CMD ["bash"]
