import { existsSync, readFileSync } from 'fs';
import { resolve } from 'path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// getUserMedia (mic access, used by voice) only works in a "secure context":
// https://, or the special http://localhost exception. That exception does NOT
// cover a phone hitting your machine's LAN IP over plain http, so mobile voice
// testing needs real (if self-signed) TLS. See docs/local-dev.md "HTTPS for
// mobile testing" for how to (re)generate frontend/.certs/*.pem — gitignored,
// so this stays a no-op on machines without them (plain http, as before).
const certKeyPath = resolve(__dirname, '.certs/dev-key.pem');
const certPath = resolve(__dirname, '.certs/dev-cert.pem');
const https =
  existsSync(certKeyPath) && existsSync(certPath)
    ? { key: readFileSync(certKeyPath), cert: readFileSync(certPath) }
    : undefined;

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      devOptions: { enabled: true },
      manifest: {
        name: 'BooKI',
        short_name: 'BooKI',
        description: 'Lector de PDF con asistente contextual por texto y voz',
        theme_color: '#0f172a',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          { src: '/icon-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icon-512x512.png', sizes: '512x512', type: 'image/png' }
        ]
      }
    })
  ],
  server: {
    port: 5173,
    host: true,
    https,
    // Defaults to the real Spring backend. Set VITE_PROXY_TARGET=http://localhost:3001
    // to run the frontend against the Node mock backend instead.
    proxy: {
      '/api': process.env.VITE_PROXY_TARGET || 'http://localhost:8080'
    }
  }
});
