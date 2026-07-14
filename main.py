# -*- coding: utf-8 -*-
import os
import sys
import subprocess

def check_project():
    print("=== P2P-GOZLEM Android Projesi Yapilandirma Yardimcisi ===")
    print("Bu betik yerel ortamda projenizi derlemeye hazirlamak icin kontroller yapar.\n")
    
    # Gradle calisabilirlik kontrolu
    gradlew_file = "gradlew" if sys.platform != "win32" else "gradlew.bat"
    if os.path.exists(gradlew_file):
        print(f"[OK] '{gradlew_file}' yerel betigi basariyla tespit edildi.")
    else:
        print("[HATA] Proje kok dizininde 'gradlew' dosyasi bulunamadi! Proje yapisini kontrol edin.")

    # Android Manifest kontrolu
    manifest_path = os.path.join("app", "src", "main", "AndroidManifest.xml")
    if os.path.exists(manifest_path):
        print(f"[OK] AndroidManifest.xml bulundu: {manifest_path}")
    else:
        print("[UYARI] AndroidManifest.xml dosyasi standart yolda bulunamadi!")

    # Yerel Derleme Talimati
    print("\n[YEREL DERLEME TALIMATI]")
    if sys.platform == "win32":
        print("Windows uzerinde derlemek icin komut satirinda sunu calistirin:")
        print("    .\\gradlew.bat assembleDebug")
    else:
        print("macOS/Linux uzerinde derlemek icin sunu calistirin:")
        print("    chmod +x gradlew && ./gradlew assembleDebug")

if __name__ == '__main__':
    check_project()
