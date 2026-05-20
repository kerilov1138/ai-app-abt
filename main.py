"""
YouTube Video Analiz & Yorumlayıcı
Mobil Uyumlu Python Uygulaması — Kivy Tabanlı
"""

import subprocess, sys, os

# ─── Otomatik bağımlılık yükleme ───
def _ensure(pkg, imp=None):
    try:
        __import__(imp or pkg)
    except ImportError:
        print(f"  ↳ '{pkg}' yükleniyor...")
        subprocess.check_call(
            [sys.executable, '-m', 'pip', 'install', pkg],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )

# Kivy ve diğer gerekli kütüphanelerin kurulduğundan emin ol
_ensure('kivy')
_ensure('google-genai', 'google.genai')
_ensure('youtube-transcript-api', 'youtube_transcript_api')
_ensure('requests')

# Kivy Pencere Ayarları - Mobil görünüm simülasyonu (Desktop için)
from kivy.config import Config
Config.set('graphics', 'width', '450')
Config.set('graphics', 'height', '800')
Config.set('graphics', 'minimum_width', '350')
Config.set('graphics', 'minimum_height', '600')

import re, json, threading, time
from urllib.parse import urlparse, parse_qs
import requests
from google import genai
from google.genai import types
from youtube_transcript_api import YouTubeTranscriptApi

# Kivy UI Bileşenleri
from kivy.app import App
from kivy.lang import Builder
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.widget import Widget
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.togglebutton import ToggleButton
from kivy.uix.scrollview import ScrollView
from kivy.uix.popup import Popup
from kivy.properties import StringProperty, NumericProperty, BooleanProperty, ObjectProperty
from kivy.clock import Clock
from kivy.metrics import dp

# ═══════════════════════════════════════════
#  Sabitler & Temalar
# ═══════════════════════════════════════════
API_KEY = 'AIzaSyDbT0aoruz3QmhXqVlAlYR7wurcSKDqCkE'
MODEL   = 'gemini-2.5-flash'

SYSTEM = (
    'Sen bir YouTube Video Analiz Asistanısın. '
    'Görevin, sana verilen video transkriptini analiz etmek ve '
    'kullanıcıya yardımcı olmaktır. Türkçe yanıt ver. Markdown formatı kullan. '
    'Yanıtlarında şu formatlama kurallarına uy: '
    '- Bölüm başlıkları için ## kullan. '
    '- Önemli kavramları **kalın** yaz. '
    '- Madde listeleri için - kullan. '
    '- Paragraflar arasında boşluk bırak. '
    '- Akıcı, profesyonel ve kolay okunur bir dil kullan. '
    'ÖNEMLİ KURALLAR: '
    '1) Eğer kullanıcının sorusu video içeriğiyle doğrudan ilişkili değilse, '
    'belirsizse veya transkriptte karşılığı yoksa, ASLA "bunu yapamam" veya '
    '"bilgi bulunamadı" gibi sert bir ret mesajı verme. '
    'Bunun yerine, yardımsever bir kısa açıklama yaz ve ardından '
    '"Bu konularla ilgili sorabilirsiniz:" diyerek en fazla 3 alternatif soru öner. '
    '2) Her öneriyi şu formatta yaz: <<ÖNERİ: alternatif soru metni>> '
    '3) Öneriler, videonun gerçek içeriğine dayalı ve kullanıcının niyetine '
    'yakın konularda olsun. '
    '4) Eğer soru videoyla tamamen alakasızsa bile, videonun ana konularından '
    '3 tane ilgili soru öner. '
    '5) Eğer soru açık ve videoya uygunsa, direkt yanıtla; öneri ekleme zorunlu değil.'
)


# ═══════════════════════════════════════════
#  Backend Logic (Birebir Aynı)
# ═══════════════════════════════════════════
class Analyzer:
    def __init__(self):
        self.config_file = 'config.json'
        self.api_key = self.load_api_key()
        self.client = genai.Client(api_key=self.api_key)
        self.ytt_api = YouTubeTranscriptApi()
        self.chat = None
        self.transcript = ''
        self.title = ''

    def load_api_key(self):
        try:
            if os.path.exists(self.config_file):
                with open(self.config_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    return data.get('api_key', API_KEY)
        except Exception:
            pass
        return API_KEY

    def save_api_key(self, key):
        self.api_key = key.strip() if key.strip() else API_KEY
        self.client = genai.Client(api_key=self.api_key)
        try:
            with open(self.config_file, 'w', encoding='utf-8') as f:
                json.dump({'api_key': self.api_key}, f, indent=4)
        except Exception:
            pass

    @staticmethod
    def video_id(url):
        p = urlparse(url)
        if p.hostname in ('youtu.be',):
            return p.path[1:]
        if p.hostname in ('www.youtube.com', 'youtube.com', 'm.youtube.com'):
            if p.path == '/watch':
                return parse_qs(p.query).get('v', [None])[0]
            for prefix in ('/embed/', '/v/', '/shorts/'):
                if p.path.startswith(prefix):
                    return p.path.split('/')[2]
        return None

    def fetch_info(self, url):
        try:
            r = requests.get(f'https://www.youtube.com/oembed?url={url}&format=json', timeout=10)
            self.title = r.json().get('title', 'Bilinmeyen Video')
        except Exception:
            self.title = 'Bilinmeyen Video'
        return self.title

    def fetch_transcript(self, vid):
        try:
            snippets = self.ytt_api.fetch(vid, languages=['tr', 'en'])
        except Exception:
            snippets = self.ytt_api.fetch(vid, languages=['en'])
        self.transcript = ' '.join(s.text for s in snippets)
        return self.transcript

    def summarize(self, long_ver=False):
        self.chat = self.client.chats.create(
            model=MODEL,
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM,
                temperature=0.7,
                max_output_tokens=4096,
            ),
        )
        note = ('Detaylı ve kapsamlı bir özet yap. Önemli noktaları madde madde listele.'
                if long_ver else 'Kısa ve öz bir özet yap. En önemli 3-5 noktayı vurgula.')
        prompt = f'İşte bir YouTube videosunun transkript metni. {note}\n\n--- TRANSKRİPT BAŞLANGIÇ ---\n{self.transcript}\n--- TRANSKRİPT BİTİŞ ---'
        return self._retry_send(prompt)

    def send(self, msg):
        if not self.chat:
            raise Exception('Önce bir video analiz edin.')
        return self._retry_send(msg)

    def _retry_send(self, msg, max_retries=5):
        for attempt in range(max_retries):
            try:
                resp = self.chat.send_message(msg)
                return resp.text or 'Yanıt alınamadı.'
            except Exception as e:
                err_str = str(e)
                is_transient = any(k in err_str for k in ('503', 'UNAVAILABLE', '429', 'overloaded', 'high demand', 'RESOURCE_EXHAUSTED'))
                if is_transient and attempt < max_retries - 1:
                    retry_match = re.search(r'retry(?:Delay)?.*?(\d+)', err_str, re.IGNORECASE)
                    if retry_match:
                        wait = int(retry_match.group(1)) + 2
                    else:
                        wait = 15 * (attempt + 1)
                    time.sleep(wait)
                    continue
                raise

    @staticmethod
    def parse_suggestions(text):
        pat = re.compile(r'<<ÖNERİ:\s*(.+?)>>')
        sug = pat.findall(text)
        clean = pat.sub('', text).strip()
        return clean, sug

    @staticmethod
    def generate_fallback_suggestions(query):
        fallbacks = [
            f'Bu videoda "{query[:30]}" konusu ele alınıyor mu?',
            'Videonun ana konusu nedir?',
            'Videodaki en önemli noktalar nelerdir?',
        ]
        return fallbacks


# ═══════════════════════════════════════════
#  Markdown → Kivy Rich Text Çevirici
# ═══════════════════════════════════════════
def markdown_to_kivy(md_text):
    lines = md_text.split('\n')
    kivy_lines = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('### '):
            text = stripped[4:]
            text = parse_inline_kivy(text)
            kivy_lines.append(f"[size=16sp][b]{text}[/b][/size]")
        elif stripped.startswith('## '):
            text = stripped[3:]
            text = parse_inline_kivy(text)
            kivy_lines.append(f"[size=18sp][b][color=BB86FC]{text}[/color][/b][/size]")
        elif stripped.startswith('# '):
            text = stripped[2:]
            text = parse_inline_kivy(text)
            kivy_lines.append(f"[size=22sp][b][color=BB86FC]{text}[/color][/b][/size]")
        elif stripped.startswith('- ') or stripped.startswith('* '):
            text = stripped[2:]
            text = parse_inline_kivy(text)
            kivy_lines.append(f"  [color=BB86FC]•[/color] {text}")
        elif stripped.startswith('---') or stripped.startswith('***'):
            kivy_lines.append("[color=302B63]────────────────────────────────────────[/color]")
        elif stripped == '':
            kivy_lines.append('')
        else:
            text = parse_inline_kivy(stripped)
            kivy_lines.append(text)
    return '\n'.join(kivy_lines)


def parse_inline_kivy(line):
    # Kivy'nin yerleşik markup işaretlerini bozmamak için önce köşeli parantezleri escape edelim
    line = line.replace('[', '[[').replace(']', ']]')
    
    # **kalın** -> [b]kalın[/b]
    # *italik* -> [i][color=9B59B6]italik[/color][/i]
    parts = re.split(r'(\*\*.*?\*\*|\*.*?\*)', line)
    new_parts = []
    for part in parts:
        if part.startswith('**') and part.endswith('**'):
            new_parts.append(f"[b]{part[2:-2]}[/b]")
        elif part.startswith('*') and part.endswith('*'):
            new_parts.append(f"[i][color=9B59B6]{part[1:-1]}[/color][/i]")
        else:
            new_parts.append(part)
    return ''.join(new_parts)


# ═══════════════════════════════════════════
#  Kivy Modern Popup Yardımcısı
# ═══════════════════════════════════════════
def show_popup(title, message, is_error=False):
    content = BoxLayout(orientation='vertical', padding=dp(15), spacing=dp(10))
    msg_lbl = Label(
        text=message, 
        halign='center', 
        valign='middle', 
        markup=True,
        color=[1, 1, 1, 1] if not is_error else [1, 0.4, 0.4, 1]
    )
    msg_lbl.bind(size=msg_lbl.setter('text_size'))
    content.add_widget(msg_lbl)
    
    close_btn = Button(
        text='Tamam', 
        size_hint_y=None, 
        height=dp(40),
        background_color=[0, 0, 0, 0],
        color=[15/255, 12/255, 41/255, 1],
        bold=True
    )
    with close_btn.canvas.before:
        from kivy.graphics import Color, RoundedRectangle
        Color(187/255, 134/255, 252/255, 1)
        close_btn.bg_rect = RoundedRectangle(radius=[6, 6, 6, 6])
    close_btn.bind(pos=lambda obj, val: setattr(obj.bg_rect, 'pos', val))
    close_btn.bind(size=lambda obj, val: setattr(obj.bg_rect, 'size', val))
    
    content.add_widget(close_btn)
    
    popup = Popup(
        title=title,
        content=content,
        size_hint=(0.85, 0.35),
        title_color=[187/255, 134/255, 252/255, 1],
        background_color=[30/255, 26/255, 58/255, 0.95]
    )
    close_btn.bind(on_release=popup.dismiss)
    popup.open()


# ═══════════════════════════════════════════
#  Kivy Custom Arayüz Bileşenleri (KV String)
# ═══════════════════════════════════════════
KV_LAYOUT = """
#:import dp kivy.metrics.dp

<ModernTextInput@TextInput>:
    background_color: [0, 0, 0, 0]
    foreground_color: [1, 1, 1, 1]
    cursor_color: [187/255, 134/255, 252/255, 1]
    hint_text_color: [179/255, 179/255, 179/255, 0.5]
    padding: [14, 10, 14, 10]
    multiline: False
    size_hint_y: None
    height: dp(42)
    canvas.before:
        Color:
            rgba: [37/255, 32/255, 64/255, 1]
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [8, 8, 8, 8]
        Color:
            rgba: [48/255, 43/255, 99/255, 1] if not self.focus else [187/255, 134/255, 252/255, 1]
        Line:
            rounded_rectangle: [self.x, self.y, self.width, self.height, 8]
            width: 1

<ModernButton@Button>:
    background_color: [0, 0, 0, 0]
    color: [15/255, 12/255, 41/255, 1]
    bold: True
    size_hint_y: None
    height: dp(42)
    canvas.before:
        Color:
            rgba: [187/255, 134/255, 252/255, 1] if self.state == 'normal' else [155/255, 89/255, 182/255, 1]
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [8, 8, 8, 8]

<ModernToggleButton@ToggleButton>:
    background_color: [0, 0, 0, 0]
    color: [187/255, 134/255, 252/255, 1] if self.state == 'normal' else [15/255, 12/255, 41/255, 1]
    bold: True
    size_hint_y: None
    height: dp(36)
    canvas.before:
        Color:
            rgba: [26/255, 5/255, 51/255, 1] if self.state == 'normal' else [187/255, 134/255, 252/255, 1]
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [6, 6, 6, 6]
        Color:
            rgba: [187/255, 134/255, 252/255, 1]
        Line:
            rounded_rectangle: [self.x, self.y, self.width, self.height, 6]
            width: 1

BoxLayout:
    orientation: 'vertical'
    canvas.before:
        Color:
            rgba: [15/255, 12/255, 41/255, 1]
        Rectangle:
            pos: self.pos
            size: self.size

    # Header
    BoxLayout:
        orientation: 'horizontal'
        size_hint_y: None
        height: dp(56)
        padding: [16, 8, 16, 8]
        spacing: 10
        Label:
            text: '🎬 YouTube Analiz'
            font_size: '18sp'
            bold: True
            color: [187/255, 134/255, 252/255, 1]
            halign: 'left'
            valign: 'middle'
            text_size: self.size
        Button:
            text: '⚙️ Ayarlar'
            size_hint_x: None
            width: dp(90)
            size_hint_y: None
            height: dp(36)
            background_color: [0, 0, 0, 0]
            color: [187/255, 134/255, 252/255, 1] if app.settings_height == 0 else [15/255, 12/255, 41/255, 1]
            bold: True
            pos_hint: {'center_y': 0.5}
            canvas.before:
                Color:
                    rgba: [30/255, 26/255, 58/255, 1] if app.settings_height == 0 else [187/255, 134/255, 252/255, 1]
                RoundedRectangle:
                    pos: self.pos
                    size: self.size
                    radius: [6, 6, 6, 6]
            on_release: app.toggle_settings()

    # Settings Panel (Collapsible)
    BoxLayout:
        id: settings_panel
        orientation: 'vertical'
        size_hint_y: None
        height: app.settings_height
        opacity: 1 if app.settings_height > 0 else 0
        disabled: app.settings_height == 0
        padding: [16, 4, 16, 4]
        spacing: 6
        canvas.before:
            Color:
                rgba: [26/255, 5/255, 51/255, 1]
            Rectangle:
                pos: self.pos
                size: self.size
        
        BoxLayout:
            orientation: 'horizontal'
            spacing: 8
            Label:
                text: 'API Anahtarı:'
                size_hint_x: None
                width: dp(80)
                font_size: '12sp'
                bold: True
                color: [1, 1, 1, 1]
                halign: 'left'
                valign: 'middle'
                text_size: self.size
            ModernTextInput:
                id: api_entry
                password: True
                text: app.api_key
                size_hint_y: None
                height: dp(32)
                padding: [8, 6, 8, 6]
        
        BoxLayout:
            orientation: 'horizontal'
            spacing: 10
            size_hint_y: None
            height: dp(32)
            Widget:
            Button:
                text: 'Kaydet'
                size_hint_x: None
                width: dp(70)
                background_color: [0, 0, 0, 0]
                color: [15/255, 12/255, 41/255, 1]
                bold: True
                canvas.before:
                    Color:
                        rgba: [187/255, 134/255, 252/255, 1]
                    RoundedRectangle:
                        pos: self.pos
                        size: self.size
                        radius: [6, 6, 6, 6]
                on_release: app.save_api_key(api_entry.text)
            Button:
                text: 'Sıfırla'
                size_hint_x: None
                width: dp(70)
                background_color: [0, 0, 0, 0]
                color: [187/255, 134/255, 252/255, 1]
                bold: True
                canvas.before:
                    Color:
                        rgba: [30/255, 26/255, 58/255, 1]
                    RoundedRectangle:
                        pos: self.pos
                        size: self.size
                        radius: [6, 6, 6, 6]
                on_release: app.reset_api_key()

    # URL Input Panel
    BoxLayout:
        orientation: 'horizontal'
        size_hint_y: None
        height: dp(54)
        padding: [16, 4, 16, 4]
        spacing: 8
        ModernTextInput:
            id: url_entry
            hint_text: 'YouTube video URL yapıştırın...'
            text: ''
        ModernButton:
            id: analyze_btn
            text: '✦ Analiz'
            size_hint_x: None
            width: dp(80)
            on_release: app.analyze_video(url_entry.text)

    # Length & Status Row
    BoxLayout:
        orientation: 'horizontal'
        size_hint_y: None
        height: dp(40)
        padding: [16, 2, 16, 2]
        spacing: 6
        ModernToggleButton:
            id: short_btn
            text: 'Kısa'
            group: 'length'
            state: 'down'
            size_hint_x: None
            width: dp(60)
            allow_no_selection: False
            on_release: app.set_is_long(False)
        ModernToggleButton:
            id: long_btn
            text: 'Uzun'
            group: 'length'
            size_hint_x: None
            width: dp(60)
            allow_no_selection: False
            on_release: app.set_is_long(True)
        Label:
            text: app.status_text
            font_size: '12sp'
            color: [179/255, 179/255, 179/255, 1]
            halign: 'right'
            valign: 'middle'
            text_size: self.size

    # Scrollable Content
    ScrollView:
        id: scroll_view
        do_scroll_x: False
        do_scroll_y: True
        BoxLayout:
            id: scroll_content
            orientation: 'vertical'
            size_hint_y: None
            height: self.minimum_height
            padding: [16, 8, 16, 8]
            spacing: 12

            # Video Title
            Label:
                text: app.video_title
                font_size: '15sp'
                bold: True
                color: [1, 1, 1, 1]
                size_hint_y: None
                height: self.texture_size[1] + dp(8) if app.video_title else 0
                opacity: 1 if app.video_title else 0
                text_size: self.width, None
                halign: 'left'
                valign: 'top'

            # Line Divider
            Widget:
                size_hint_y: None
                height: dp(1) if app.video_title else 0
                opacity: 1 if app.video_title else 0
                canvas.before:
                    Color:
                        rgba: [48/255, 43/255, 99/255, 1]
                    Rectangle:
                        pos: self.pos
                        size: self.size

            # Summary Header
            BoxLayout:
                orientation: 'horizontal'
                size_hint_y: None
                height: dp(24) if app.summary_text else 0
                opacity: 1 if app.summary_text else 0
                spacing: 6
                Label:
                    text: '✦'
                    font_size: '16sp'
                    color: [187/255, 134/255, 252/255, 1]
                    size_hint_x: None
                    width: dp(16)
                Label:
                    text: 'AI Özet'
                    font_size: '15sp'
                    bold: True
                    color: [187/255, 134/255, 252/255, 1]
                    halign: 'left'
                    valign: 'middle'
                    text_size: self.size

            # Summary Box Card
            BoxLayout:
                orientation: 'vertical'
                size_hint_y: None
                height: summary_lbl.height + dp(20) if app.summary_text else 0
                opacity: 1 if app.summary_text else 0
                padding: [14, 10, 14, 10]
                canvas.before:
                    Color:
                        rgba: [30/255, 26/255, 58/255, 1]
                    RoundedRectangle:
                        pos: self.pos
                        size: self.size
                        radius: [8, 8, 8, 8]
                    Color:
                        rgba: [48/255, 43/255, 99/255, 1]
                    Line:
                        rounded_rectangle: [self.x, self.y, self.width, self.height, 8]
                        width: 1
                Label:
                    id: summary_lbl
                    text: app.summary_text
                    markup: True
                    font_size: '13sp'
                    color: [1, 1, 1, 1]
                    size_hint_y: None
                    height: self.texture_size[1]
                    text_size: self.width, None
                    halign: 'left'
                    valign: 'top'

            # Chat Header
            BoxLayout:
                orientation: 'horizontal'
                size_hint_y: None
                height: dp(24) if app.chat_visible else 0
                opacity: 1 if app.chat_visible else 0
                spacing: 6
                Label:
                    text: '💬'
                    font_size: '16sp'
                    size_hint_x: None
                    width: dp(16)
                Label:
                    text: 'Video Hakkında Soru Sor'
                    font_size: '15sp'
                    bold: True
                    color: [1, 1, 1, 1]
                    halign: 'left'
                    valign: 'middle'
                    text_size: self.size

            # Chat Container
            BoxLayout:
                id: chat_container
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: 10

    # Bottom Chat Panel (Only visible after analysis complete)
    BoxLayout:
        orientation: 'horizontal'
        size_hint_y: None
        height: dp(54) if app.chat_visible else 0
        opacity: 1 if app.chat_visible else 0
        disabled: not app.chat_visible
        padding: [16, 6, 16, 6]
        spacing: 8
        canvas.before:
            Color:
                rgba: [26/255, 5/255, 51/255, 1]
            Rectangle:
                pos: self.pos
                size: self.size
        ModernTextInput:
            id: chat_entry
            hint_text: 'Video hakkında bir şey sorun...'
            text: ''
            on_text_validate: app.send_chat(chat_entry.text); chat_entry.text = ''
        Button:
            text: '➤'
            size_hint_x: None
            width: dp(46)
            size_hint_y: None
            height: dp(42)
            background_color: [0, 0, 0, 0]
            color: [15/255, 12/255, 41/255, 1]
            font_size: '16sp'
            bold: True
            canvas.before:
                Color:
                    rgba: [187/255, 134/255, 252/255, 1]
                RoundedRectangle:
                    pos: self.pos
                    size: self.size
                    radius: [8, 8, 8, 8]
            on_release: app.send_chat(chat_entry.text); chat_entry.text = ''
"""


# ═══════════════════════════════════════════
#  Dinamik Chat Balonu & Öneri Bileşenleri
# ═══════════════════════════════════════════
class ChatBubble(BoxLayout):
    bg_color = ObjectProperty([0.176, 0.122, 0.369, 1])
    
    def __init__(self, text, is_user=False, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.size_hint_x = None
        self.size_hint_y = None
        self.padding = [dp(12), dp(8), dp(12), dp(8)]
        
        if is_user:
            self.bg_color = [45/255, 31/255, 94/255, 1]
        else:
            self.bg_color = [22/255, 18/255, 48/255, 1]
            
        with self.canvas.before:
            from kivy.graphics import Color, RoundedRectangle
            self.canvas_color = Color(rgba=self.bg_color)
            self.canvas_rect = RoundedRectangle(radius=[10, 10, 10, 10])
            
        self.bind(pos=self._update_rect, size=self._update_rect)
        
        self.label = Label(
            text=text,
            markup=True,
            font_size='13sp',
            color=[1, 1, 1, 1],
            size_hint_y=None,
            halign='left',
            valign='top'
        )
        self.label.bind(width=lambda s, w: setattr(s, 'text_size', (w, None)))
        self.label.bind(texture_size=self._update_height)
        self.add_widget(self.label)
        
    def _update_rect(self, instance, value):
        self.canvas_rect.pos = self.pos
        self.canvas_rect.size = self.size
        
    def _update_height(self, instance, size):
        self.label.height = size[1]
        self.height = self.label.height + dp(16)


class ChatBubbleWrapper(BoxLayout):
    def __init__(self, text, is_user=False, **kwargs):
        super().__init__(orientation='horizontal', size_hint_y=None, spacing=dp(10), **kwargs)
        self.bubble = ChatBubble(text, is_user=is_user)
        
        # Parent genişliği değiştikçe balon genişliğini sınırla (%75)
        self.bind(width=self._update_bubble_width)
        
        if is_user:
            self.add_widget(Widget()) # Sol boşluk (Sağa yaslama)
            self.add_widget(self.bubble)
        else:
            self.add_widget(self.bubble)
            self.add_widget(Widget()) # Sağ boşluk (Sola yaslama)
            
        self.height = self.bubble.height
        self.bubble.bind(height=self._update_height)
        
    def _update_bubble_width(self, instance, width):
        self.bubble.width = width * 0.75
        
    def _update_height(self, instance, val):
        self.height = val + dp(6)


class SuggestionCard(BoxLayout):
    def __init__(self, suggestions, app_ref, **kwargs):
        super().__init__(orientation='vertical', size_hint_y=None, padding=dp(12), spacing=dp(6), **kwargs)
        self.app_ref = app_ref
        
        with self.canvas.before:
            from kivy.graphics import Color, RoundedRectangle, Line
            Color(28/255, 22/255, 64/255, 1)
            self.bg_rect = RoundedRectangle(radius=[10, 10, 10, 10])
            Color(187/255, 134/255, 252/255, 1)
            self.bg_line = Line(width=1, radius=[10, 10, 10, 10])
            
        self.bind(pos=self._update_canvas, size=self._update_canvas)
        
        # Başlık
        hdr = BoxLayout(orientation='horizontal', size_hint_y=None, height=dp(20), spacing=dp(6))
        hdr.add_widget(Label(text='🔍', font_size='13sp', size_hint_x=None, width=dp(16)))
        hdr.add_widget(Label(
            text='Bunu mu demek istediniz?', 
            font_size='13sp', 
            bold=True, 
            color=[187/255, 134/255, 252/255, 1], 
            halign='left',
            valign='middle'
        ))
        # Başlık hizalama desteği
        hdr.children[0].bind(size=hdr.children[0].setter('text_size'))
        self.add_widget(hdr)
        
        self.btn_height = dp(36)
        self.total_buttons = len(suggestions)
        
        for s in suggestions:
            btn = Button(
                text=f'→  {s}',
                font_size='12sp',
                color=[0.88, 0.88, 0.88, 1],
                size_hint_y=None,
                height=self.btn_height,
                background_color=[0, 0, 0, 0],
                halign='left',
                valign='middle',
                padding=[dp(10), dp(2)]
            )
            btn.bind(size=btn.setter('text_size'))
            
            with btn.canvas.before:
                Color(30/255, 26/255, 58/255, 1)
                btn.bg_rect = RoundedRectangle(radius=[6, 6, 6, 6])
                Color(48/255, 43/255, 99/255, 1)
                btn.bg_line = Line(width=1, radius=[6, 6, 6, 6])
                
            btn.bind(pos=self._update_btn_canvas, size=self._update_btn_canvas)
            btn.bind(on_release=self._on_btn_click)
            self.add_widget(btn)
            
        self.height = dp(20 + 8) + (self.btn_height + dp(6)) * self.total_buttons + dp(12)
        
    def _update_canvas(self, instance, value):
        self.bg_rect.pos = self.pos
        self.bg_rect.size = self.size
        self.bg_line.rounded_rectangle = [self.x, self.y, self.width, self.height, 10]
        
    def _update_btn_canvas(self, btn, value):
        btn.bg_rect.pos = btn.pos
        btn.bg_rect.size = btn.size
        btn.bg_line.rounded_rectangle = [btn.x, btn.y, btn.width, btn.height, 6]
        
    def _on_btn_click(self, btn):
        text = btn.text[3:].strip()
        self.app_ref.send_chat(text)


# ═══════════════════════════════════════════
#  Kivy Main App Sınıfı
# ═══════════════════════════════════════════
class MainApp(App):
    settings_height = NumericProperty(0)
    api_key = StringProperty('')
    status_text = StringProperty('')
    video_title = StringProperty('')
    summary_text = StringProperty('')
    chat_visible = BooleanProperty(False)
    
    def build(self):
        self.title = 'YouTube Video Analiz & Yorumlayıcı'
        self.analyzer = Analyzer()
        self.api_key = self.analyzer.api_key
        self.is_long = False
        
        # Arayüz tasarım şablonunu yükle
        root = Builder.load_string(KV_LAYOUT)
        return root

    def toggle_settings(self):
        if self.settings_height == 0:
            self.settings_height = dp(76)
        else:
            self.settings_height = 0

    def save_api_key(self, key):
        if not key.strip():
            show_popup('Uyarı', 'Lütfen geçerli bir API Anahtarı girin.', is_error=True)
            return
        self.analyzer.save_api_key(key)
        self.api_key = self.analyzer.api_key
        show_popup('Başarılı', 'Gemini API Anahtarı başarıyla güncellendi!')
        self.toggle_settings()

    def reset_api_key(self):
        self.analyzer.save_api_key(API_KEY)
        self.api_key = self.analyzer.api_key
        self.root.ids.api_entry.text = self.api_key
        show_popup('Sıfırlandı', 'API Anahtarı varsayılan sisteme sıfırlandı.')
        self.toggle_settings()

    def set_is_long(self, val):
        self.is_long = val

    def analyze_video(self, url):
        url = url.strip()
        if not url or 'URL' in url:
            show_popup('Uyarı', 'Lütfen bir YouTube URL girin.', is_error=True)
            return
        vid = self.analyzer.video_id(url)
        if not vid:
            show_popup('Hata', 'Geçersiz YouTube URL.', is_error=True)
            return
            
        self.root.ids.analyze_btn.disabled = True
        self.root.ids.analyze_btn.text = '⏳ ...'
        self.status_text = 'Video bilgileri alınıyor...'
        
        # Eski sohbet ve özetleri temizle
        self.root.ids.chat_container.clear_widgets()
        self.chat_visible = False
        self.summary_text = ''
        self.video_title = ''
        
        def task():
            try:
                self.analyzer.fetch_info(url)
                Clock.schedule_once(lambda dt: setattr(self, 'video_title', f'📺  {self.analyzer.title}'))
                Clock.schedule_once(lambda dt: setattr(self, 'status_text', 'Transkript çekiliyor...'))
                
                self.analyzer.fetch_transcript(vid)
                Clock.schedule_once(lambda dt: setattr(self, 'status_text', 'Gemini ile özetleniyor...'))
                
                summary = self.analyzer.summarize(long_ver=self.is_long)
                clean, _ = self.analyzer.parse_suggestions(summary)
                
                kivy_summary = markdown_to_kivy(clean)
                Clock.schedule_once(lambda dt: setattr(self, 'summary_text', kivy_summary))
                Clock.schedule_once(lambda dt: setattr(self, 'status_text', '✓ Analiz tamamlandı'))
                Clock.schedule_once(lambda dt: setattr(self, 'chat_visible', True))
            except Exception as e:
                Clock.schedule_once(lambda dt: show_popup('Hata', str(e), is_error=True))
                Clock.schedule_once(lambda dt: setattr(self, 'status_text', ''))
            finally:
                def restore_btn(dt):
                    self.root.ids.analyze_btn.disabled = False
                    self.root.ids.analyze_btn.text = '✦ Analiz'
                Clock.schedule_once(restore_btn)
                
        threading.Thread(target=task, daemon=True).start()

    def send_chat(self, msg):
        msg = msg.strip()
        if not msg or 'sorun' in msg:
            return
            
        self.add_bubble(msg, is_user=True)
        
        def task():
            try:
                resp = self.analyzer.send(msg)
                clean, suggestions = self.analyzer.parse_suggestions(resp)
                kivy_clean = markdown_to_kivy(clean)
                Clock.schedule_once(lambda dt: self.add_bubble(kivy_clean, is_user=False))
                if suggestions:
                    Clock.schedule_once(lambda dt: self.add_suggestions(suggestions), 0.1)
            except Exception as e:
                err_str = str(e)
                is_no_chat = 'Önce bir video' in err_str
                if is_no_chat:
                    Clock.schedule_once(lambda dt: self.add_bubble(
                        '⚠️ Henüz bir video analiz edilmedi. Lütfen önce bir YouTube URL girin ve analiz edin.',
                        is_user=False))
                else:
                    fallbacks = self.analyzer.generate_fallback_suggestions(msg)
                    Clock.schedule_once(lambda dt: self.add_bubble(
                        '⚠️ Yanıt alınırken bir sorun oluştu. Aşağıdaki alternatif sorulardan birini deneyebilirsiniz:',
                        is_user=False))
                    Clock.schedule_once(lambda dt: self.add_suggestions(fallbacks), 0.1)
                    
        threading.Thread(target=task, daemon=True).start()

    def add_bubble(self, text, is_user=False):
        wrapper = ChatBubbleWrapper(text, is_user=is_user)
        self.root.ids.chat_container.add_widget(wrapper)
        self.scroll_to_bottom()

    def add_suggestions(self, suggestions):
        card = SuggestionCard(suggestions, self)
        self.root.ids.chat_container.add_widget(card)
        self.scroll_to_bottom()

    def scroll_to_bottom(self):
        def scroll(dt):
            self.root.ids.scroll_view.scroll_y = 0
        Clock.schedule_once(scroll, 0.1)


# ═══════════════════════════════════════════
if __name__ == '__main__':
    MainApp().run()
