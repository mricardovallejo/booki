/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        booki: {
          bg: '#0b0b0f',
          surface: '#16161d',
          card: '#1f1f2a',
          'card-hover': '#272736',
          text: '#ffffff',
          muted: '#a1a1aa',
          accent: '#e63946',
          'accent-hover': '#ff4d5a'
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
