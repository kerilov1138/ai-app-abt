# -*- coding: utf-8 -*-
# P2P-GOZLEM Projesi icin Android APK Derleme ve Hazirlik Yardimci Betigi

import os
import sys
import subprocess
import platform

def check_environment():
    """Java JDK ve Gradle Wrapper dosyalarinin varligini kontrol eder."""
    print("[YARDIMCI] Ortam kontrolleri yapiliyor...")
    
    # Java kontrolu
    try:
        java_check = subprocess.run(["java", "-version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if java_check.returncode == 0:
            print("[OK] Java JDK basariyla tespit edildi.")
        else:
            print("[HATA] Java tespit edildi ancak duzgun calismiyor.")
            return False
    except FileNotFoundError:
        print("[HATA] 'java' komutu sistemde bulunamadi. Lutfen Java JDK 17 yukleyin.")
        return False
        
    # Gradle wrapper kontrolu
    gradle_exec = "gradlew.bat" if platform.system() == "Windows" else "./gradlew"
    if os.path.exists("gradlew") or os.path.exists("gradlew.bat"):
        print(f"[OK] Gradle Wrapper ({gradle_exec}) projenin ana dizininde bulundu.")
        if platform.system() != "Windows":
            print("[BILGI] Linux/macOS icin Gradle wrapper'a yurutme izni veriliyor...")
            os.chmod("gradlew", 0o755)
    else:
        print("[HATA] Proje ana dizininde 'gradlew' bulunamadi. Lutfen dogru dizinde oldugunuzdan emin olun.")
        return False

    return True

def run_build():
    """Gradlew kullanarak Debug APK'sini derler."""
    print("[YARDIMCI] Gradle derleme sureci baslatiliyor...")
    cmd = "gradlew.bat" if platform.system() == "Windows" else "./gradlew"
    
    try:
        process = subprocess.Popen([cmd, "assembleDebug"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        
        # Loglari anlik yazdir
        for line in process.stdout:
            print(line, end="")
            
        process.wait()
        
        if process.returncode == 0:
            print("\n[BASARILI] Derleme basariyla tamamlandi!")
            apk_path = os.path.join("app", "build", "outputs", "apk", "debug", "app-debug.apk")
            if os.path.exists(apk_path):
                print(f"[OK] Derlenen APK Yolu: {os.path.abspath(apk_path)}")
            else:
                print("[UYARI] Derleme basarili, fakat 'app-debug.apk' hedeflenen dizinde bulunamadi.")
        else:
            print(f"\n[HATA] Derleme hatasi! Cikis kodu: {process.returncode}")
            sys.exit(1)
            
    except Exception as e:
        print(f"[HATA] Derleme sirasinda bir istisna olustu: {str(e)}")
        sys.exit(1)

if __name__ == "__main__":
    if check_environment():
        run_build()
    else:
        print("[HATA] Kurulum gereksinimleri karsilanmadigi icin derleme baslatilamadi.")
        sys.exit(1)