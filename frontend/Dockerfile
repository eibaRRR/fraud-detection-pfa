# ----- Stage build -----
FROM node:20-alpine AS build

WORKDIR /app
COPY package.json ./
RUN npm install

COPY . .
ARG VITE_WS_URL=ws://localhost:8080/alertes
ENV VITE_WS_URL=$VITE_WS_URL
RUN npm run build

# ----- Stage runtime (Nginx) -----
FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
