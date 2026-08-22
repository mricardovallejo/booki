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
        sans: ['Inter', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        glow: '0 0 30px rgba(230, 57, 70, 0.15)'
      }
    }
  },
  plugins: []
};
