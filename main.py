# -*- coding: utf-8 -*-
import os
import subprocess
import sys
import shutil

def run_command(command):
    print(f"Calistiriliyor: {command}")
    process = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    while True:
        output = process.stdout.readline()
        if output == b'' and process.poll() is not None:
            break
        if output:
            print(output.decode('utf-8', errors='ignore').strip())
    rc = process.poll()
    return rc

def main():
    print("=== Android Kotlin Projesi Local Derleme Yardimcisi ===")
    
    # 1. JDK Kontrolu
    try:
        java_version = subprocess.check_output("java -version", shell=True, stderr=subprocess.STDOUT).decode('utf-8')
        print(f"Java Tespit Edildi:\n{java_version}")
    except Exception:
        print("HATA: Sisteminizde Java JDK bulunamadi. Lutfen Java JDK 17 yukleyip tekrar deneyin.")
        sys.exit(1)

    # 2. Gradle Izni ve Kontrolu
    if not os.path.exists("gradlew") and not os.path.exists("gradlew.bat"):
        print("Gradle Wrapper bulunamadi, yerel ortamda olusturuluyor...")
        run_command("gradle wrapper")

    if os.name != 'nt':
        run_command("chmod +x gradlew")

    # 3. Derleme Islemine Basla
    print("Hizli derleme baslatiliyor...")
    gradle_cmd = "./gradlew assembleDebug" if os.name != 'nt' else "gradlew.bat assembleDebug"
    result = run_command(gradle_cmd)

    if result == 0:
        print("\nDerleme Basariyla Tamamlandi!")
        # APK bul ve kopyala
        found_apk = None
        for root, dirs, files in os.walk("."):
            for file in files:
                if file.endswith(".apk"):
                    found_apk = os.path.join(root, file)
                    break
            if found_apk: 
                break
        
        if found_apk:
            target_name = "./KIVVAANC.apk"
            shutil.copy2(found_apk, target_name)
            print(f"APK basariyla yeniden adlandirildi ve suraya kopyalandi: {os.path.abspath(target_name)}")
        else:
            print("HATA: Derlenmis bir APK bulunamadi!")
    else:
        print("HATA: Derleme sirasinda hata olustu.")
        sys.exit(1)

if __name__ == '__main__':
    main()
