/**
 * CPay brand-aligned design tokens.
 *
 * Colours follow the CPay Brand Guidelines v2 palette while keeping
 * the compact spacing/radius scale used throughout the React client.
 */
const iosTheme = {
  // Colour palette
  colors: {
    // Brand accents
    blue: '#1198C4',
    lightBlue: '#5FBBD4',
    teal: '#1198C4',

    // Status colours
    green: '#34C759',
    orange: '#F3B01B',
    red: '#FF3B30',
    yellow: '#FFCC00',
    purple: '#AF52DE',
    pink: '#FF2D55',

    // Greyscale
    white: '#FFFFFF',
    systemGray6: '#F5F7FA',
    systemGray5: '#E4EAF0',
    systemGray4: '#D6E3EA',
    systemGray3: '#B8C6D1',
    systemGray2: '#8A98A8',
    systemGray: '#667085',
    label: '#163B5C',
    secondaryLabel: 'rgba(102,112,133,0.72)',
    tertiaryLabel: 'rgba(102,112,133,0.42)',

    // Backgrounds
    systemBackground: '#FFFFFF',
    secondarySystemBackground: '#F5F7FA',
    tertiarySystemBackground: '#FFFFFF',
    groupedBackground: '#F5F7FA',
    separator: 'rgba(102,112,133,0.24)',
  },

  // Typography
  typography: {
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif',
    fontFamilyDisplay: 'Montserrat, Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif',
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

export default iosTheme;
