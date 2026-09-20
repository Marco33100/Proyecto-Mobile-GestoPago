import 'package:flutter/material.dart';

import '../services/auth_api_service.dart';

class RegisterPage extends StatefulWidget {
  const RegisterPage({super.key, required this.authApi});

  final AuthApiService authApi;

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final _formKey = GlobalKey<FormState>();
  final _fullNameController = TextEditingController();
  final _emailController = TextEditingController();
  final _identifierController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  bool _loading = false;
  bool _obscurePassword = true;

  @override
  void dispose() {
    _fullNameController.dispose();
    _emailController.dispose();
    _identifierController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_loading) return;
    _normalizeInputs();
    if (!_formKey.currentState!.validate()) return;
    setState(() => _loading = true);

    try {
      final message = await widget.authApi.register(
        email: _emailController.text,
        identifier: _identifierController.text,
        password: _passwordController.text,
        fullName: _fullNameController.text,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message), backgroundColor: Colors.green),
      );
      Navigator.of(context).pop(_emailController.text.trim());
    } on ApiException catch (error) {
      if (mounted) _showError(error.message);
    } catch (_) {
      if (mounted) _showError('No fue posible completar el registro.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  void _normalizeInputs() {
    _replaceText(
      _fullNameController,
      _fullNameController.text.trim().replaceAll(RegExp(r'\s+'), ' '),
    );
    _replaceText(_emailController, _emailController.text.trim());
    _replaceText(_identifierController, _identifierController.text.trim());
  }

  void _replaceText(TextEditingController controller, String value) {
    controller.value = TextEditingValue(
      text: value,
      selection: TextSelection.collapsed(offset: value.length),
    );
  }

  String? _validatePassword(String? value) {
    final password = value ?? '';
    if (password.length < 8 || password.length > 72) {
      return 'Usa entre 8 y 72 caracteres';
    }
    if (!RegExp(r'[A-Z]').hasMatch(password) ||
        !RegExp(r'[a-z]').hasMatch(password) ||
        !RegExp(r'[0-9]').hasMatch(password)) {
      return 'Incluye mayuscula, minuscula y numero';
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Crear cuenta')),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    TextFormField(
                      controller: _fullNameController,
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Nombre completo',
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        final name = value ?? '';
                        if (name.trim().length < 2) return 'Ingresa tu nombre completo';
                        if (name != name.trim()) {
                          return 'El nombre no debe iniciar o terminar con espacios';
                        }
                        if (RegExp(r'\s{2,}').hasMatch(name)) {
                          return 'Usa solamente un espacio entre nombres';
                        }
                        if (name.length > 150) return 'El nombre es demasiado largo';
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _emailController,
                      keyboardType: TextInputType.emailAddress,
                      textInputAction: TextInputAction.next,
                      autofillHints: const [AutofillHints.email],
                      decoration: const InputDecoration(
                        labelText: 'Correo',
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        final email = value ?? '';
                        if (email.trim().isEmpty) return 'Ingresa tu correo';
                        if (email != email.trim() || RegExp(r'\s').hasMatch(email)) {
                          return 'El correo no puede contener espacios';
                        }
                        if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(email)) {
                          return 'Ingresa un correo valido';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _identifierController,
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Usuario',
                        hintText: 'marti_01',
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        final identifier = value ?? '';
                        if (identifier != identifier.trim()) {
                          return 'El identificador no puede llevar espacios';
                        }
                        if (!RegExp(r'^[A-Za-z0-9._-]{4,50}$').hasMatch(identifier)) {
                          return 'Usa de 4 a 50 letras, numeros, punto o guion';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _passwordController,
                      obscureText: _obscurePassword,
                      textInputAction: TextInputAction.next,
                      autofillHints: const [AutofillHints.newPassword],
                      decoration: InputDecoration(
                        labelText: 'Contraseña',
                        border: const OutlineInputBorder(),
                        suffixIcon: IconButton(
                          onPressed: () => setState(
                            () => _obscurePassword = !_obscurePassword,
                          ),
                          icon: Icon(
                            _obscurePassword ? Icons.visibility : Icons.visibility_off,
                          ),
                        ),
                      ),
                      validator: _validatePassword,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _confirmPasswordController,
                      obscureText: _obscurePassword,
                      onFieldSubmitted: (_) => _submit(),
                      decoration: const InputDecoration(
                        labelText: 'Confirmar contraseña',
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) => value != _passwordController.text
                          ? 'Las contraseñas no coinciden'
                          : null,
                    ),
                    const SizedBox(height: 24),
                    FilledButton(
                      onPressed: _loading ? null : _submit,
                      child: _loading
                          ? const SizedBox.square(
                              dimension: 22,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Registrarme'),
                    ),
                    TextButton(
                      onPressed: _loading ? null : () => Navigator.of(context).pop(),
                      child: const Text('Ya tengo cuenta'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
