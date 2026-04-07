import webbrowser
import sys
import os

# Dünya Ülkeleri Bilgi Yarışması - Masaüstü Başlatıcı
# Bu script, uygulamayı varsayılan tarayıcınızda açar.

def main():
    # Uygulama URL'si (AI Studio Build tarafından sağlanan App URL)
    app_url = "https://ais-dev-xskmyeopzgqnprmdyhhb4o-26636727861.europe-west2.run.app"
    
    print("--------------------------------------------------")
    print("DÜNYA ÜLKELERİ BİLGİ YARIŞMASI")
    print("--------------------------------------------------")
    print(f"Uygulama başlatılıyor: {app_url}")
    print("Lütfen bekleyin...")
    
    try:
        # Tarayıcıyı aç
        webbrowser.open(app_url)
        print("\nBaşarılı! Uygulama tarayıcınızda açıldı.")
        print("İyi oyunlar!")
    except Exception as e:
        print(f"\nHata oluştu: {e}")
        print("Lütfen internet bağlantınızı kontrol edin veya URL'yi manuel olarak açın.")
    
    # Pencerenin hemen kapanmaması için (Windows'ta çift tıklandığında)
    if os.name == 'nt':
        input("\nKapatmak için ENTER tuşuna basın...")

if __name__ == "__main__":
    main()
