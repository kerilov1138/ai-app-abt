# -*- coding: utf-8 -*-
import os
import sys
import subprocess

def sistem_kontrol_et():
    print("[BILGI] Java Surumu Kontrol Ediliyor...")
    try:
        java_surum = subprocess.run(["java", "-version"], capture_output=True, text=True)
        print(java_surum.stderr)
    except FileNotFoundError:
        print("[HATA] Java bulunamadi. Android derleme islemi icin JDK kurulmalidir.")
        sys.exit(1)

def gradle_calistir():
    print("[BILGI] Gradle build baslatiliyor...")
    is_windows = os.name == 'nt'
    gradle_cmd = "gradlew.bat" if is_windows else "./gradlew"
    
    if not is_windows:
        # Linux/macOS icin izin ayarla
        subprocess.run(["chmod", "+x", "gradlew"])
    
    try:
        # Debug APK derleme komutunu calistir
        sonuc = subprocess.run([gradle_cmd, "assembleDebug"], check=True)
        if sonuc.returncode == 0:
            print("[BASARILI] APK basariyla derlendi.")
            apk_yolu = os.path.join("app", "build", "outputs", "apk", "debug", "app-debug.apk")
            if os.path.exists(apk_yolu):
                print(f"[BILGI] APK Konumu: {os.path.abspath(apk_yolu)}")
    except subprocess.CalledProcessError as e:
        print(f"[HATA] Gradle derleme hatasi olustu: {e}")
        sys.exit(1)

if __name__ == "__main__":
    print("=== Android Projesi Derleme Yardimcisi ===")
    sistem_kontrol_et()
    gradle_calistir()
