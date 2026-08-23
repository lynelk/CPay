FROM node:20-bookworm AS build
WORKDIR /verify/repo

COPY . .
WORKDIR /verify/repo/clientside
RUN npm ci --legacy-peer-deps

ENV CI=true \
    NODE_OPTIONS="--openssl-legacy-provider"

RUN npm run lint --if-present
RUN npm test
RUN npm run typecheck
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /verify/repo/clientside/build /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
