/** @type {import('tailwindcss').Config} */
module.exports = {
  prefix: 'tw-',
  content: ['./src/**/*.{js,jsx,ts,tsx}', './public/index.html'],
  corePlugins: { preflight: false },
  theme: {
    extend: {
      colors: {
        cpay: {
          blue:        '#007AFF',
          lightBlue:   '#5AC8FA',
          teal:        '#32ADE6',
          green:       '#34C759',
          orange:      '#FF9500',
          red:         '#FF3B30',
          yellow:      '#FFCC00',
          purple:      '#AF52DE',
          pink:        '#FF2D55',
          gray6:       '#F2F2F7',
          gray5:       '#E5E5EA',
          gray4:       '#D1D1D6',
          gray3:       '#C7C7CC',
          gray2:       '#AEAEB2',
          gray:        '#8E8E93',
          label:       '#000000',
          bg:          '#FFFFFF',
          bgSecondary: '#F2F2F7',
          separator:   'rgba(60,60,67,0.29)',
        },
      },
      fontFamily: {
        ios: ['-apple-system','BlinkMacSystemFont','"SF Pro Text"','"Helvetica Neue"','Arial','sans-serif'],
      },
      spacing: {
        'ios-xs': '4px', 'ios-sm': '8px', 'ios-md': '16px',
        'ios-lg': '24px', 'ios-xl': '32px', 'ios-2xl': '48px',
      },
      borderRadius: {
        'ios-sm': '6px', 'ios-md': '10px', 'ios-lg': '14px',
        'ios-xl': '20px', 'ios-pill': '9999px',
      },
      boxShadow: {
        'ios-sm':   '0 1px 3px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.08)',
        'ios-md':   '0 4px 6px rgba(0,0,0,0.07), 0 2px 4px rgba(0,0,0,0.06)',
        'ios-lg':   '0 10px 15px rgba(0,0,0,0.10), 0 4px 6px rgba(0,0,0,0.05)',
        'ios-card': '0 2px 8px rgba(0,0,0,0.12)',
      },
      screens: { xs: '375px' },
    },
  },
  plugins: [],
};
