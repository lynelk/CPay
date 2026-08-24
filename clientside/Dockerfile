# Production frontend image for Railway.
# Vite builds the Cito SPA into /app/build; nginx serves it and proxies
# same-origin API requests to the CPay backend over Railway private networking.
FROM node:20.19.0-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run typecheck && npm run build

FROM nginx:1.27-alpine

COPY --from=build /app/build /usr/share/nginx/html
COPY default.conf.template /etc/nginx/templates/default.conf.template

# Railway routes to the PORT environment variable. We standardize the frontend
# on 8080 while still letting Railway override it when required.
ENV PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider "http://127.0.0.1:${PORT}/healthz" || exit 1

CMD ["nginx", "-g", "daemon off;"]
