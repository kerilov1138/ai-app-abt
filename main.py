import os
import sys
import subprocess
import shutil

def check_java():
    print("[*] Java (JDK) sürümü kontrol ediliyor...")
    try:
        result = subprocess.run(["java", "-version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        print(result.stderr)
        return True
    except FileNotFoundError:
        print("[-] Hata: Java (JDK) sistemde yüklü değil! Lütfen JDK 17 veya üzerini yükleyin.")
        return False

def build_apk():
    print("[*] Android APK derleme işlemi başlatılıyor...")
    
    gradlew_cmd = "./gradlew" if os.name != "nt" else "gradlew.bat"
    if not os.path.exists(gradlew_cmd):
        print("[!] gradlew bulunamadı, Gradle Wrapper oluşturulmaya çalışılıyor...")
        try:
            subprocess.run(["gradle", "wrapper"], check=True)
        except FileNotFoundError:
            print("[-] Hata: Sistemde 'gradle' komutu bulunamadı! Lütfen gradle kurun veya gradlew dosyalarını hazırlayın.")
            return False

    if os.name != "nt":
        os.chmod(gradlew_cmd, 0o755)

    print("[*] Gradle assembleDebug çalıştırılıyor...")
    try:
        subprocess.run([gradlew_cmd, "assembleDebug"], check=True)
        print("[+] Derleme başarıyla tamamlandı!")
        return True
    except subprocess.CalledProcessError as e:
        print(f"[-] Derleme sırasında bir hata oluştu: {e}")
        return False

def locate_and_rename_apk():
    print("[*] Derlenen APK aranıyor...")
    search_paths = [
        os.path.join("app", "build", "outputs", "apk", "debug"),
        os.path.join("app", "build", "outputs", "apk"),
        os.path.abspath(".")
    ]
    
    found_apk = None
    for path in search_paths:
        if os.path.exists(path):
            for root, dirs, files in os.walk(path):
                for file in files:
                    if file.endswith(".apk"):
                        found_apk = os.path.join(root, file)
                        break
                if found_apk:
                    break
        if found_apk:
            break

    if found_apk:
        target_name = "kıvo.apk"
        shutil.copy(found_apk, target_name)
        print(f"[+] Başarılı! APK kopyalandı ve yeniden adlandırıldı: {os.path.abspath(target_name)}")
    else: 
        print("[-] Hata: Derlenen APK dosyası bulunamadı!")

if __name__ == "__main__":
    print("=== P2P-GOZLEM Android Build Helper ===")
    if check_java():
        if build_apk():
            locate_and_rename_apk()
        else:
            print("[-] APK derlenemedi.")
    else:
        sys.exit(1)