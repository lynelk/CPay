import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'app_config.dart';

class CitoApiException implements Exception {
  const CitoApiException(this.message, {this.statusCode, this.code});

  final String message;
  final int? statusCode;
  final String? code;

  @override
  String toString() => message;
}

class CitoApiClient {
  CitoApiClient({
    required this.config,
    FlutterSecureStorage? secureStorage,
    HttpClient? httpClient,
  })  : _storage = secureStorage ?? const FlutterSecureStorage(),
        _httpClient = httpClient ?? HttpClient();

  static const _cookieStorageKey = 'cito.session.cookies';
  static const _userStorageKey = 'cito.session.user';

  final AppConfig config;
  final FlutterSecureStorage _storage;
  final HttpClient _httpClient;

  final Map<String, String> _cookies = <String, String>{};
  String _csrfHeaderName = 'X-XSRF-TOKEN';
  String? _csrfToken;

  Future<void> initialize() async {
    final raw = await _storage.read(key: _cookieStorageKey);
    if (raw == null || raw.isEmpty) return;
    try {
      final parsed = jsonDecode(raw);
      if (parsed is Map<String, dynamic>) {
        for (final entry in parsed.entries) {
          _cookies[entry.key] = entry.value.toString();
        }
      }
    } on FormatException {
      await _storage.delete(key: _cookieStorageKey);
    }
  }

  Future<Map<String, dynamic>?> readStoredUser() async {
    final raw = await _storage.read(key: _userStorageKey);
    if (raw == null || raw.isEmpty) return null;
    try {
      final parsed = jsonDecode(raw);
      return parsed is Map<String, dynamic> ? parsed : null;
    } on FormatException {
      await _storage.delete(key: _userStorageKey);
      return null;
    }
  }

  Future<void> storeUser(Map<String, dynamic> user) =>
      _storage.write(key: _userStorageKey, value: jsonEncode(user));

  Future<void> clearSession() async {
    _cookies.clear();
    _csrfToken = null;
    await Future.wait(<Future<void>>[
      _storage.delete(key: _cookieStorageKey),
      _storage.delete(key: _userStorageKey),
    ]);
  }

  Future<Map<String, dynamic>> getJson(String path) =>
      requestJson('GET', path);

  Future<Map<String, dynamic>> postJson(
    String path, {
    Map<String, dynamic>? body,
  }) =>
      requestJson('POST', path, body: body);

  Future<Map<String, dynamic>> patchJson(
    String path, {
    Map<String, dynamic>? body,
  }) =>
      requestJson('PATCH', path, body: body);

  Future<Map<String, dynamic>> requestJson(
    String method,
    String path, {
    Map<String, dynamic>? body,
    bool retryOnForbidden = true,
  }) async {
    final upperMethod = method.toUpperCase();
    if (_isMutating(upperMethod) && path != '/auth/csrf') {
      await _ensureCsrfToken();
    }

    final request = await _httpClient
        .openUrl(upperMethod, config.resolve(path))
        .timeout(const Duration(seconds: 15));
    request.followRedirects = true;
    request.maxRedirects = 3;
    request.headers.set(HttpHeaders.acceptHeader, ContentType.json.mimeType);
    request.headers.set(HttpHeaders.userAgentHeader, 'CitoBusinessMobile/1.0');
    if (_cookies.isNotEmpty) {
      request.headers.set(
        HttpHeaders.cookieHeader,
        _cookies.entries.map((entry) => '${entry.key}=${entry.value}').join('; '),
      );
    }
    if (_csrfToken != null && _isMutating(upperMethod)) {
      request.headers.set(_csrfHeaderName, _csrfToken!);
    }
    if (body != null) {
      request.headers.contentType = ContentType.json;
      request.write(jsonEncode(body));
    }

    final response = await request.close().timeout(const Duration(seconds: 30));
    await _captureCookies(response.cookies);
    final responseBody = await utf8.decoder.bind(response).join();

    if (response.statusCode == HttpStatus.forbidden && retryOnForbidden && _isMutating(upperMethod)) {
      _csrfToken = null;
      await _ensureCsrfToken();
      return requestJson(
        upperMethod,
        path,
        body: body,
        retryOnForbidden: false,
      );
    }

    final payload = _decodeObject(responseBody);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw CitoApiException(
        _messageFrom(payload, fallback: response.reasonPhrase ?? 'Request failed.'),
        statusCode: response.statusCode,
        code: payload['code']?.toString(),
      );
    }
    return payload;
  }

  Future<void> _ensureCsrfToken() async {
    if (_csrfToken != null) return;
    final payload = await requestJson(
      'GET',
      '/auth/csrf',
      retryOnForbidden: false,
    );
    _csrfHeaderName = payload['headerName']?.toString().trim().isNotEmpty == true
        ? payload['headerName'].toString()
        : 'X-XSRF-TOKEN';
    _csrfToken = payload['token']?.toString();
    if (_csrfToken == null || _csrfToken!.isEmpty) {
      throw const CitoApiException('Cito could not establish a secure request token.');
    }
  }

  Future<void> _captureCookies(List<Cookie> responseCookies) async {
    var changed = false;
    for (final cookie in responseCookies) {
      if (cookie.maxAge == 0 || cookie.value.isEmpty) {
        changed = _cookies.remove(cookie.name) != null || changed;
      } else {
        _cookies[cookie.name] = cookie.value;
        changed = true;
      }
    }
    if (changed) {
      await _storage.write(key: _cookieStorageKey, value: jsonEncode(_cookies));
    }
  }

  static bool _isMutating(String method) =>
      method == 'POST' || method == 'PUT' || method == 'PATCH' || method == 'DELETE';

  static Map<String, dynamic> _decodeObject(String body) {
    if (body.trim().isEmpty) return <String, dynamic>{};
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic>) return decoded;
      if (decoded is List<dynamic>) return <String, dynamic>{'data': decoded};
      return <String, dynamic>{'value': decoded};
    } on FormatException {
      return <String, dynamic>{'message': body.trim()};
    }
  }

  static String _messageFrom(
    Map<String, dynamic> payload, {
    required String fallback,
  }) {
    return payload['message']?.toString().trim().isNotEmpty == true
        ? payload['message'].toString()
        : payload['error']?.toString().trim().isNotEmpty == true
            ? payload['error'].toString()
            : fallback;
  }
}
