import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';
import 'package:flutter_native_splash/flutter_native_splash.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'dart:async';
import 'dart:io';

void main() {
  WidgetsBinding widgetsBinding = WidgetsFlutterBinding.ensureInitialized();
  FlutterNativeSplash.preserve(widgetsBinding: widgetsBinding);
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: Colors.black,
        colorScheme: const ColorScheme.dark(
          primary: Colors.white,
          surface: Color(0xFF121212),
        ),
      ),
      home: const MainContainer(),
    );
  }
}

class MainContainer extends StatefulWidget {
  const MainContainer({super.key});

  @override
  State<MainContainer> createState() => _MainContainerState();
}

class _MainContainerState extends State<MainContainer> {
  String? _errorMessage;
  bool _isLoading = true;
  WebViewController? _controller;
  HttpServer? _server;
  int _port = 0; // Will be assigned dynamically
  Timer? _failsafeTimer;
  final List<String> _consoleLogs = [];

  @override
  void initState() {
    super.initState();
    _initializeApp();
  }

  Future<void> _initializeApp() async {
    try {
      // 1. Request Essential Permissions
      await [
        Permission.storage,
        Permission.camera,
        Permission.microphone,
      ].request();

      // 2. Start Hardened Offline Server
      await _startHardenedServer();

      // 3. Initialize WebView
      final controller = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setBackgroundColor(Colors.black)
        ..enableZoom(true)
        ..setNavigationDelegate(
          NavigationDelegate(
            onPageFinished: (_) {
              if (mounted) {
                setState(() => _isLoading = false);
                _failsafeTimer?.cancel();
                FlutterNativeSplash.remove();
              }
            },
            onWebResourceError: (error) {
              _showError("Render Error: ${error.description}\nCode: ${error.errorCode}\nURL: ${error.url}");
            },
          ),
        );

      // Advanced Console Debugging
      if (controller.platform is AndroidWebViewController) {
        AndroidWebViewController.enableDebugging(true);
        (controller.platform as AndroidWebViewController).setAllowFileAccess(true);
        (controller.platform as AndroidWebViewController).setAllowContentAccess(true);
      }

      // Capture JS Errors for Diagnostic Console
      controller.setOnConsoleMessage((message) {
        debugPrint("WEB_CONSOLE: ${message.message}");
        if (mounted) {
          setState(() {
            _consoleLogs.add("[${message.level.name.toUpperCase()}] ${message.message}");
          });
        }
      });

      // 4. Start Failsafe Timer (20 seconds)
      _failsafeTimer = Timer(const Duration(seconds: 20), () {
        if (_isLoading && mounted && _errorMessage == null) {
          _showError("Initialization Timeout: The app is taking too long to load. CSS/JS files might be missing or CORS blocked.\n\nCaptured Logs:\n${_consoleLogs.join('\n')}");
        }
      });

      // 5. Load dynamic entry point (Absolute URL)
      String entry = "index.html";
      try {
        entry = "index.html";
      } catch (_) {}
      
      await controller.loadRequest(Uri.parse('http://127.0.0.1:$_port/$entry'));
      
      if (mounted) {
        setState(() {
          _controller = controller;
        });
      }

    } catch (e, stack) {
      _showError("Startup Failure: $e\n\n$stack");
    }
  }

  Future<void> _startHardenedServer() async {
    if (_server != null) return;
    try {
      _server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      _port = _server!.port;
      
      _server!.listen((HttpRequest request) async {
        try {
          String path = Uri.decodeComponent(request.uri.path);
          path = path == '/' ? '/index.html' : path;
          String assetPath = 'assets/www$path';
          
          final content = await rootBundle.load(assetPath);
          final bytes = content.buffer.asUint8List();
          
          // CRITICAL: Stability & Security Headers
          // COOP/COEP removed for external CDN compatibility (Tailwind, Fonts, etc.)
          request.response.headers.add('Access-Control-Allow-Origin', '*');
          request.response.headers.contentLength = bytes.length;
          
          // Enhanced MIME Mapping for strict browsers
          String ext = path.split('.').last.toLowerCase();
          switch (ext) {
            case 'html': request.response.headers.contentType = ContentType.html; break;
            case 'js': request.response.headers.contentType = ContentType.parse('application/javascript; charset=utf-8'); break;
            case 'css': request.response.headers.contentType = ContentType.parse('text/css; charset=utf-8'); break;
            case 'json': request.response.headers.contentType = ContentType.parse('application/json'); break;
            case 'svg': request.response.headers.contentType = ContentType.parse('image/svg+xml'); break;
            case 'png': request.response.headers.contentType = ContentType.parse('image/png'); break;
            case 'jpg':
            case 'jpeg': request.response.headers.contentType = ContentType.parse('image/jpeg'); break;
            case 'gif': request.response.headers.contentType = ContentType.parse('image/gif'); break;
            case 'webp': request.response.headers.contentType = ContentType.parse('image/webp'); break;
            case 'woff': request.response.headers.contentType = ContentType.parse('font/woff'); break;
            case 'woff2': request.response.headers.contentType = ContentType.parse('font/woff2'); break;
            case 'ttf': request.response.headers.contentType = ContentType.parse('font/ttf'); break;
            default: request.response.headers.contentType = ContentType.binary;
          }
          
          request.response.add(bytes);
        } catch (e) {
          String failLog = "[404 NOT FOUND] ${request.uri.path}";
          debugPrint(failLog);
          if (mounted) {
            setState(() {
              _consoleLogs.add(failLog);
            });
          }
          request.response.statusCode = HttpStatus.notFound;
          request.response.write("Resource not found: ${request.uri.path}");
        } finally {
          await request.response.close();
        }
      });
    } catch (e) {
      rethrow;
    }
  }

  void _showError(String message) {
    if (mounted) {
      setState(() {
        _errorMessage = message;
        _isLoading = false;
      });
      _failsafeTimer?.cancel();
      FlutterNativeSplash.remove();
    }
  }

  @override
  void dispose() {
    _failsafeTimer?.cancel();
    _server?.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_errorMessage != null) {
      return Scaffold(
        appBar: AppBar(
          title: const Text("Diagnostic Console"),
          backgroundColor: Colors.transparent,
          elevation: 0,
        ),
        body: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(Icons.error_outline_rounded, color: Colors.redAccent, size: 48),
              const SizedBox(height: 16),
              const Text(
                "Loading Failure",
                style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              const Text(
                "The application was unable to render the mirrored content. This is usually due to missing files in the uploaded ZIP or code that requires a full browser environment.",
                style: TextStyle(color: Colors.white60, height: 1.4),
              ),
              const SizedBox(height: 24),
              Expanded(
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: const Color(0xFF1E1E1E),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.white10),
                  ),
                  child: SingleChildScrollView(
                    child: SelectableText(
                      _errorMessage!,
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        color: Colors.orangeAccent,
                        fontSize: 12,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                height: 56,
                child: ElevatedButton.icon(
                  onPressed: () {
                    setState(() {
                      _errorMessage = null;
                      _isLoading = true;
                      _controller = null;
                    });
                    _initializeApp();
                  },
                  icon: const Icon(Icons.refresh_rounded),
                  label: const Text("REBOOT ENGINE"),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.white,
                    foregroundColor: Colors.black,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
              )
            ],
          ),
        ),
      );
    }

    return Scaffold(
      body: SafeArea(
        child: Stack(
          children: [
            if (_controller != null) WebViewWidget(controller: _controller!),
            if (_isLoading)
              Container(
                color: Colors.black,
                child: const Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircularProgressIndicator(color: Colors.white, strokeWidth: 3),
                      SizedBox(height: 24),
                      Text(
                        "PREPARING MIRROR...",
                        style: TextStyle(
                          color: Colors.white38,
                          letterSpacing: 2,
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
