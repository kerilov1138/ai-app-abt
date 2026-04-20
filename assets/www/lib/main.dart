import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';
import 'dart:io';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Yerel bir HTTP sunucusu başlatıyoruz.
  // Bu, Web Speech API'nin (Ses Tanıma) çalışması için gereken "Secure Context"i sağlar.
  final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 8080);
  
  server.listen((HttpRequest request) async {
    try {
      String path = request.uri.path;
      if (path == '/') path = '/index.html';
      
      // Assets klasöründen dosyayı oku
      final assetPath = 'assets/www${path}';
      final byteData = await rootBundle.load(assetPath);
      final bytes = byteData.buffer.asUint8List();
      
      // İçerik tipini belirle
      String contentType = 'text/html';
      if (path.endsWith('.js')) contentType = 'application/javascript';
      if (path.endsWith('.css')) contentType = 'text/css';
      if (path.endsWith('.json')) contentType = 'application/json';
      if (path.endsWith('.png')) contentType = 'image/png';
      if (path.endsWith('.jpg') || path.endsWith('.jpeg')) contentType = 'image/jpeg';
      if (path.endsWith('.wav')) contentType = 'audio/wav';
      if (path.endsWith('.mp3')) contentType = 'audio/mpeg';
      if (path.endsWith('.svg')) contentType = 'image/svg+xml';
      
      request.response
        ..headers.contentType = ContentType.parse(contentType)
        ..add(bytes)
        ..close();
    } catch (e) {
      request.response
        ..statusCode = HttpStatus.notFound
        ..close();
    }
  });

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Kelime Oyunu AI',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterialDesign: true,
      ),
      home: const WebViewPage(),
    );
  }
}

class WebViewPage extends StatefulWidget {
  const WebViewPage({super.key});

  @override
  State<WebViewPage> createState() => _WebViewPageState();
}

class _WebViewPageState extends State<WebViewPage> {
  late final WebViewController _controller;
  bool _isLoading = true;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _requestPermissions();
    _initController();
  }

  Future<void> _requestPermissions() async {
    await [
      Permission.microphone,
      Permission.speech,
    ].request();
  }

  void _initController() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(const Color(0xFF0A0A20))
      ..addJavaScriptChannel(
        'ConsoleLog',
        onMessageReceived: (JavaScriptMessage message) {
          debugPrint('JS: ${message.message}');
        },
      )
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (String url) {
            setState(() {
              _isLoading = true;
              _errorMessage = null;
            });
          },
          onPageFinished: (String url) {
            setState(() {
              _isLoading = false;
            });
          },
          onWebResourceError: (WebResourceError error) {
            if (error.description.contains("net::ERR_CACHE_MISS")) return;
            setState(() {
              _errorMessage = "Yükleme hatası: ${error.description}";
              _isLoading = false;
            });
          },
        ),
      );

    if (_controller.platform is AndroidWebViewController) {
      final androidController = _controller.platform as AndroidWebViewController;
      
      androidController.setOnPermissionRequest((request) async {
        return request.grant();
      });

      androidController.setMediaPlaybackRequiresUserGesture(false);
    }
    
    // Yerel sunucu üzerinden uygulamayı yükle (Ses tanıma için zorunlu)
    _controller.loadRequest(Uri.parse('http://localhost:8080/index.html'));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0A0A20),
      body: SafeArea(
        child: Stack(
          children: [
            WebViewWidget(controller: _controller),
            if (_isLoading)
              const Center(
                child: CircularProgressIndicator(color: Colors.blue),
              ),
            if (_errorMessage != null)
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(20.0),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.error_outline, color: Colors.red, size: 60),
                      const SizedBox(height: 16),
                      Text(
                        _errorMessage!,
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: Colors.white),
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: () => _controller.loadFlutterAsset('assets/www/index.html'),
                        child: const Text("Tekrar Dene"),
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
