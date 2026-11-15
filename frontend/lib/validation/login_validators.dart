/// Limites del login, sin imponer las reglas de alta a credenciales historicas.
abstract final class LoginValidators {
  static String? email(String? value) {
    final text = value ?? '';
    if (text.trim().isEmpty) return 'Ingresa tu correo';
    if (text != text.trim() || RegExp(r'\s').hasMatch(text)) {
      return 'El correo no puede contener espacios';
    }
    if (text.length > 254) return 'El correo no puede superar 254 caracteres';
    if (!RegExp(r'^[^@\s]+@[^@\s]+$').hasMatch(text)) {
      return 'Ingresa un correo valido';
    }
    return null;
  }

  static String? password(String? value) {
    final text = value ?? '';
    if (text.trim().isEmpty) return 'Ingresa tu contrasena';
    if (text.length > 72) return 'La contrasena no puede superar 72 caracteres';
    // No recortar ni normalizar la contrasena.
    return null;
  }
}
