import os
import sys

def update_pubspec():
    pubspec_path = 'pubspec.yaml'
    # The source of truth for assets is strictly assets/www
    assets_root = 'assets/www'
    marker = '# END ASSETS'
    
    # 1. Clean and Collect Asset Directories
    asset_dirs = set()
    
    # Junk folders and non-runtime files to ignore (Mükemmel temizlik)
    junk_folders = {
        'node_modules', '.git', '.next', 'dist', 'build', 
        'android', 'ios', 'src', 'public', '.github',
        'scripts', '__pycache__', 'coverage', 'test'
    }

    if os.path.exists(assets_root):
        # We always start by adding the root asset folder if it exists
        asset_dirs.add('assets/www/')
        
        for root, dirs, files in os.walk(assets_root):
            # Rule 1: ELIMINATE JUNK FOLDERS (In-place filter to stop recursion)
            dirs[:] = [d for d in dirs if d not in junk_folders]
            
            # Rule 2: COLLECT FOLDERS THAT CONTAIN ACTUAL FILES
            if files:
                # Rule 3: DOUBLE-CONTROL SECURITY (Slash cleaning)
                # Remove leading/trailing slashes and standardize to forward slashes
                normalized_root = root.replace('\\', '/').strip('/')
                
                # Ensure the path ends with a single slash as required by Flutter
                if not normalized_root.endswith('/'):
                    normalized_root += '/'
                
                # Rule 4: SECURITY - Only allow paths inside assets/www
                if normalized_root.startswith('assets/www/'):
                    asset_dirs.add(normalized_root)

    # Sort for deterministic and clean YAML output
    sorted_assets = sorted(list(asset_dirs))

    # 2. Read existing pubspec.yaml
    if not os.path.exists(pubspec_path):
        print(f"Error: {pubspec_path} is missing. Build cannot proceed.")
        return

    with open(pubspec_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    # 3. Marker Search (Hassas İşaretleme Sistemi)
    marker_idx = -1
    for i, line in enumerate(lines):
        if marker in line:
            marker_idx = i
            break

    if marker_idx == -1:
        print(f"Error: Critical marker {marker} is missing in pubspec.yaml. Injection failed.")
        return

    # 4. Find the 'assets:' header
    # We look backwards from the marker to find the relevant section
    assets_header_idx = -1
    for i in range(marker_idx - 1, -1, -1):
        if 'assets:' in lines[i] and not lines[i].strip().startswith('#'):
            assets_header_idx = i
            break

    if assets_header_idx == -1:
         print("Error: 'assets:' section header was not found before the marker. Check YAML structure.")
         return

    # 5. Build Content based on preservation rules
    cleaned_lines = []
    assets_found = False
    
    for i, line in enumerate(lines[:marker_idx]):
        stripped = line.strip()
        
        # Keep everything until we reach the assets section
        if not assets_found:
            cleaned_lines.append(line)
            if stripped == 'assets:':
                assets_found = True
            continue
            
        # Inside assets section but before marker:
        # Keep any asset that is NOT under assets/www/ (manual management)
        if not stripped.startswith('- assets/www/'):
            cleaned_lines.append(line)

    # 6. MÜKEMMEL HİZALAMA (Inject new dynamic assets)
    for asset_path in sorted_assets:
        # Rules: 4 spaces, clean paths, exactly one slash at end
        final_path = asset_path.replace('//', '/').strip('/')
        cleaned_lines.append(f"    - {final_path}/\n")
    
    # 7. Add marker and beyond
    cleaned_lines.append(f"    {marker}\n")
    cleaned_lines.extend(lines[marker_idx+1:])

    # Atomic Write
    try:
        with open(pubspec_path, 'w', encoding='utf-8') as f:
            f.writelines(cleaned_lines)
        print(f"Success: Marker-Based Injection complete. {len(sorted_assets)} asset folders added with 4-space indent.")
    except Exception as e:
        print(f"Failure: Could not write to pubspec.yaml: {str(e)}")

if __name__ == "__main__":
    update_pubspec()
