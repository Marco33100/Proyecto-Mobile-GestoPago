import 'package:flutter/material.dart';

const ink = Color(0xFF173B2D);
const paper = Color(0xFFF6F0E5);
const paperHighlight = Color(0xFFFBF6EC);
const sage = Color(0xFFE7E7D8);
// 5.3:1 sobre `paper` y 4.8:1 sobre `sage` (WCAG AA para texto normal).
const terracotta = Color(0xFF96502E);
const warmSurface = Color(0xFFFFFCF6);
const mutedInk = Color(0xFF58645A);
// Bordes decorativos de tarjetas y separadores.
const outlineColor = Color(0xFFD6CCBA);
// Borde de campos editables: 3.6:1 sobre `paper` (WCAG 1.4.11, mínimo 3:1).
const fieldOutline = Color(0xFF857B69);
const successSurface = Color(0xFFE6EBDD);
const errorSurface = Color(0xFFF6E6DF);
const errorOnSurface = Color(0xFF7A2F27);

// Superficies oscuras (paneles de marca, tarjeta de saldo). Contrastes sobre
// `ink`: warmSurface 12:1, onInkMuted 7.7:1, terracottaOnInk 6.8:1.
const inkDeep = Color(0xFF0F2A20);
const inkRaised = Color(0xFF1F4A39);
const onInkMuted = Color(0xFFBFD0C4);
const terracottaOnInk = Color(0xFFF0B48F);
const terracottaSoft = Color(0xFFF0DFD1);

/// Escala de espaciado (múltiplos de 4).
abstract final class AppSpace {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double xxl = 32;
  static const double xxxl = 48;
}

/// Elevación: sombras cálidas y difusas, tintadas con `ink`.
abstract final class AppShadow {
  /// Tarjetas en reposo.
  static const card = [
    BoxShadow(color: Color(0x0F173B2D), blurRadius: 2, offset: Offset(0, 1)),
    BoxShadow(color: Color(0x0D173B2D), blurRadius: 16, offset: Offset(0, 6)),
  ];

  /// Tarjetas con el puntero encima o superficies destacadas.
  static const raised = [
    BoxShadow(color: Color(0x14173B2D), blurRadius: 4, offset: Offset(0, 2)),
    BoxShadow(color: Color(0x1F173B2D), blurRadius: 32, offset: Offset(0, 14)),
  ];
}

/// Duraciones y curvas compartidas. Respetan "reducir movimiento" en
/// [AppMotion.of].
abstract final class AppMotion {
  static const press = Duration(milliseconds: 120);
  static const hover = Duration(milliseconds: 180);
  static const page = Duration(milliseconds: 260);
  static const curve = Curves.easeOutCubic;

  /// Duración efectiva: cero si el sistema pide reducir animaciones.
  static Duration of(BuildContext context, Duration duration) =>
      MediaQuery.maybeDisableAnimationsOf(context) == true
      ? Duration.zero
      : duration;
}

/// Radios de esquina compartidos por toda la app.
abstract final class AppRadius {
  static const double sm = 12;
  static const double md = 18;
  static const double lg = 24;
}

/// Puntos de corte de Material 3 para diseño adaptable.
abstract final class Breakpoints {
  /// Por debajo: teléfono (barra de navegación inferior).
  static const double medium = 600;

  /// Desde aquí: pantallas anchas (tablet horizontal, escritorio, web).
  static const double expanded = 840;

  /// Desde aquí la barra lateral muestra sus etiquetas completas.
  static const double large = 1200;

  static bool isExpanded(BuildContext context) =>
      MediaQuery.sizeOf(context).width >= expanded;
}

/// Anchos máximos del contenido según el tipo de pantalla.
abstract final class ContentWidth {
  static const double auth = 440;
  static const double form = 600;
  static const double page = 680;
  static const double wide = 1080;
}

ThemeData gestopagoTheme() {
  final scheme =
      ColorScheme.fromSeed(
        seedColor: ink,
        brightness: Brightness.light,
      ).copyWith(
        primary: ink,
        onPrimary: const Color(0xFFFFFCF6),
        primaryContainer: const Color(0xFFDDE7D9),
        onPrimaryContainer: ink,
        secondary: terracotta,
        onSecondary: Colors.white,
        secondaryContainer: const Color(0xFFF0DFD1),
        onSecondaryContainer: const Color(0xFF4E2817),
        tertiary: const Color(0xFF777D61),
        onTertiary: Colors.white,
        surface: paper,
        onSurface: const Color(0xFF202A22),
        outline: const Color(0xFF8B927F),
        outlineVariant: outlineColor,
        error: const Color(0xFF9C3F36),
        onError: Colors.white,
      );
  final base = ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: paper,
  );
  final fieldBorder = OutlineInputBorder(
    borderRadius: BorderRadius.circular(AppRadius.md),
    borderSide: const BorderSide(color: fieldOutline),
  );
  final disabledFieldBorder = fieldBorder.copyWith(
    borderSide: const BorderSide(color: outlineColor),
  );

  return base.copyWith(
    // Foco visible para teclado (WCAG 2.4.7) y hover discreto solo con mouse.
    focusColor: ink.withValues(alpha: 0.12),
    hoverColor: ink.withValues(alpha: 0.05),
    textTheme: base.textTheme
        .apply(bodyColor: const Color(0xFF202A22), displayColor: ink)
        .copyWith(
          // Tracking negativo en tamaños grandes; el cuerpo queda en 0.
          displaySmall: const TextStyle(
            fontSize: 40,
            height: 1.05,
            fontWeight: FontWeight.w700,
            letterSpacing: -1.2,
            color: ink,
            fontFeatures: [FontFeature.tabularFigures()],
          ),
          headlineLarge: const TextStyle(
            fontSize: 34,
            height: 1.1,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.8,
            color: ink,
          ),
          headlineMedium: const TextStyle(
            fontSize: 30,
            height: 1.14,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.6,
            color: ink,
          ),
          headlineSmall: const TextStyle(
            fontSize: 25,
            height: 1.2,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.4,
            color: ink,
          ),
          titleLarge: const TextStyle(
            fontSize: 20,
            height: 1.25,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.2,
            color: ink,
          ),
          labelMedium: const TextStyle(
            fontSize: 12,
            height: 1.3,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.0,
            color: mutedInk,
          ),
          titleMedium: const TextStyle(
            fontSize: 16,
            height: 1.3,
            fontWeight: FontWeight.w600,
            color: ink,
          ),
          bodyLarge: const TextStyle(
            fontSize: 16,
            height: 1.5,
            color: Color(0xFF344138),
          ),
          bodyMedium: const TextStyle(
            fontSize: 15,
            height: 1.45,
            color: Color(0xFF47544A),
          ),
          labelLarge: const TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w700,
          ),
        ),
    appBarTheme: const AppBarTheme(
      backgroundColor: paper,
      foregroundColor: ink,
      surfaceTintColor: Colors.transparent,
      centerTitle: false,
      elevation: 0,
      titleTextStyle: TextStyle(
        color: ink,
        fontSize: 19,
        fontWeight: FontWeight.w700,
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: warmSurface,
      border: fieldBorder,
      enabledBorder: fieldBorder,
      disabledBorder: disabledFieldBorder,
      focusedBorder: fieldBorder.copyWith(
        borderSide: const BorderSide(color: ink, width: 2),
      ),
      errorBorder: fieldBorder.copyWith(
        borderSide: const BorderSide(color: Color(0xFF9C3F36), width: 1.5),
      ),
      focusedErrorBorder: fieldBorder.copyWith(
        borderSide: const BorderSide(color: Color(0xFF9C3F36), width: 2),
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
      labelStyle: const TextStyle(color: mutedInk),
      hintStyle: const TextStyle(color: mutedInk),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(54),
        backgroundColor: ink,
        foregroundColor: warmSurface,
        disabledBackgroundColor: const Color(0xFFB9C2B6),
        disabledForegroundColor: const Color(0xFF445249),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
        ),
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size.fromHeight(52),
        foregroundColor: ink,
        side: const BorderSide(color: outlineColor),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
        ),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: terracotta,
        minimumSize: const Size(48, 48),
        textStyle: const TextStyle(fontWeight: FontWeight.w700),
      ),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: ink,
      contentTextStyle: const TextStyle(color: Colors.white, fontSize: 15),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppRadius.sm),
      ),
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      color: terracotta,
      linearTrackColor: sage,
    ),
    navigationRailTheme: const NavigationRailThemeData(
      backgroundColor: warmSurface,
      indicatorColor: sage,
      selectedIconTheme: IconThemeData(color: ink),
      unselectedIconTheme: IconThemeData(color: mutedInk),
      selectedLabelTextStyle: TextStyle(
        color: ink,
        fontSize: 14,
        fontWeight: FontWeight.w700,
      ),
      unselectedLabelTextStyle: TextStyle(
        color: mutedInk,
        fontSize: 14,
        fontWeight: FontWeight.w500,
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: warmSurface,
      indicatorColor: sage,
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        final selected = states.contains(WidgetState.selected);
        return TextStyle(
          color: selected ? ink : mutedInk,
          fontSize: 12,
          fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
        );
      }),
    ),
  );
}
