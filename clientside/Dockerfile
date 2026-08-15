# Stage 1: Build with Node.js
FROM node:20.19.0-alpine AS build
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source
COPY . .

# Build
RUN npm run build

# Stage 2: Runtime with Vite preview or nginx
FROM node:20.19.0-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 appuser && adduser -D -u 1000 -G appuser appuser

# Copy built files from build stage
COPY --from=build --chown=appuser:appuser /app/dist ./dist
COPY --from=build --chown=appuser:appuser /app/package*.json ./

# Install only production dependencies
RUN npm ci --only=production

EXPOSE 3000

USER appuser

# Use vite preview for development or production
CMD ["npm", "run", "preview", "--", "--host", "0.0.0.0", "--port", "3000"]
