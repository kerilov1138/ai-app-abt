import webbrowser
import os
import sys

# Dunya Bilgi Yarismasi - PC Launcher
# Bu arac uygulamayi tarayicinizda tam ekran modunda acar.

def launch():
    url = "https://ais-dev-xskmyeopzgqnprmdyhhb4o-26636727861.europe-west2.run.app"
    print("Uygulama baslatiliyor...")
    print(f"Hedef: {url}")
    
    try:
        webbrowser.open(url)
        print("Basarili! Iyi oyunlar.")
    except Exception as e:
        print(f"Hata: {e}")

if __name__ == "__main__":
    launch()
    # Windows'ta pencerenin hemen kapanmamasi icin
    if os.name == 'nt':
        os.system("pause")
