# syntax=docker/dockerfile:1.7

FROM node:24.19.0-alpine AS build
WORKDIR /workspace

ARG VITE_GOOGLE_AUTH_CLIENT_ID=
ARG VITE_GOOGLE_MAPS_BROWSER_KEY=
ARG VITE_GOOGLE_MAPS_MAP_ID=DEMO_MAP_ID
ENV VITE_GOOGLE_AUTH_CLIENT_ID=${VITE_GOOGLE_AUTH_CLIENT_ID}
ENV VITE_GOOGLE_MAPS_BROWSER_KEY=${VITE_GOOGLE_MAPS_BROWSER_KEY}
ENV VITE_GOOGLE_MAPS_MAP_ID=${VITE_GOOGLE_MAPS_MAP_ID}

COPY apps/web/package.json apps/web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY apps/web/ ./
RUN npm run build

FROM nginx:1.29-alpine AS runtime
COPY infra/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80