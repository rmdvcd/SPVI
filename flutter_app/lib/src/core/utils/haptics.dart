import 'package:flutter/services.dart';

/// Utilidades de feedback háptico siguiendo Apple Human Interface Guidelines
class SpviHaptics {
  SpviHaptics._();

  /// Tap suave para botones, cambios de pestaña y selección
  static Future<void> lightImpact() async {
    await HapticFeedback.lightImpact();
  }

  /// Impacto medio para confirmaciones de acción o toggle switches
  static Future<void> mediumImpact() async {
    await HapticFeedback.mediumImpact();
  }

  /// Impacto fuerte para acciones críticas (anulaciones, cierres de turno)
  static Future<void> heavyImpact() async {
    await HapticFeedback.heavyImpact();
  }

  /// Feedback de selección de rueda o picker
  static Future<void> selectionClick() async {
    await HapticFeedback.selectionClick();
  }
}
