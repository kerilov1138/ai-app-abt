import eel
import socket
import threading
import mss
import pyautogui
from PIL import Image, ImageDraw
import io
import base64
import time
import struct
import sounddevice as sd
import numpy as np
import sys
import os
import tempfile

# Embed HTML, JS, and CSS as string constants
INDEX_HTML = """<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Yansıma - Ekran Paylaşımı</title>
    <link rel="stylesheet" href="style.css">
    <script type="text/javascript" src="/eel.js"></script>
</head>
<body>
    <!-- Connection Dashboard -->
    <div class="container">
        <!-- Sidebar -->
        <div class="sidebar">
            <div class="logo-area">
                <h1>Yansıma</h1>
                <p>Güvenli Uzak Bağlantı</p>
            </div>
            <div class="status-indicator">
                <span class="dot"></span>
                <span>Bağlantıya Hazır</span>
            </div>
        </div>

        <!-- Main Workspace -->
        <div class="main-content">
            <!-- ID Display Card -->
            <div class="card">
                <h2>Senin Bağlantı ID'n</h2>
                <p class="description">Karşı tarafın sana bağlanabilmesi için bu kodu paylaş.</p>
                <div class="id-box">
                    <input type="text" id="my-id" readonly value="Yükleniyor...">
                    <button class="copy-btn" onclick="navigator.clipboard.writeText(document.getElementById('my-id').value)">Kopyala</button>
                </div>
            </div>

            <!-- Connect to Remote Card -->
            <div class="card">
                <h2>Uzak Bilgisayara Bağlan</h2>
                <p class="description">Bağlanmak istediğin bilgisayarın ID'sini gir.</p>
                <div class="input-group">
                    <input type="text" id="remote-id" placeholder="ID Girin (Örn: C0A80101TR)">
                    <button id="connect-btn" onclick="connectToRemote()">Bağlan</button>
                </div>
            </div>
        </div>
    </div>

    <!-- Remote Screen View (Hidden by default) -->
    <div id="remote-view" class="hidden">
        <div class="toolbar">
            <span>Bağlantı Aktif</span>
            <div class="mic-controls">
                <button id="btn-mic" onclick="toggleMic()" style="opacity: 0.7;">Mikrofon: Kapalı</button>
                <div class="mic-level-container" title="Mikrofon Ses Seviyesi">
                    <div id="mic-level-bar"
                        style="width: 0%; height: 100%; background-color: #28a745; transition: width 0.1s;"></div>
                </div>
            </div>
            <button id="btn-spk" onclick="toggleSpk()" style="opacity: 0.7;">Hoparlör: Kapalı</button>
            <div class="quality-select">
                <select id="image-quality" onchange="changeQuality(this.value)"
                    style="padding: 5px; border-radius: 4px;">
                    <option value="SD">Düşük Kalite (SD)</option>
                    <option value="HD" selected>Yüksek Kalite (HD)</option>
                    <option value="2K">Yüksek Kalite (2K)</option>
                    <option value="4K">Yüksek Kalite (4K)</option>
                </select>
            </div>
            <button onclick="disconnect()" style="background-color: #dc3545;">Bağlantıyı Kes</button>
        </div>
        <img id="remote-screen-img" src="" alt="Uzak Ekran">
    </div>

    <!-- Connection Request Modal -->
    <div id="connection-modal" class="modal hidden">
        <div class="modal-content">
            <h3>Bağlantı İsteği</h3>
            <p><span id="requester-ip">Unknown</span> adresinden bağlantı isteği var.</p>
            <div class="modal-actions">
                <button onclick="respondConnection(true)" class="btn-accept">Kabul Et</button>
                <button onclick="respondConnection(false)" class="btn-deny">Reddet</button>
            </div>
        </div>
    </div>

    <!-- Waiting Modal -->
    <div id="waiting-modal" class="modal hidden">
        <div class="modal-content">
            <h3>Bağlantı İsteği Gönderildi</h3>
            <div class="status-indicator" style="justify-content: center; margin: 20px 0;">
                <span class="dot" style="animation: pulse 1s infinite;"></span>
                <span>Karşı tarafın onayı bekleniyor...</span>
            </div>
            <button onclick="cancelConnection()" class="btn-deny" style="margin-top: 10px;">İptal</button>
        </div>
    </div>

    <!-- Host Active Session View (No Screen Share, just controls) -->
    <div id="host-view" class="hidden">
        <div class="container" style="height: auto; width: 400px; flex-direction: column;">
            <div style="padding: 20px; text-align: center; border-bottom: 1px solid #444;">
                <h2 style="color: #28a745;">Oturum Aktif</h2>
                <p style="font-size: 12px; color: #aaa;">Ekranınız paylaşılıyor</p>
            </div>
            <div class="toolbar"
                style="padding: 20px; justify-content: space-around; background: transparent; border: none;">
                <div class="mic-controls">
                    <button id="btn-mic-host" onclick="toggleMic()" style="opacity: 0.7;">Mikrofon: Kapalı</button>
                    <div class="mic-level-container" title="Mikrofon Ses Seviyesi">
                        <div id="mic-level-bar-host" style="width: 0%; height: 100%; background-color: #28a745;"></div>
                    </div>
                </div>
                <button id="btn-spk-host" onclick="toggleSpk()" style="opacity: 0.7;">Hoparlör: Kapalı</button>
            </div>
            <div style="padding: 20px; text-align: center;">
                <button onclick="disconnect()" style="background-color: #dc3545; width: 100%; padding: 10px;">Oturumu
                    Sonlandır</button>
            </div>
        </div>
    </div>
    <script src="script.js"></script>
</body>
</html>
"""

SCRIPT_JS = """function connectToRemote() {
    const rid = document.getElementById("remote-id").value;
    if (!rid) {
        alert("Lütfen geçerli bir ID girin.");
        return;
    }
    document.getElementById("waiting-modal").classList.remove("hidden");
    document.getElementById("waiting-modal").style.display = "flex";
    eel.connect_to_remote(rid)();
}

function cancelConnection() {
    document.getElementById("waiting-modal").classList.add("hidden");
    eel.stop_session();
}

function disconnect() {
    eel.stop_session();
    document.getElementById("remote-view").classList.add("hidden");
    document.getElementById("host-view").classList.add("hidden");
    document.querySelector(".container").classList.remove("hidden");

    micState = false;
    spkState = false;
    updateButton("btn-mic", false, "Mikrofon");
    updateButton("btn-spk", false, "Hoparlör");
    updateButton("btn-mic-host", false, "Mikrofon");
    updateButton("btn-spk-host", false, "Hoparlör");

    document.getElementById("remote-screen-img").src = "";
}

let micState = false;
let spkState = false;

eel.expose(showHostView);
function showHostView() {
    document.querySelector(".container").classList.add("hidden");
    document.getElementById("host-view").classList.remove("hidden");
}

function toggleMic() {
    micState = !micState;
    updateButton("btn-mic", micState, "Mikrofon");
    updateButton("btn-mic-host", micState, "Mikrofon");
    eel.toggle_audio(micState, spkState);
}

function toggleSpk() {
    spkState = !spkState;
    updateButton("btn-spk", spkState, "Hoparlör");
    updateButton("btn-spk-host", spkState, "Hoparlör");
    eel.toggle_audio(micState, spkState);
}

function updateButton(id, state, label) {
    const btn = document.getElementById(id);
    if (btn) {
        btn.innerText = label + ": " + (state ? "Açık" : "Kapalı");
        btn.style.opacity = state ? "1" : "0.7";
    }
}

eel.expose(updateMicVisual);
function updateMicVisual(level) {
    const bar = document.getElementById('mic-level-bar');
    if (bar) {
        bar.style.width = level + "%";
        if (level > 80) bar.style.backgroundColor = "#dc3545";
        else if (level > 50) bar.style.backgroundColor = "#ffc107";
        else bar.style.backgroundColor = "#28a745";
    }
}

function changeQuality(val) {
    eel.set_image_quality(val);
}

eel.expose(show_connection_request);
function show_connection_request(ip) {
    console.log("Bağlantı isteği geldi:", ip);
    document.getElementById('requester-ip').innerText = ip;
    document.getElementById('connection-modal').classList.remove("hidden");
    document.getElementById("connection-modal").style.display = "flex";
}

function respondConnection(allow) {
    document.getElementById('connection-modal').classList.add("hidden");
    eel.respond_to_request(allow);
}

eel.expose(updateRemoteScreen);
function updateRemoteScreen(imageStr) {
    document.getElementById("waiting-modal").classList.add("hidden");
    document.getElementById("remote-view").classList.remove("hidden");
    document.querySelector(".container").classList.add("hidden");
    document.getElementById("remote-screen-img").src = imageStr;
}

eel.expose(on_connection_closed);
function on_connection_closed() {
    document.getElementById("waiting-modal").classList.add("hidden");
    document.getElementById("remote-view").classList.add("hidden");
    document.getElementById("host-view").classList.add("hidden");
    document.querySelector(".container").classList.remove("hidden");
    alert("Bağlantı sonlandı veya reddedildi.");
}

const img = document.getElementById("remote-screen-img");

function getScaledCoordinates(e, imageElement) {
    const rect = imageElement.getBoundingClientRect();
    const naturalWidth = imageElement.naturalWidth;
    const naturalHeight = imageElement.naturalHeight;
    if (!naturalWidth || !naturalHeight) return null;

    const scaleX = rect.width / naturalWidth;
    const scaleY = rect.height / naturalHeight;
    const scale = Math.min(scaleX, scaleY);

    const displayWidth = naturalWidth * scale;
    const displayHeight = naturalHeight * scale;

    const offsetX = (rect.width - displayWidth) / 2;
    const offsetY = (rect.height - displayHeight) / 2;

    const clientX = e.clientX - rect.left;
    const clientY = e.clientY - rect.top;

    let x = (clientX - offsetX) / displayWidth;
    let y = (clientY - offsetY) / displayHeight;

    if (x < 0) x = 0;
    if (x > 1) x = 1;
    if (y < 0) y = 0;
    if (y > 1) y = 1;

    return { x, y };
}

let isThrottled = false;
img.addEventListener('mousemove', (e) => {
    if (isThrottled) return;
    isThrottled = true;
    requestAnimationFrame(() => {
        const coords = getScaledCoordinates(e, img);
        if (coords) {
            eel.send_mouse_event('MOVE', coords.x, coords.y);
        }
        isThrottled = false;
    });
});

img.addEventListener('mousedown', (e) => {
    if (e.button !== 0) return;
    const coords = getScaledCoordinates(e, img);
    if (coords) {
        eel.send_mouse_event('MOUSEDOWN_LEFT', coords.x, coords.y);
    }
});

window.addEventListener('mouseup', (e) => {
    if (e.button !== 0) return;
    if (!document.getElementById("remote-view").classList.contains("hidden")) {
        const coords = getScaledCoordinates(e, img);
        if (coords) {
            eel.send_mouse_event('MOUSEUP_LEFT', coords.x, coords.y);
        }
    }
});

img.addEventListener('contextmenu', (e) => {
    e.preventDefault();
    const coords = getScaledCoordinates(e, img);
    if (coords) {
        eel.send_mouse_event('RCLICK', coords.x, coords.y);
    }
});

img.addEventListener('dblclick', (e) => {
    const coords = getScaledCoordinates(e, img);
    if (coords) {
        eel.send_mouse_event('DCLICK', coords.x, coords.y);
    }
});

window.addEventListener('keydown', (e) => {
    if (!document.getElementById("remote-view").classList.contains("hidden")) {
        if (["Tab", "Alt", "F5", "F11", "F12"].includes(e.key) || 
            (e.ctrlKey && ["c", "v", "x", "a", "s"].includes(e.key.toLowerCase()))) {
            e.preventDefault();
        }
        eel.send_keyboard_event("DOWN:" + e.key);
    }
});

window.addEventListener('keyup', (e) => {
    if (!document.getElementById("remote-view").classList.contains("hidden")) {
        if (["Tab", "Alt", "F5", "F11", "F12"].includes(e.key) || 
            (e.ctrlKey && ["c", "v", "x", "a", "s"].includes(e.key.toLowerCase()))) {
            e.preventDefault();
        }
        eel.send_keyboard_event("UP:" + e.key);
    }
});

const imgElement = document.getElementById("remote-screen-img");
if (imgElement) {
    imgElement.addEventListener('dragover', (e) => {
        e.preventDefault();
    });

    imgElement.addEventListener('drop', async (e) => {
        e.preventDefault();
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            for (let i = 0; i < files.length; i++) {
                const file = files[i];
                const reader = new FileReader();
                reader.onload = async function(evt) {
                    const base64Data = evt.target.result.split(',')[1];
                    await eel.receive_file_from_client(file.name, base64Data)();
                };
                reader.readAsDataURL(file);
            }
        }
    });
}

window.onload = async function () {
    if (window.eel) {
        let myId = await eel.get_local_id()();
        document.getElementById("my-id").value = myId;
    }
}
"""

STYLE_CSS = """:root {
    --bg-dark: #1e1e1e;
    --bg-card: #2d2d2d;
    --primary: #ef3945;
    --text-main: #ffffff;
    --text-muted: #aaaaaa;
    --input-bg: #383838;
}

* {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

body {
    background-color: var(--bg-dark);
    color: var(--text-main);
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    overflow: hidden;
}

.container {
    display: flex;
    width: 800px;
    height: 500px;
    background-color: var(--bg-card);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
    border-radius: 8px;
    overflow: hidden;
}

.sidebar {
    width: 250px;
    background-color: #252525;
    padding: 30px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    border-right: 1px solid #3d3d3d;
}

.logo-area h1 {
    font-size: 28px;
    font-weight: 600;
    color: var(--primary);
    margin-bottom: 5px;
}

.logo-area p {
    font-size: 12px;
    color: var(--text-muted);
}

.status-indicator {
    display: flex;
    align-items: center;
    font-size: 14px;
    color: #4caf50;
}

.dot {
    width: 10px;
    height: 10px;
    background-color: #28a745;
    border-radius: 50%;
    margin-right: 5px;
    box-shadow: 0 0 5px #4caf5080;
    display: inline-block;
}

.modal {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.5);
    display: flex;
    justify-content: center;
    align-items: center;
    z-index: 10000;
}

.modal-content {
    background-color: white;
    padding: 20px;
    border-radius: 8px;
    text-align: center;
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    min-width: 300px;
    color: #333;
}

.modal-actions {
    margin-top: 20px;
    display: flex;
    justify-content: space-around;
}

.btn-accept {
    background-color: #28a745;
    color: white;
}

.btn-deny {
    background-color: #dc3545;
    color: white;
}

.hidden {
    display: none !important;
}

.main-content {
    flex: 1;
    padding: 40px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 30px;
}

.card {
    background-color: #333;
    padding: 25px;
    border-radius: 6px;
    border: 1px solid #3d3d3d;
}

.card h2 {
    font-size: 18px;
    margin-bottom: 5px;
    font-weight: 500;
}

.description {
    font-size: 13px;
    color: var(--text-muted);
    margin-bottom: 20px;
}

label {
    display: block;
    font-size: 12px;
    margin-bottom: 8px;
    font-weight: 600;
    color: var(--text-muted);
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.id-box {
    display: flex;
    gap: 10px;
}

input {
    flex: 1;
    background-color: var(--input-bg);
    border: 1px solid #444;
    padding: 12px;
    color: white;
    font-size: 20px;
    font-family: monospace;
    border-radius: 4px;
    outline: none;
    letter-spacing: 2px;
    transition: border-color 0.2s;
}

input:focus {
    border-color: var(--primary);
}

button {
    cursor: pointer;
    border: none;
    border-radius: 4px;
    font-weight: 600;
    transition: filter 0.2s;
    padding: 10px 20px;
}

.copy-btn {
    width: 80px;
    background-color: #444;
    color: white;
    display: flex;
    justify-content: center;
    align-items: center;
}

.copy-btn:hover {
    background-color: #555;
}

#connect-btn {
    background-color: var(--primary);
    color: white;
    padding: 0 30px;
    font-size: 16px;
}

#connect-btn:hover {
    filter: brightness(1.1);
}

.input-group {
    display: flex;
    gap: 10px;
}

#remote-view {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    background-color: #000;
    z-index: 1000;
    display: flex;
    flex-direction: column;
}

.toolbar {
    height: 40px;
    background-color: #252525;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 20px;
    border-bottom: 1px solid #3d3d3d;
}

.toolbar button {
    background-color: #444;
    color: white;
    padding: 5px 15px;
    font-size: 12px;
}

.mic-controls {
    display: flex;
    align-items: center;
    gap: 5px;
}

.mic-level-container {
    width: 20px;
    height: 15px;
    background-color: #333;
    border: 1px solid #555;
    border-radius: 2px;
    overflow: hidden;
}

.quality-select {
    margin: 0 10px;
}

#remote-screen-img {
    flex: 1;
    width: 100%;
    height: calc(100vh - 40px);
    object-fit: contain;
    cursor: crosshair;
}

@keyframes pulse {
    0% {
        transform: scale(1);
        opacity: 1;
    }
    50% {
        transform: scale(1.5);
        opacity: 0.5;
    }
    100% {
        transform: scale(1);
        opacity: 1;
    }
}
"""

# Dynamic temporary folder extraction for standalone execution
temp_web_dir = os.path.join(tempfile.gettempdir(), 'yansima_web_assets')
os.makedirs(temp_web_dir, exist_ok=True)

# Write HTML, JS, and CSS files to the temporary folder
with open(os.path.join(temp_web_dir, 'index.html'), 'w', encoding='utf-8') as f:
    f.write(INDEX_HTML)
with open(os.path.join(temp_web_dir, 'script.js'), 'w', encoding='utf-8') as f:
    f.write(SCRIPT_JS)
with open(os.path.join(temp_web_dir, 'style.css'), 'w', encoding='utf-8') as f:
    f.write(STYLE_CSS)

# Ports
PORT_VIDEO = 5555
PORT_CONTROL = 5556
PORT_AUDIO = 5557
PORT_AUDIO_REVERSE = 5558

# Global PyAutoGUI Settings
SCREEN_WIDTH, SCREEN_HEIGHT = pyautogui.size()
pyautogui.PAUSE = 0
pyautogui.FAILSAFE = False

# Audio Settings
AUDIO_SAMPLE_RATE = 24000
AUDIO_CHANNELS = 1
AUDIO_DTYPE = 'int16'

# Initialize Eel on the temporary directory
eel.init(temp_web_dir)

class SessionManager:
    def __init__(self):
        self.host_mode = False
        self.client_mode = False
        self.active_client_ip = None
        self.approval_event = threading.Event()
        self.reject_event = threading.Event()
        self.session_active = False
        self.mic_enabled = False  # Start as Kapalı (False)
        self.spk_enabled = False  # Start as Kapalı (False)
        self.audio_quality = 1 # 1: HD, 0: SD
        self.image_quality = 'HD'

    def reset_approval(self):
        self.approval_event.clear()
        self.reject_event.clear()
        self.active_client_ip = None
        self.session_active = False

session = SessionManager()
control_sock = None

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return '127.0.0.1'

def encode_ip(ip):
    try:
        parts = ip.split('.')
        hex_ip = ''.join(f"{int(p):02X}" for p in parts)
        return hex_ip + 'TR'
    except Exception:
        return 'HATA000000'

def decode_id(id_str):
    try:
        hex_ip = id_str[:-2]
        parts = [str(int(hex_ip[i:i+2], 16)) for i in range(0, 8, 2)]
        return '.'.join(parts)
    except Exception:
        return None

def server_video_thread():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('0.0.0.0', PORT_VIDEO))
    s.listen(1)
    print(f'[HOST] Video sunucusu hazır: {PORT_VIDEO}')
    while True:
        try:
            client, addr = s.accept()
            client.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            print(f'[HOST] Görüntü bağlantı isteği: {addr[0]}')
            session.reset_approval()
            session.active_client_ip = addr[0]
            print('[HOST] Onay bekleniyor...')
            
            eel.show_connection_request(addr[0])()
            
            start_wait = time.time()
            decision = None
            while time.time() - start_wait < 60:
                if session.approval_event.is_set():
                    decision = 'accept'
                    break
                if session.reject_event.is_set():
                    decision = 'reject'
                    break
                time.sleep(0.1)
                
            if decision == 'accept':
                print('[HOST] İstek ONAYLANDI.')
                client.sendall(b'OK')
                session.session_active = True
                eel.showHostView()()
                
                with mss.mss() as sct:
                    monitor = sct.monitors[1] if len(sct.monitors) > 1 else sct.monitors[0]
                    while session.session_active:
                        try:
                            img = sct.grab(monitor)
                            img_pil = Image.frombytes('RGB', img.size, img.bgra, 'raw', 'BGRX')
                            cx, cy = pyautogui.position()
                            draw = ImageDraw.Draw(img_pil)
                            draw.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill='red', outline='white')
                            
                            q = session.image_quality
                            if q == 'SD':
                                width, height = 854, 480
                                jpeg_quality = 40
                            elif q == '2K':
                                width, height = 2560, 1440
                                jpeg_quality = 80
                            elif q == '4K':
                                width, height = 3840, 2160
                                jpeg_quality = 90
                            else: # HD
                                width, height = 1280, 720
                                jpeg_quality = 60
                            
                            img_pil.thumbnail((width, height))
                            buffer = io.BytesIO()
                            img_pil.save(buffer, format='JPEG', quality=jpeg_quality)
                            data = buffer.getvalue()
                            size = len(data)
                            client.sendall(struct.pack('>L', size) + data)
                            time.sleep(0.015)
                        except (BrokenPipeError, ConnectionResetError):
                            print('[HOST] İstemci koptu.')
                            break
                        except Exception as e:
                            print('[HOST] Yayın hatası: ' + str(e))
                            break
            else:
                print(f'[HOST] İstek reddedildi veya zaman aşımı: {decision}')
                try:
                    client.sendall(b'NO')
                except Exception:
                    pass
            client.close()
            session.session_active = False
            try:
                eel.on_connection_closed()()
            except Exception:
                pass
            print('[HOST] Video oturumu kapandı.')
        except Exception as e:
            print(f'[HOST] Video sunucu hatası: {e}')
            time.sleep(1)

def server_audio_thread():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('0.0.0.0', PORT_AUDIO))
    s.listen(1)
    print(f'[HOST] Ses sunucusu hazır: {PORT_AUDIO}')
    while True:
        try:
            client, _ = s.accept()
            if not session.session_active:
                client.close()
                continue
            
            def callback(indata, frames, time_info, status):
                try:
                    if not session.session_active:
                        return
                    if session.mic_enabled:
                        client.send(indata.tobytes())
                    else:
                        client.send(bytes(len(indata.tobytes())))
                except Exception:
                    pass
            
            try:
                with sd.InputStream(samplerate=AUDIO_SAMPLE_RATE, channels=AUDIO_CHANNELS, dtype=AUDIO_DTYPE, callback=callback):
                    while session.session_active:
                        sd.sleep(500)
                        if client.fileno() == -1:
                            break
            except Exception as e:
                print(f"Ses hata: {e}")
            finally:
                client.close()
        except Exception:
            pass

def server_audio_reverse_thread():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('0.0.0.0', PORT_AUDIO_REVERSE))
    s.listen(1)
    print(f'[HOST] Ses sunucusu hazır: {PORT_AUDIO_REVERSE}')
    while True:
        try:
            client, _ = s.accept()
            if not session.session_active:
                client.close()
                continue
            try:
                with sd.OutputStream(samplerate=AUDIO_SAMPLE_RATE, channels=AUDIO_CHANNELS, dtype=AUDIO_DTYPE) as stream:
                    while session.session_active:
                        data = client.recv(4096)
                        if not data:
                            break
                        if session.spk_enabled:
                            stream.write(np.frombuffer(data, dtype=AUDIO_DTYPE))
            except Exception:
                break
            finally:
                client.close()
        except Exception:
            pass

def server_control_thread():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('0.0.0.0', PORT_CONTROL))
    s.listen(1)
    while True:
        try:
            client, _ = s.accept()
            client.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            if not session.session_active:
                client.close()
                continue
            buffer = ''
            while session.session_active:
                data = client.recv(1024).decode('utf-8', errors='ignore')
                if not data:
                    break
                buffer += data
                while '\n' in buffer:
                    line, buffer = buffer.split('\n', 1)
                    process_control_command(line)
        except Exception:
            pass
        finally:
            try:
                client.close()
            except Exception:
                pass

def map_key(k):
    key_map = {
        'Control': 'ctrl',
        'Shift': 'shift',
        'Alt': 'alt',
        'Meta': 'win',
        'Backspace': 'backspace',
        'Tab': 'tab',
        'Enter': 'enter',
        'Escape': 'esc',
        'ArrowLeft': 'left',
        'ArrowUp': 'up',
        'ArrowRight': 'right',
        'ArrowDown': 'down',
        'Delete': 'delete',
        'CapsLock': 'capslock',
        'PageUp': 'pageup',
        'PageDown': 'pagedown',
        'End': 'end',
        'Home': 'home',
        'Insert': 'insert',
        ' ': 'space',
        'F1': 'f1', 'F2': 'f2', 'F3': 'f3', 'F4': 'f4', 'F5': 'f5', 'F6': 'f6',
        'F7': 'f7', 'F8': 'f8', 'F9': 'f9', 'F10': 'f10', 'F11': 'f11', 'F12': 'f12'
    }
    if k in key_map:
        return key_map[k]
    return k.lower()

def process_control_command(cmd_str):
    try:
        parts = cmd_str.split(':', 1)
        cmd = parts[0]
        args = parts[1] if len(parts) > 1 else ''
        if cmd == 'MOVE':
            x, y = map(float, args.split(','))
            pyautogui.moveTo(x * SCREEN_WIDTH, y * SCREEN_HEIGHT)
        elif cmd == 'CLICK':
            x, y = map(float, args.split(','))
            pyautogui.click(x * SCREEN_WIDTH, y * SCREEN_HEIGHT)
        elif cmd == 'RCLICK':
            x, y = map(float, args.split(','))
            pyautogui.rightClick(x * SCREEN_WIDTH, y * SCREEN_HEIGHT)
        elif cmd == 'DCLICK':
            x, y = map(float, args.split(','))
            pyautogui.doubleClick(x * SCREEN_WIDTH, y * SCREEN_HEIGHT)
        elif cmd == 'MOUSEDOWN_LEFT':
            x, y = map(float, args.split(','))
            pyautogui.mouseDown(x * SCREEN_WIDTH, y * SCREEN_HEIGHT, button='left')
        elif cmd == 'MOUSEUP_LEFT':
            x, y = map(float, args.split(','))
            pyautogui.mouseUp(x * SCREEN_WIDTH, y * SCREEN_HEIGHT, button='left')
        elif cmd == 'KEY':
            k = args
            if k.startswith('DOWN:'):
                key = map_key(k[5:])
                try:
                    pyautogui.keyDown(key)
                except Exception:
                    pass
            elif k.startswith('UP:'):
                key = map_key(k[3:])
                try:
                    pyautogui.keyUp(key)
                except Exception:
                    pass
            else:
                if len(k) == 1:
                    pyautogui.press(k)
                else:
                    pyautogui.press(k.lower())
        elif cmd == 'DISCONNECT':
            session.session_active = False
            try:
                eel.on_connection_closed()()
            except Exception:
                pass
        elif cmd == 'QUALITY':
            session.image_quality = args
    except Exception:
        pass

def client_connect_flow(ip):
    try:
        vid_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        vid_sock.connect((ip, PORT_VIDEO))
        vid_sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        print('[CLIENT] Sunucudan onay bekleniyor...')
        try:
            resp = vid_sock.recv(2)
            if resp != b'OK':
                print('[CLIENT] Bağlantı reddedildi.')
                eel.on_connection_closed()()
                vid_sock.close()
                return
        except Exception:
            return

        print('[CLIENT] Bağlantı Onaylandı!')
        threading.Thread(target=client_audio_recv, args=(ip,), daemon=True).start()
        threading.Thread(target=client_audio_send, args=(ip,), daemon=True).start()
        threading.Thread(target=client_control_send, args=(ip,), daemon=True).start()

        data = b''
        payload_size = struct.calcsize('>L')
        while True:
            while len(data) < payload_size:
                packet = vid_sock.recv(4096)
                if not packet:
                    break
                data += packet
            if len(data) < payload_size:
                return
            packed_size = data[:payload_size]
            data = data[payload_size:]
            msg_size = struct.unpack('>L', packed_size)[0]
            while len(data) < msg_size:
                data += vid_sock.recv(4096)
            frame_data = data[:msg_size]
            data = data[msg_size:]
            b64_img = base64.b64encode(frame_data).decode('utf-8')
            eel.updateRemoteScreen('data:image/jpeg;base64,' + b64_img)()
    except Exception as e:
        print('[CLIENT] Hata: ' + str(e))
        eel.on_connection_closed()()

def client_control_send(ip):
    global control_sock
    try:
        control_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        control_sock.connect((ip, PORT_CONTROL))
        control_sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    except Exception:
        pass

def client_audio_recv(ip):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((ip, PORT_AUDIO))
        with sd.OutputStream(samplerate=AUDIO_SAMPLE_RATE, channels=AUDIO_CHANNELS, dtype=AUDIO_DTYPE) as stream:
            while True:
                data = s.recv(4096)
                if not data:
                    break
                if session.spk_enabled:
                    stream.write(np.frombuffer(data, dtype=AUDIO_DTYPE))
    except Exception:
        pass

def client_audio_send(ip):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((ip, PORT_AUDIO_REVERSE))
        
        def cb(indata, frames, time_info, status):
            try:
                if int(time_info.currentTime * 10) % 5 == 0 and session.mic_enabled:
                    vol = int(np.sqrt(np.mean(indata ** 2)) / 32768 * 100)
                    eel.updateMicVisual(vol)()
            except Exception:
                pass
            try:
                if session.mic_enabled:
                    s.send(indata.tobytes())
                else:
                    s.send(bytes(len(indata.tobytes())))
            except Exception:
                pass
                
        with sd.InputStream(samplerate=AUDIO_SAMPLE_RATE, channels=AUDIO_CHANNELS, dtype=AUDIO_DTYPE, callback=cb):
            while True:
                sd.sleep(1000)
    except Exception:
        pass

@eel.expose
def get_local_id():
    return encode_ip(get_local_ip())

@eel.expose
def connect_to_remote(rid):
    ip = decode_id(rid)
    if not ip:
        return {'success': False, 'message': 'Geçersiz ID'}
    threading.Thread(target=client_connect_flow, args=(ip,), daemon=True).start()
    return {'success': True, 'message': 'İstek gönderildi'}

@eel.expose
def respond_to_request(allow):
    if allow:
        session.approval_event.set()
    else:
        session.reject_event.set()

@eel.expose
def send_mouse_event(type, x, y):
    try:
        if control_sock:
            control_sock.send(f"{type}:{x},{y}\n".encode())
    except Exception:
        pass

@eel.expose
def send_keyboard_event(key):
    try:
        if control_sock:
            control_sock.send(f"KEY:{key}\n".encode())
    except Exception:
        pass

@eel.expose
def toggle_audio(mic, spk):
    session.mic_enabled = mic
    session.spk_enabled = spk

@eel.expose
def stop_session():
    global control_sock
    try:
        if control_sock:
            control_sock.send(b"DISCONNECT\n")
    except Exception:
        pass
    session.session_active = False
    session.active_client_ip = None
    session.approval_event.clear()
    session.reject_event.clear()
    if control_sock:
        try:
            control_sock.close()
        except Exception:
            pass
        control_sock = None
    try:
        eel.on_connection_closed()()
    except Exception:
        pass
    print('[SESSION] Oturum sonlandırıldı.')

@eel.expose
def set_audio_quality(q):
    global AUDIO_SAMPLE_RATE
    session.audio_quality = q
    if q == 1:
        AUDIO_SAMPLE_RATE = 24000
    else:
        AUDIO_SAMPLE_RATE = 8000

@eel.expose
def set_image_quality(q):
    session.image_quality = q
    try:
        if control_sock:
            control_sock.send(f"QUALITY:{q}\n".encode())
    except Exception:
        pass

@eel.expose
def receive_file_from_client(filename, base64_data):
    try:
        import base64
        import os
        desktop = os.path.join(os.path.expanduser('~'), 'Desktop')
        filepath = os.path.join(desktop, filename)
        base, ext = os.path.splitext(filename)
        counter = 1
        while os.path.exists(filepath):
            filepath = os.path.join(desktop, f"{base}_{counter}{ext}")
            counter += 1
            
        file_bytes = base64.b64decode(base64_data)
        with open(filepath, 'wb') as f:
            f.write(file_bytes)
        print(f"[FILE] Dosya başarıyla kaydedildi: {filepath}")
    except Exception as e:
        print(f"[FILE] Dosya kaydetme hatası: {e}")

if __name__ == '__main__':
    threading.Thread(target=server_video_thread, daemon=True).start()
    threading.Thread(target=server_audio_thread, daemon=True).start()
    threading.Thread(target=server_audio_reverse_thread, daemon=True).start()
    threading.Thread(target=server_control_thread, daemon=True).start()
    
    eel.start('index.html', size=(850, 560), block=False)
    
    while True:
        if session.active_client_ip and not session.approval_event.is_set() and not session.reject_event.is_set():
            eel.show_connection_request(session.active_client_ip)()
            session.active_client_ip = None
        eel.sleep(0.1)
