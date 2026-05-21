"""
YouTube Video Analiz & Yorumlayıcı
Python Masaüstü Uygulaması — Flet (Flutter) tabanlı
"""

import subprocess
import sys
import os

# ─── Otomatik Bağımlılık Yükleme ───
def _ensure(pkg, imp=None):
    try:
        __import__(imp or pkg)
    except ImportError:
        print(f"  ↳ '{pkg}' yükleniyor...")
        subprocess.check_call(
            [sys.executable, '-m', 'pip', 'install', pkg],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )

_ensure('flet')
_ensure('google-genai', 'google.genai')
_ensure('youtube-transcript-api', 'youtube_transcript_api')
_ensure('requests')

import flet as ft
import requests
import re
import json
import threading
import time
from urllib.parse import urlparse, parse_qs
from google import genai
from google.genai import types
from youtube_transcript_api import YouTubeTranscriptApi

# ═══════════════════════════════════════════
#  Sabitler
# ═══════════════════════════════════════════
API_KEY = 'AIzaSyDbT0aoruz3QmhXqVlAlYR7wurcSKDqCkE'
MODEL   = 'gemini-2.5-flash'

C = {
    'bg':       '#0F0C29',
    'bg2':      '#1A0533',
    'card':     '#1E1A3A',
    'purple':   '#BB86FC',
    'purple_d': '#9B59B6',
    'white':    '#FFFFFF',
    'gray':     '#B3B3B3',
    'input':    '#252040',
    'user_bg':  '#2D1F5E',
    'ai_bg':    '#161230',
    'border':   '#302B63',
}

SYSTEM = (
    'Sen bir YouTube Video Analiz Asistanısın. '
    'Görevin, SADECE ve YALNIZCA sana verilen video transkriptini analiz etmek ve kullanıcıya yardımcı olmaktır. '
    'Sadece transkriptte geçen konular hakkında konuşabilirsin. Transkript dışında hiçbir konuda bilgi veremezsin, '
    'soru cevaplayamazsın veya genel sohbet edemezsin. Türkçe yanıt ver. Markdown formatı kullan. '
    'Yanıtlarında şu formatlama kurallarına uy: '
    '- Bölüm başlıkları için ## kullan. '
    '- Önemli kavramları **kalın** yaz. '
    '- Madde listeleri için - kullan. '
    '- Paragraflar arasında boşluk bırak. '
    'ÖNEMLİ KURALLAR:\n'
    '1) EĞER KULLANICI VİDEO DIŞI/ALAKASIZ BİR SORU SORARSA:\n'
    '   Kesinlikle soruyu cevaplama. Kibarca bu sorunun video içeriğiyle ilgili olmadığını belirt. '
    '   Ardından, "Video içeriğine dayanarak şu soruları sorabilirsiniz:" diyerek video ile %100 ilgili en fazla 3 alternatif soru öner.\n'
    '2) EĞER KULLANICI VİDEO İLE İLGİLİ AMA VİDEODA TAM OLARAK CEVABI OLMAYAN BİR SORU SORARSA (veya hatalı/eksik bir soru sorarsa):\n'
    '   Kullanıcıya bu spesifik sorunun cevabının videoda tam olarak geçmediğini güzelce açıkla. '
    '   Ardından "Bunu mu demek istediniz?" diyerek, kullanıcının niyetine en yakın ve SADECE videoda geçen konulara dayalı en fazla 3 alternatif soru öner.\n'
    '3) ÖNERİ FORMATI:\n'
    '   Her öneriyi mutlaka tam olarak şu formatta yazmalısın: <<ÖNERİ: alternatif soru metni>>\n'
    '4) Sınırlama: Kesinlikle video dışına çıkma. Hem sen hem de kullanıcı sadece video içeriği ve kullanıcı tarafından konulan URL hakkında konuşabilirsiniz.'
)

# Clipboard Helper function (independent from Flet version changes)
def set_clipboard_text(text):
    try:
        import tkinter as tk
        r = tk.Tk()
        r.withdraw()
        r.clipboard_clear()
        r.clipboard_append(text)
        r.update()
        r.destroy()
        return True
    except Exception:
        return False

# ═══════════════════════════════════════════
#  Backend
# ═══════════════════════════════════════════
class CallableString(str):
    def __new__(cls, value):
        return super().__new__(cls, value)
    
    def __call__(self, url):
        from urllib.parse import urlparse, parse_qs
        try:
            p = urlparse(url)
            if p.hostname in ('youtu.be',):
                return p.path[1:]
            if p.hostname in ('www.youtube.com', 'youtube.com', 'm.youtube.com'):
                if p.path == '/watch':
                    return parse_qs(p.query).get('v', [None])[0]
                for prefix in ('/embed/', '/v/', '/shorts/'):
                    if p.path.startswith(prefix):
                        return p.path.split('/')[2]
        except Exception:
            pass
        return None

class Analyzer:
    def __init__(self):
        self.config_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'config.json')
        self.api_key = self.load_api_key()
        self.client = None
        if self.api_key:
            self.init_client()
        self.chat = None
        self.transcript = ''
        self.title = ''
        self.author = ''
        self.thumbnail_url = ''
        self.video_id = CallableString('')

    def init_client(self):
        self.client = genai.Client(api_key=self.api_key)

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
        self.init_client()
        try:
            with open(self.config_file, 'w', encoding='utf-8') as f:
                json.dump({'api_key': self.api_key}, f, indent=4)
        except Exception:
            pass

    def clear_api_key(self):
        self.api_key = ''
        self.client = None
        try:
            if os.path.exists(self.config_file):
                os.remove(self.config_file)
        except Exception:
            pass

    @staticmethod
    def get_video_id(url):
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

    def fetch_video_info(self, url):
        self.video_id = CallableString(self.get_video_id(url) or '')
        if not self.video_id:
            raise Exception('Geçersiz YouTube URL')
        try:
            r = requests.get(f'https://www.youtube.com/oembed?url={url}&format=json', timeout=10)
            data = r.json()
            self.title = data.get('title', 'Bilinmeyen Video')
            self.author = data.get('author_name', 'Bilinmeyen Kanal')
            self.thumbnail_url = data.get('thumbnail_url', f'https://img.youtube.com/vi/{self.video_id}/sddefault.jpg')
        except Exception:
            self.title = 'Bilinmeyen Video'
            self.author = 'Bilinmeyen Kanal'
            self.thumbnail_url = f'https://img.youtube.com/vi/{self.video_id}/sddefault.jpg'
        return self.title

    def fetch_transcript(self):
        try:
            api = YouTubeTranscriptApi()
            tl = api.list(self.video_id)
            try:
                t = tl.find_transcript(['tr'])
            except Exception:
                try:
                    t = tl.find_transcript(['en'])
                except Exception:
                    t = next(iter(tl))
            snippets = t.fetch()
            self.transcript = ' '.join(s.get('text', '') if isinstance(s, dict) else getattr(s, 'text', '') for s in snippets)
        except Exception as e:
            raise Exception(f'Altyazı alınamadı: {str(e)}')
        return self.transcript

    def summarize(self, long_ver=False):
        if not self.client:
            raise Exception('API istemcisi başlatılamadı. API Key kontrol edin.')
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
        return [
            f'Bu videoda "{query[:30]}" konusu ele alınıyor mu?',
            'Videonun ana konusu nedir?',
            'Videodaki en önemli noktalar nelerdir?',
        ]

# ═══════════════════════════════════════════
#  UI (Flet)
# ═══════════════════════════════════════════
def main(page: ft.Page):
    page.title = "YouTube Video Analiz & Yorumlayıcı"
    page.theme_mode = ft.ThemeMode.DARK
    page.padding = 0
    page.bgcolor = C['bg']
    
    # Pencere Boyutları
    page.window_width = 960
    page.window_height = 800
    page.window_min_width = 750
    page.window_min_height = 600

    analyzer = Analyzer()

    # Uygulama Durumu
    state = type('State', (), {
        'length': 'short',          # 'short' veya 'long'
        'is_loading': False,        # Video analiz yükleniyor mu
        'is_chat_loading': False,   # Soru yükleniyor mu
        'ai_summary': '',
        'summary_suggestions': [],  # Özet altındaki öneri soruları
        'chat_messages': [],        # [{'text': str, 'is_user': bool, 'suggestions': list}]
        'current_view': 'home',     # 'home' veya 'result'
        'analysis_completed': False,
        'progress_value': 0.0,
        'progress_status': '',
    })()

    # UI Bileşenleri
    url_input = ft.TextField(
        hint_text="https://youtube.com/watch?v=...",
        prefix_icon=ft.Icons.LINK,
        bgcolor="#77302B63",
        border_color=ft.Colors.TRANSPARENT,
        focused_border_color=C['purple'],
        border_radius=16,
        text_style=ft.TextStyle(color=ft.Colors.WHITE),
        hint_style=ft.TextStyle(color=C['gray']),
        expand=True,
    )

    api_key_input = ft.TextField(
        hint_text="Google AI Studio API Key...",
        prefix_icon=ft.Icons.VPN_KEY,
        password=True,
        can_reveal_password=True,
        bgcolor="#77302B63",
        border_color=ft.Colors.TRANSPARENT,
        focused_border_color=C['purple'],
        border_radius=16,
        text_style=ft.TextStyle(color=ft.Colors.WHITE),
        hint_style=ft.TextStyle(color=C['gray']),
    )

    chat_input = ft.TextField(
        hint_text="Video hakkında bir şey sorun...",
        bgcolor="#77302B63",
        border_color=ft.Colors.TRANSPARENT,
        focused_border_color=C['purple'],
        border_radius=16,
        text_style=ft.TextStyle(color=ft.Colors.WHITE),
        hint_style=ft.TextStyle(color=C['gray']),
        expand=True,
        on_submit=lambda _: send_chat_message(),
    )

    # Kalıcı Progress Kontrolleri (thread-safe güncellemeler için)
    _progress_status_text = ft.Text("", size=13, color=C['gray'], weight=ft.FontWeight.W_500)
    _progress_pct_text = ft.Text("%0", size=14, color="#BB86FC", weight=ft.FontWeight.BOLD)
    _progress_bar = ft.ProgressBar(value=0, color="#BB86FC", bgcolor="#1A0533", height=8, border_radius=4)
    _action_container = ft.Container(width=float("inf"))

    # Containers for dynamic UI updates
    main_container = ft.Container(expand=True)
    chat_list_column = ft.Column(scroll=ft.ScrollMode.AUTO, expand=True, spacing=14)

    # Snackbar Helper
    def show_toast(text, is_error=True):
        page.snack_bar = ft.SnackBar(
            content=ft.Text(text, color=ft.Colors.WHITE),
            bgcolor=ft.Colors.RED_800 if is_error else ft.Colors.GREEN_800,
            action=ft.SnackBarAction(label="Kapat", text_color=C['purple']),
        )
        page.snack_bar.open = True
        page.update()

    # Toggle Length Action
    def set_length(length_type):
        state.length = length_type
        refresh_ui()

    # Reset API Key Dialog
    def show_reset_dialog(e):
        dialog_key_input = ft.TextField(
            value=analyzer.api_key,
            password=True,
            can_reveal_password=True,
            bgcolor="#77302B63",
            border_color=ft.Colors.TRANSPARENT,
            focused_border_color=C['purple'],
            border_radius=12,
            text_style=ft.TextStyle(color=ft.Colors.WHITE),
            hint_text="Yeni API Key...",
        )
        
        def save_and_close(confirm_save):
            if confirm_save:
                new_key = dialog_key_input.value.strip()
                if not new_key:
                    show_toast("Lütfen geçerli bir API Key girin.")
                    return
                analyzer.save_api_key(new_key)
                show_toast("API Key başarıyla güncellendi.", is_error=False)
            else:
                analyzer.clear_api_key()
                show_toast("API Key silindi.", is_error=False)
            
            if hasattr(page, "close"):
                page.close(dialog)
            else:
                dialog.open = False
                page.update()
            refresh_ui()

        def cancel_dialog(_):
            if hasattr(page, "close"):
                page.close(dialog)
            else:
                dialog.open = False
                page.update()

        dialog = ft.AlertDialog(
            modal=True,
            title=ft.Text("API Key Ayarları"),
            content=ft.Column([
                ft.Text("Mevcut API Key'i güncelleyin veya tamamen silin:", size=13, color=C['gray']),
                ft.Container(height=8),
                dialog_key_input
            ], tight=True, spacing=8),
            actions=[
                ft.TextButton("İptal", on_click=cancel_dialog),
                ft.TextButton("Sil", on_click=lambda _: save_and_close(False), style=ft.ButtonStyle(color=ft.Colors.RED_ACCENT)),
                ft.TextButton("Kaydet", on_click=lambda _: save_and_close(True), style=ft.ButtonStyle(color=C['purple'])),
            ],
            actions_alignment=ft.MainAxisAlignment.END,
        )
        
        if hasattr(page, "open"):
            page.open(dialog)
        else:
            if dialog not in page.overlay:
                page.overlay.append(dialog)
            dialog.open = True
            page.update()

    # Save API Key Action
    def save_api_key(e):
        key = api_key_input.value.strip()
        if not key:
            show_toast("Lütfen geçerli bir API Key girin.")
            return
        analyzer.save_api_key(key)
        show_toast("API Key başarıyla kaydedildi.", is_error=False)
        refresh_ui()

    # Result Screen Transition Click Handler
    def show_result_view_clicked(e):
        state.current_view = 'result'
        refresh_ui()

    # ─── Hafif Progress Güncelleyiciler (thread-safe) ───
    def update_progress_display():
        """Sadece progress kontrollerinin değerlerini günceller. UI ağacını yeniden oluşturmaz."""
        try:
            _progress_status_text.value = state.progress_status
            _progress_pct_text.value = f"%{int(state.progress_value * 100)}"
            _progress_bar.value = state.progress_value
            page.update()
        except Exception:
            pass

    def update_action_area():
        """action_container içeriğini mevcut state'e göre günceller."""
        if state.is_loading:
            _action_container.content = ft.Column([
                ft.Row([_progress_status_text, _progress_pct_text],
                       alignment=ft.MainAxisAlignment.SPACE_BETWEEN),
                _progress_bar,
            ], spacing=8)
        elif state.analysis_completed:
            _action_container.content = ft.Column([
                ft.Button(
                    content=ft.Row([
                        ft.Icon(ft.Icons.CHECK_CIRCLE, size=18, color="#0F0C29"),
                        ft.Text("Analiz Sonucu Tıkla", weight=ft.FontWeight.BOLD, color="#0F0C29"),
                    ], alignment=ft.MainAxisAlignment.CENTER),
                    bgcolor=ft.Colors.GREEN_400,
                    style=ft.ButtonStyle(shape=ft.RoundedRectangleBorder(radius=12)),
                    on_click=show_result_view_clicked,
                    height=52,
                ),
                ft.Button(
                    content=ft.Row([
                        ft.Icon(ft.Icons.AUTO_AWESOME, size=18, color="#BB86FC"),
                        ft.Text("YENİDEN ANALİZ ET", weight=ft.FontWeight.BOLD, color="#BB86FC"),
                    ], alignment=ft.MainAxisAlignment.CENTER),
                    bgcolor=ft.Colors.TRANSPARENT,
                    style=ft.ButtonStyle(shape=ft.RoundedRectangleBorder(radius=12)),
                    on_click=start_analysis,
                    height=52,
                )
            ], spacing=10)
        else:
            _action_container.content = ft.Button(
                content=ft.Row([
                    ft.Icon(ft.Icons.AUTO_AWESOME, size=18, color="#0F0C29"),
                    ft.Text("ANALİZ ET", weight=ft.FontWeight.BOLD, color="#0F0C29"),
                ], alignment=ft.MainAxisAlignment.CENTER),
                bgcolor="#BB86FC",
                style=ft.ButtonStyle(shape=ft.RoundedRectangleBorder(radius=12)),
                on_click=start_analysis,
                height=52,
            )

    # Main Analysis Flow
    def start_analysis(e):
        url = url_input.value.strip()
        if not url:
            show_toast("Lütfen bir YouTube URL'si girin.")
            return
        if not analyzer.api_key:
            show_toast("Lütfen önce bir API Key ayarlayın.")
            return

        state.is_loading = True
        state.analysis_completed = False
        state.progress_value = 0.0
        state.progress_status = "Başlatılıyor..."
        update_action_area()
        page.update()

        def analyze_thread():
            gemini_done = [False]

            def smooth_progress_ticker():
                while not gemini_done[0] and state.progress_value < 0.95:
                    time.sleep(0.3)
                    if gemini_done[0]:
                        break
                    if state.progress_value < 0.90:
                        state.progress_value += 0.02
                        update_progress_display()

            try:
                # Aşama 1: Video bilgileri
                state.progress_value = 0.10
                state.progress_status = "Video bilgileri alınıyor..."
                update_progress_display()
                analyzer.fetch_video_info(url)

                # Aşama 2: Altyazılar
                state.progress_value = 0.30
                state.progress_status = "Video altyazıları indiriliyor..."
                update_progress_display()
                analyzer.fetch_transcript()

                # Aşama 3: Yapay zeka analizi (ticker ile pürüzsüz ilerleme)
                state.progress_value = 0.40
                state.progress_status = "Yapay zeka ile video analiz ediliyor..."
                update_progress_display()
                page.run_thread(smooth_progress_ticker)

                summary_text = analyzer.summarize(long_ver=(state.length == 'long'))
                gemini_done[0] = True

                # Aşama 4: Sonuçlar işleniyor
                state.progress_value = 0.92
                state.progress_status = "Analiz sonuçları işleniyor..."
                update_progress_display()
                clean_summary, suggestions = analyzer.parse_suggestions(summary_text)

                state.ai_summary = clean_summary
                if not suggestions:
                    suggestions = analyzer.generate_fallback_suggestions(analyzer.title)
                state.summary_suggestions = suggestions
                state.chat_messages = []

                # Aşama 5: Tamamlandı!
                state.progress_value = 1.0
                state.progress_status = "Analiz başarıyla tamamlandı!"
                update_progress_display()
                time.sleep(0.5)
                state.analysis_completed = True
            except Exception as ex:
                state.analysis_completed = False
                state.current_view = 'home'
                show_toast(str(ex))
            finally:
                gemini_done[0] = True
                state.is_loading = False
                update_action_area()
                page.update()

        page.run_thread(analyze_thread)

    # Regenerate Summary Flow
    def regenerate_summary(length_type=None):
        if length_type:
            state.length = length_type
        state.is_loading = True
        refresh_ui()

        def regen_thread():
            try:
                summary_text = analyzer.summarize(long_ver=(state.length == 'long'))
                clean_summary, suggestions = analyzer.parse_suggestions(summary_text)
                state.ai_summary = clean_summary
                if not suggestions:
                    suggestions = analyzer.generate_fallback_suggestions(analyzer.title)
                state.summary_suggestions = suggestions
            except Exception as ex:
                show_toast(str(ex))
            finally:
                state.is_loading = False
                refresh_ui()

        page.run_thread(regen_thread)

    # ─── Chat List Updater ───
    def update_chat_list(skip_update=False):
        chat_list_column.controls.clear()
        for idx, m in enumerate(state.chat_messages):
            if m['is_user']:
                # User Bubble (Right Aligned)
                chat_list_column.controls.append(
                    ft.Row([
                        ft.Container(
                            content=ft.Text(m['text'], color=ft.Colors.WHITE, size=14),
                            bgcolor="#2D1F5E",
                            border_radius=ft.BorderRadius.only(top_left=16, top_right=16, bottom_left=16, bottom_right=4),
                            border=ft.Border.all(1, "#3CBB86FC"),
                            padding=14,
                            width=500,
                        )
                    ], alignment=ft.MainAxisAlignment.END)
                )
            else:
                # AI Bubble (Left Aligned)
                ai_bubble = ft.Container(
                    content=ft.Markdown(
                        m['text'],
                        selectable=True,
                        extension_set=ft.MarkdownExtensionSet.GITHUB_WEB,
                    ),
                    bgcolor="#161230",
                    border_radius=ft.BorderRadius.only(top_left=16, top_right=16, bottom_left=4, bottom_right=16),
                    border=ft.Border.all(1, "#14BB86FC"),
                    padding=14,
                    width=600,
                )
                
                # Suggestions chips row
                chips_list = []
                suggestions = m.get('suggestions', [])
                if not suggestions:
                    # Generate beautiful context-aware fallback questions so they are ALWAYS clickable
                    user_msgs = [x['text'] for x in state.chat_messages[:idx] if x['is_user']]
                    last_query = user_msgs[-1] if user_msgs else "bu video"
                    suggestions = analyzer.generate_fallback_suggestions(last_query)
                
                for s in suggestions:
                    chips_list.append(
                        ft.Container(
                            content=ft.Row([
                                ft.Icon(ft.Icons.LIGHTBULB_OUTLINE, size=13, color="#BB86FC"),
                                ft.Text(s, size=12, color="#BB86FC", weight=ft.FontWeight.W_500),
                            ], spacing=4),
                            bgcolor="#1CBB86FC",
                            border=ft.Border.all(1, "#4CBB86FC"),
                            border_radius=18,
                            padding=ft.Padding.symmetric(horizontal=12, vertical=8),
                            on_click=lambda _, text=s: send_chat_message(text),
                        )
                    )
                
                if chips_list:
                    chat_list_column.controls.append(
                        ft.Column([
                            ft.Row([ai_bubble], alignment=ft.MainAxisAlignment.START),
                            ft.Container(
                                content=ft.Row(chips_list, wrap=True, spacing=8, run_spacing=8),
                                padding=ft.Padding.only(left=8, right=60),
                            )
                        ], spacing=8)
                    )
                else:
                    chat_list_column.controls.append(
                        ft.Row([ai_bubble], alignment=ft.MainAxisAlignment.START)
                    )

        # Chat Loading Indicator
        if state.is_chat_loading:
            chat_list_column.controls.append(
                ft.Row([
                    ft.Container(
                        content=ft.Row([
                            ft.ProgressRing(color="#BB86FC", width=20, height=20),
                            ft.Text("Gemini yanıt yazıyor...", size=13, color=C['gray']),
                        ], spacing=10),
                        bgcolor="#161230",
                        border_radius=ft.BorderRadius.only(top_left=16, top_right=16, bottom_left=4, bottom_right=16),
                        border=ft.Border.all(1, "#14BB86FC"),
                        padding=14,
                    )
                ], alignment=ft.MainAxisAlignment.START)
            )
        if not skip_update:
            page.update()

    # Chat Message Send Flow
    def send_chat_message(prefilled_msg=None):
        msg = prefilled_msg or chat_input.value.strip()
        if not msg:
            return
        if not prefilled_msg:
            chat_input.value = ""
        
        # Add user message
        state.chat_messages.append({'text': msg, 'is_user': True, 'suggestions': []})
        state.is_chat_loading = True
        update_chat_list()
        scroll_chat_to_bottom()

        def chat_thread():
            try:
                resp = analyzer.send(msg)
                clean_resp, suggestions = analyzer.parse_suggestions(resp)
                state.chat_messages.append({
                    'text': clean_resp,
                    'is_user': False,
                    'suggestions': suggestions
                })
            except Exception as ex:
                # generate local fallbacks in case of transient API error
                sugs = analyzer.generate_fallback_suggestions(msg)
                state.chat_messages.append({
                    'text': f"Hata oluştu: {str(ex)}\n\nLütfen tekrar deneyin.",
                    'is_user': False,
                    'suggestions': sugs
                })
            finally:
                state.is_chat_loading = False
                update_chat_list()
                scroll_chat_to_bottom()

        page.run_thread(chat_thread)

    def scroll_chat_to_bottom():
        time.sleep(0.1) # Wait briefly for container layout
        chat_list_column.scroll_to(delta=1000, duration=300)
        page.update()

    # ─── Home Screen View Builder ───
    def build_home_view():
        # Banner/Header
        header = ft.Column([
            ft.Text(
                "YouTube\nAnaliz & Özet",
                size=38,
                weight=ft.FontWeight.BOLD,
                color="#BB86FC",
                height=1.1,
            ),
            ft.Container(
                content=ft.Text(
                    "Design by Kerem Akşahin",
                    size=12,
                    weight=ft.FontWeight.W_500,
                    color="#BB86FC",
                ),
                bgcolor="#1CBB86FC",
                border_radius=6,
                padding=ft.Padding.symmetric(horizontal=10, vertical=4),
            ),
            ft.Text(
                "Video URL'sini girin, yapay zeka saniyeler içinde özetlesin ve sorularınızı yanıtlasın.",
                size=14,
                color=C['gray'],
            )
        ], spacing=10)

        # Length Chips
        short_active = (state.length == 'short')
        length_row = ft.Row([
            ft.Container(
                content=ft.Row([
                    ft.Icon(ft.Icons.SHORT_TEXT, size=16, color="#0F0C29" if short_active else ft.Colors.WHITE),
                    ft.Text("Kısa Özet", size=13, weight=ft.FontWeight.BOLD, color="#0F0C29" if short_active else ft.Colors.WHITE),
                ], alignment=ft.MainAxisAlignment.CENTER),
                bgcolor="#BB86FC" if short_active else ft.Colors.TRANSPARENT,
                border=ft.Border.all(1, "#BB86FC" if short_active else "#28B3B3B3"),
                border_radius=12,
                padding=ft.Padding.symmetric(vertical=14),
                on_click=lambda _: set_length('short'),
                expand=True,
            ),
            ft.Container(
                content=ft.Row([
                    ft.Icon(ft.Icons.SUBJECT, size=16, color="#0F0C29" if not short_active else ft.Colors.WHITE),
                    ft.Text("Uzun Özet", size=13, weight=ft.FontWeight.BOLD, color="#0F0C29" if not short_active else ft.Colors.WHITE),
                ], alignment=ft.MainAxisAlignment.CENTER),
                bgcolor="#BB86FC" if not short_active else ft.Colors.TRANSPARENT,
                border=ft.Border.all(1, "#BB86FC" if not short_active else "#28B3B3B3"),
                border_radius=12,
                padding=ft.Padding.symmetric(vertical=14),
                on_click=lambda _: set_length('long'),
                expand=True,
            ),
        ], spacing=10)

        # Video Analiz Card
        analiz_card = ft.Container(
            bgcolor="#77302B63",
            border=ft.Border.all(1, "#28BB86FC"),
            border_radius=20,
            padding=20,
            content=ft.Column([
                ft.Row([
                    ft.Icon(ft.Icons.PLAY_CIRCLE_FILL_ROUNDED, color="#BB86FC", size=20),
                    ft.Text("Video Analiz", color=ft.Colors.WHITE, size=16, weight=ft.FontWeight.W_600),
                ], spacing=8),
                ft.Container(height=10),
                ft.Row([url_input]),
                ft.Container(height=10),
                length_row,
                ft.Container(height=16),
                _action_container,
            ], spacing=10)
        )

        # action_container içeriğini güncelle
        update_action_area()

        # API Key Section
        if not analyzer.api_key:
            api_card = ft.Container(
                bgcolor="#77302B63",
                border=ft.Border.all(1, "#28BB86FC"),
                border_radius=20,
                padding=20,
                content=ft.Column([
                    ft.Row([
                        ft.Icon(ft.Icons.KEY_ROUNDED, color="#BB86FC", size=20),
                        ft.Text("API Key", color=ft.Colors.WHITE, size=16, weight=ft.FontWeight.W_600),
                    ], spacing=8),
                    ft.Container(height=10),
                    api_key_input,
                    ft.Container(height=12),
                    ft.Container(
                        content=ft.Button(
                            content=ft.Row([
                                ft.Icon(ft.Icons.CHECK_CIRCLE_OUTLINE, size=18, color="#0F0C29"),
                                ft.Text("KEY KAYDET", weight=ft.FontWeight.BOLD, color="#0F0C29"),
                            ], alignment=ft.MainAxisAlignment.CENTER),
                            bgcolor="#BB86FC",
                            style=ft.ButtonStyle(shape=ft.RoundedRectangleBorder(radius=12)),
                            on_click=save_api_key,
                            height=52,
                        ),
                        width=float("inf"),
                    )
                ])
            )
        else:
            api_card = ft.Container(
                content=ft.Row([
                    ft.Icon(ft.Icons.CHECK_CIRCLE, color=ft.Colors.GREEN_400, size=16),
                    ft.TextButton(
                        "API Key aktif  •  Değiştir",
                        on_click=show_reset_dialog,
                        style=ft.ButtonStyle(color=ft.Colors.GREEN_400),
                    )
                ], alignment=ft.MainAxisAlignment.CENTER),
                margin=ft.Margin.symmetric(vertical=10),
            )

        # Wrap in Scroll Column
        return ft.Column([
            header,
            ft.Container(height=26),
            api_card,
            analiz_card,
        ], scroll=ft.ScrollMode.AUTO, expand=True, spacing=14)

    # ─── Result Screen View Builder ───
    def build_result_view():
        # Title bar
        def go_back(e):
            state.current_view = 'home'
            refresh_ui()

        def copy_summary(e):
            if set_clipboard_text(state.ai_summary):
                show_toast("Özet panoya kopyalandı ✓", is_error=False)
            else:
                show_toast("Pano hatası oluştu.")

        def share_summary(e):
            link = f"https://youtube.com/watch?v={analyzer.video_id}"
            share_text = f"*{analyzer.title}* - Video AI Özeti:\n\n{state.ai_summary}\n\nKaynak: {link}"
            if set_clipboard_text(share_text):
                show_toast("Özet ve video linki panoya kopyalandı ✓", is_error=False)
            else:
                show_toast("Paylaşım hatası.")

        app_bar = ft.Row([
            ft.IconButton(ft.Icons.ARROW_BACK_IOS_NEW, on_click=go_back, icon_color="#BB86FC"),
            ft.Text("Analiz Sonucu", size=20, weight=ft.FontWeight.BOLD, color=ft.Colors.WHITE, expand=True),
            ft.IconButton(ft.Icons.COPY, on_click=copy_summary, icon_color=C['gray']),
            ft.IconButton(ft.Icons.SHARE, on_click=share_summary, icon_color=C['gray']),
        ], alignment=ft.MainAxisAlignment.SPACE_BETWEEN)

        # Video Header Card
        video_card = ft.Container(
            bgcolor="#4c302B63",
            border_radius=16,
            padding=14,
            content=ft.Row([
                ft.Image(src=analyzer.thumbnail_url, width=140, height=84, fit="cover", border_radius=8),
                ft.Container(width=12),
                ft.Column([
                    ft.Text(analyzer.title, size=15, weight=ft.FontWeight.BOLD, color=ft.Colors.WHITE, max_lines=2, overflow=ft.TextOverflow.ELLIPSIS),
                    ft.Text(analyzer.author, size=12, color=C['gray']),
                ], expand=True, spacing=4)
            ])
        )

        # Toggle & Refresh Row
        short_active = (state.length == 'short')
        toggle_regen_row = ft.Row([
            ft.Row([
                ft.Container(
                    content=ft.Text("Kısa", size=12, weight=ft.FontWeight.BOLD, color="#0F0C29" if short_active else "#BB86FC"),
                    bgcolor="#BB86FC" if short_active else ft.Colors.TRANSPARENT,
                    border=ft.Border.all(1, "#BB86FC"),
                    border_radius=8,
                    padding=ft.Padding.symmetric(horizontal=16, vertical=8),
                    on_click=lambda _: regenerate_summary('short'),
                ),
                ft.Container(
                    content=ft.Text("Uzun", size=12, weight=ft.FontWeight.BOLD, color="#0F0C29" if not short_active else "#BB86FC"),
                    bgcolor="#BB86FC" if not short_active else ft.Colors.TRANSPARENT,
                    border=ft.Border.all(1, "#BB86FC"),
                    border_radius=8,
                    padding=ft.Padding.symmetric(horizontal=16, vertical=8),
                    on_click=lambda _: regenerate_summary('long'),
                ),
            ], spacing=6),
            ft.Container(
                content=ft.Row([
                    ft.Icon(ft.Icons.REFRESH, size=14, color="#BB86FC"),
                    ft.Text("Yeniden", size=12, weight=ft.FontWeight.BOLD, color="#BB86FC"),
                ]),
                border=ft.Border.all(1, "#BB86FC"),
                border_radius=8,
                padding=ft.Padding.symmetric(horizontal=12, vertical=8),
                on_click=lambda _: regenerate_summary(),
            )
        ], alignment=ft.MainAxisAlignment.SPACE_BETWEEN)

        # AI Summary Card
        summary_chips = []
        if hasattr(state, 'summary_suggestions') and state.summary_suggestions:
            for s in state.summary_suggestions:
                summary_chips.append(
                    ft.Container(
                        content=ft.Row([
                            ft.Icon(ft.Icons.LIGHTBULB_OUTLINE, size=13, color="#BB86FC"),
                            ft.Text(s, size=12, color="#BB86FC", weight=ft.FontWeight.W_500),
                        ], spacing=4),
                        bgcolor="#1CBB86FC",
                        border=ft.Border.all(1, "#4CBB86FC"),
                        border_radius=18,
                        padding=ft.Padding.symmetric(horizontal=12, vertical=8),
                        on_click=lambda _, text=s: send_chat_message(text),
                    )
                )

        summary_card = ft.Container(
            bgcolor="#77302B63",
            border=ft.Border.all(1, "#28BB86FC"),
            border_radius=20,
            padding=20,
            content=ft.Column([
                ft.Row([
                    ft.Icon(ft.Icons.AUTO_AWESOME, color="#BB86FC", size=18),
                    ft.Text("AI Özet", color="#BB86FC", size=16, weight=ft.FontWeight.W_600),
                ], spacing=8),
                ft.Container(height=10),
                ft.Markdown(
                    state.ai_summary,
                    selectable=True,
                    extension_set=ft.MarkdownExtensionSet.GITHUB_WEB,
                ),
                ft.Container(height=10) if summary_chips else ft.Container(),
                ft.Row(summary_chips, wrap=True, spacing=8, run_spacing=8) if summary_chips else ft.Container(),
            ])
        )

        # Chat Section Header
        chat_header = ft.Column([
            ft.Row([
                ft.Icon(ft.Icons.CHAT_BUBBLE_OUTLINE, color=ft.Colors.WHITE, size=18),
                ft.Text("Video Hakkında Soru Sor", color=ft.Colors.WHITE, size=16, weight=ft.FontWeight.W_600),
            ], spacing=8),
            ft.Text("Gemini, video içeriği hakkında sorularınızı yanıtlar.", size=12, color=C['gray']),
        ], spacing=4)

        # Generate Chat List Widgets
        update_chat_list(skip_update=True)

        # Scrollable Content Area
        scroll_content = ft.Column([
            video_card,
            ft.Container(height=12),
            toggle_regen_row,
            ft.Container(height=16),
            summary_card,
            ft.Container(height=24),
            chat_header,
            ft.Container(height=10),
            chat_list_column,
            ft.Container(height=120), # Spacer at the bottom so chat input doesn't block content
        ], scroll=ft.ScrollMode.AUTO, expand=True, spacing=14)

        # Fixed bottom chat input
        bottom_chat_bar = ft.Container(
            bgcolor=C['bg2'],
            border=ft.Border.only(top=ft.border.BorderSide(1, C['border'])),
            padding=ft.Padding.symmetric(horizontal=20, vertical=12),
            content=ft.Row([
                chat_input,
                ft.IconButton(
                    icon=ft.Icons.SEND,
                    icon_color="#0F0C29",
                    bgcolor="#BB86FC",
                    icon_size=20,
                    width=46,
                    height=46,
                    on_click=lambda _: send_chat_message(),
                )
            ], alignment=ft.MainAxisAlignment.SPACE_BETWEEN)
        )

        return ft.Column([
            app_bar,
            ft.Container(height=12),
            ft.Container(content=scroll_content, expand=True),
            bottom_chat_bar
        ], expand=True)

    # ─── UI Refresh Helper ───
    def refresh_ui():
        # Set gradients on the main wrapping container
        main_container.gradient = ft.LinearGradient(
            begin=ft.Alignment.TOP_LEFT,
            end=ft.Alignment.BOTTOM_RIGHT,
            colors=["#0F0C29", "#302B63", "#1A0533"],
            stops=[0.0, 0.5, 1.0]
        )
        main_container.padding = 24 if state.current_view == 'home' else ft.Padding.only(left=24, right=24, top=24, bottom=0)
        
        if state.current_view == 'home':
            main_container.content = build_home_view()
        else:
            main_container.content = build_result_view()
        page.update()

    # Initial Render
    main_container.content = build_home_view()
    page.add(main_container)
    refresh_ui()

if __name__ == '__main__':
    ft.run(main)
