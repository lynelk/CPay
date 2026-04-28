/**
 * iOS-inspired design tokens for CPay.
 *
 * Colours are drawn from Apple's Human Interface Guidelines colour palette.
 * Typography uses the iOS system font stack (SF Pro / -apple-system).
 */
const iosTheme = {
  // Colour palette
  colors: {
    // System Blues
    blue: '#007AFF',
    lightBlue: '#5AC8FA',
    teal: '#32ADE6',

    // Status colours
    green: '#34C759',
    orange: '#FF9500',
    red: '#FF3B30',
    yellow: '#FFCC00',
    purple: '#AF52DE',
    pink: '#FF2D55',

    // Greyscale
    white: '#FFFFFF',
    systemGray6: '#F2F2F7',
    systemGray5: '#E5E5EA',
    systemGray4: '#D1D1D6',
    systemGray3: '#C7C7CC',
    systemGray2: '#AEAEB2',
    systemGray: '#8E8E93',
    label: '#000000',
    secondaryLabel: 'rgba(60,60,67,0.6)',
    tertiaryLabel: 'rgba(60,60,67,0.3)',

    // Backgrounds
    systemBackground: '#FFFFFF',
    secondarySystemBackground: '#F2F2F7',
    tertiarySystemBackground: '#FFFFFF',
    groupedBackground: '#F2F2F7',
    separator: 'rgba(60,60,67,0.29)',
  },

  // Typography
  typography: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", Arial, sans-serif',
    fontFamilyDisplay: '-apple-system, BlinkMacSystemFont, "SF Pro Display", "Helvetica Neue", Arial, sans-serif',
    largeTitle: { fontSize: 34, fontWeight: '700', letterSpacing: 0.37 },
    title1:     { fontSize: 28, fontWeight: '700', letterSpacing: 0.36 },
    title2:     { fontSize: 22, fontWeight: '700', letterSpacing: 0.35 },
    title3:     { fontSize: 20, fontWeight: '600', letterSpacing: 0.38 },
    headline:   { fontSize: 17, fontWeight: '600', letterSpacing: -0.41 },
    body:       { fontSize: 17, fontWeight: '400', letterSpacing: -0.41 },
    callout:    { fontSize: 16, fontWeight: '400', letterSpacing: -0.32 },
    subhead:    { fontSize: 15, fontWeight: '400', letterSpacing: -0.24 },
    footnote:   { fontSize: 13, fontWeight: '400', letterSpacing: -0.08 },
    caption1:   { fontSize: 12, fontWeight: '400', letterSpacing: 0 },
    caption2:   { fontSize: 11, fontWeight: '400', letterSpacing: 0.07 },
  },

  // Spacing (4-pt grid)
  spacing: {
    xs:  4,
    sm:  8,
    md:  16,
    lg:  24,
    xl:  32,
    xxl: 48,
  },

  // Border radii
  radii: {
    sm:   6,
    md:   10,
    lg:   14,
    xl:   20,
    pill: 9999,
  },

  // Shadows (iOS-style)
  shadows: {
    card: '0 2px 12px rgba(0,0,0,0.08)',
    elevated: '0 4px 20px rgba(0,0,0,0.12)',
    modal: '0 8px 40px rgba(0,0,0,0.18)',
  },
};

module.exports = iosTheme;
