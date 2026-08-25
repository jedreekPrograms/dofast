# syntax=docker/dockerfile:1.7

FROM node:26.7.0-alpine AS build
WORKDIR /workspace

COPY apps/web/package.json apps/web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY apps/web/ ./
RUN npm run build

FROM nginx:1.29-alpine AS runtime
COPY infra/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80
