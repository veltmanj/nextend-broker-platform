FROM node:24-trixie-slim

WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends openssl \
	&& rm -rf /var/lib/apt/lists/*

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY README.md ./README.md
COPY src ./src

EXPOSE 7443/udp 9090

CMD ["node", "src/server.mjs"]
