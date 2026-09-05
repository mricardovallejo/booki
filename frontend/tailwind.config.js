/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      // Values live once, as RGB channels, in src/index.css (:root). Referencing
      // them through rgb(... / <alpha-value>) keeps opacity modifiers working
      // (e.g. bg-booki-accent/15).
      colors: {
        booki: {
          bg: 'rgb(var(--color-bg) / <alpha-value>)',
          surface: 'rgb(var(--color-surface) / <alpha-value>)',
          card: 'rgb(var(--color-card) / <alpha-value>)',
          'card-hover': 'rgb(var(--color-card-hover) / <alpha-value>)',
          text: 'rgb(var(--color-text) / <alpha-value>)',
          muted: 'rgb(var(--color-muted) / <alpha-value>)',
          accent: 'rgb(var(--color-accent) / <alpha-value>)',
          'accent-hover': 'rgb(var(--color-accent-hover) / <alpha-value>)'
        }
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'system-ui', 'sans-serif'],
        // The "BooKI" wordmark only — not for running text.
        logo: ['Fontdiner Swanky', 'cursive'],
        // Short menu/tab/action labels only — Michroma is a wide, single-weight
        // display face that stops being legible past a couple of words.
        menu: ['Michroma', 'sans-serif']
      },
      boxShadow: {
        glow: '0 0 30px rgba(230, 57, 70, 0.15)'
      }
    }
  },
  plugins: []
};
