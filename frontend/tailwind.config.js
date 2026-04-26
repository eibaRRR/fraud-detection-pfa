/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'monospace'],
      },
      colors: {
        // Palette dark-first inspirée des dashboards SOC
        bg: {
          DEFAULT: '#0b0f17',
          panel: '#121826',
          elev: '#1a2233',
        },
        border: {
          DEFAULT: '#1f2937',
          strong: '#2d3748',
        },
        accent: {
          DEFAULT: '#3b82f6',
          hover: '#60a5fa',
        },
        ok: '#10b981',
        warn: '#f59e0b',
        danger: '#ef4444',
      },
      animation: {
        'pulse-soft': 'pulseSoft 2s ease-in-out infinite',
      },
      keyframes: {
        pulseSoft: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.55' },
        },
      },
    },
  },
  plugins: [],
};
