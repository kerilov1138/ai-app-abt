# -*- coding: utf-8 -*-
import os
import subprocess
import sys

def sistem_kontrolu():
    print("--- Sistem Kontrolleri Baslatiliyor ---")
    # Java kontrolü
    try:
        java_check = subprocess.run(["java", "-version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        print("[+] Java bulundu:")
        print(java_check.stderr.strip())
    except FileNotFoundError:
        print("[-] Hata: Java JDK sistemde bulunamadi. Gradle derlemesi icin Java gereklidir.")
        sys.exit(1)

    # Gradle wrapper kontrolü
    gradle_bin = "./gradlew" if os.name != "nt" else "gradlew.bat"
    if os.path.exists(gradle_bin):
        print(f"[+] Gradle Wrapper bulundu: {gradle_bin}")
        if os.name != "nt":
            os.chmod(gradle_bin, 0o755)
            print("[+] Gradle calistirma izinleri guncellendi.")
    else:
        print("[-] Hata: gradlew dosyasi kok dizinde bulunamadi.")
        sys.exit(1)

def apk_derle():
    print("--- Android APK Derleme Islemi Basliyor ---")
    gradle_bin = "./gradlew" if os.name != "nt" else "gradlew.bat"
    
    try:
        # Gradle debug derlemesi calistir
        subprocess.run([gradle_bin, "assembleDebug"], check=True)
        print("[+] Basarili: APK başarıyla derlendi!")
        apk_yolu = os.path.join("app", "build", "outputs", "apk", "debug", "app-debug.apk")
        if os.path.exists(apk_yolu):
            print(f"[+] APK Konumu: {os.path.abspath(apk_yolu)}")
        else:
            print("[!] Uyarı: APK derlendi ancak beklenen dizinde bulunamadi.")
    except subprocess.CalledProcessError as e:
        print(f"[-] Hata: Gradle derleme islemi sirasinda hata olustu: {e}")
        sys.exit(1)

if __name__ == "__main__":
    sistem_kontrolu()
    apk_derle()
