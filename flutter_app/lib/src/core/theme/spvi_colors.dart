import 'package:flutter/cupertino.dart';

/// Tokens de identidad visual de SPVI adaptados a iOS / Cupertino
class SpviColors {
  SpviColors._();

  // Marca SPVI original
  static const Color navy = Color(0xFF2B4FA3);
  static const Color sky = Color(0xFF66D1EF);
  static const Color teal = Color(0xFF478EA1);
  static const Color orange = Color(0xFFEE7F3B);
  static const Color amber = Color(0xFFF6B645);
  static const Color white = Color(0xFFFFFFFF);

  // Estados semánticos
  static const Color exito = Color(0xFF1E7E34);
  static const Color alerta = Color(0xFFF6B645);
  static const Color error = Color(0xFFBA1A1A);
  static const Color info = Color(0xFF2B4FA3);

  // Tema Claro iOS
  static const Color lightPrimary = Color(0xFF2B4FA3);
  static const Color lightPrimaryContainer = Color(0xFFDCE4FA);
  static const Color lightBackground = CupertinoColors.systemGroupedBackground;
  static const Color lightSurface = CupertinoColors.white;
  static const Color lightSecondarySurface = Color(0xFFF2F2F7);
  static const Color lightText = CupertinoColors.black;
  static const Color lightTextSecundario = CupertinoColors.secondaryLabel;
  static const Color lightBorder = CupertinoColors.separator;

  // Tema Oscuro iOS
  static const Color darkPrimary = Color(0xFFB3C5FF);
  static const Color darkPrimaryContainer = Color(0xFF1F3B80);
  static const Color darkBackground = CupertinoColors.black;
  static const Color darkSurface = Color(0xFF1C1C1E);
  static const Color darkSecondarySurface = Color(0xFF2C2C2E);
  static const Color darkText = CupertinoColors.white;
  static const Color darkTextSecundario = CupertinoColors.secondaryLabel;
  static const Color darkBorder = Color(0xFF38383A);

  static CupertinoThemeData lightTheme = const CupertinoThemeData(
    brightness: Brightness.light,
    primaryColor: navy,
    primaryContrastingColor: white,
    scaffoldBackgroundColor: CupertinoColors.systemGroupedBackground,
    barBackgroundColor: Color(0xCCFFFFFF),
    textTheme: CupertinoTextThemeData(
      primaryColor: navy,
      textStyle: TextStyle(
        fontFamily: '.SF Pro Text',
        fontSize: 16.0,
        color: CupertinoColors.label,
      ),
      navTitleTextStyle: TextStyle(
        fontFamily: '.SF Pro Display',
        fontSize: 17.0,
        fontWeight: FontWeight.w600,
        color: CupertinoColors.label,
      ),
      navLargeTitleTextStyle: TextStyle(
        fontFamily: '.SF Pro Display',
        fontSize: 34.0,
        fontWeight: FontWeight.bold,
        color: CupertinoColors.label,
        letterSpacing: -0.4,
      ),
    ),
  );

  static CupertinoThemeData darkTheme = const CupertinoThemeData(
    brightness: Brightness.dark,
    primaryColor: Color(0xFF66D1EF),
    primaryContrastingColor: Color(0xFF003544),
    scaffoldBackgroundColor: CupertinoColors.black,
    barBackgroundColor: Color(0xCC1C1C1E),
    textTheme: CupertinoTextThemeData(
      primaryColor: Color(0xFF66D1EF),
      textStyle: TextStyle(
        fontFamily: '.SF Pro Text',
        fontSize: 16.0,
        color: CupertinoColors.white,
      ),
      navTitleTextStyle: TextStyle(
        fontFamily: '.SF Pro Display',
        fontSize: 17.0,
        fontWeight: FontWeight.w600,
        color: CupertinoColors.white,
      ),
      navLargeTitleTextStyle: TextStyle(
        fontFamily: '.SF Pro Display',
        fontSize: 34.0,
        fontWeight: FontWeight.bold,
        color: CupertinoColors.white,
        letterSpacing: -0.4,
      ),
    ),
  );
}
