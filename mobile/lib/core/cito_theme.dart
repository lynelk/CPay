import 'package:flutter/material.dart';

abstract final class CitoColours {
  static const primary = Color(0xFF0F766E);
  static const navy = Color(0xFF0B1F3A);
  static const accent = Color(0xFFF59E0B);
  static const canvas = Color(0xFFF8FAFC);
  static const text = Color(0xFF111827);
  static const success = Color(0xFF15803D);
  static const warning = Color(0xFFB45309);
  static const danger = Color(0xFFB91C1C);
}

ThemeData buildCitoTheme(Brightness brightness) {
  final isDark = brightness == Brightness.dark;
  final scheme = ColorScheme.fromSeed(
    seedColor: CitoColours.primary,
    brightness: brightness,
    primary: CitoColours.primary,
    secondary: CitoColours.accent,
    surface: isDark ? const Color(0xFF111827) : Colors.white,
    error: CitoColours.danger,
  );
  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: isDark ? const Color(0xFF07111F) : CitoColours.canvas,
    appBarTheme: AppBarTheme(
      backgroundColor: isDark ? const Color(0xFF07111F) : CitoColours.canvas,
      foregroundColor: isDark ? Colors.white : CitoColours.navy,
      elevation: 0,
      scrolledUnderElevation: 0,
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      margin: EdgeInsets.zero,
      color: isDark ? const Color(0xFF111827) : Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: BorderSide(color: isDark ? const Color(0xFF263244) : const Color(0xFFE2E8F0)),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: isDark ? const Color(0xFF111827) : Colors.white,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide(color: isDark ? const Color(0xFF334155) : const Color(0xFFCBD5E1)),
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      indicatorColor: CitoColours.primary.withValues(alpha: 0.15),
      labelTextStyle: WidgetStateProperty.resolveWith(
        (states) => TextStyle(
          color: states.contains(WidgetState.selected) ? CitoColours.primary : scheme.onSurfaceVariant,
          fontSize: 12,
          fontWeight: states.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500,
        ),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(48, 52),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
    ),
  );
}
