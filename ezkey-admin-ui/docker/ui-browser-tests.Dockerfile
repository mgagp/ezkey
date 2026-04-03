FROM mcr.microsoft.com/playwright:v1.59.1-noble

WORKDIR /work

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

CMD ["bash"]
