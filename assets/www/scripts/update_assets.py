import os
import sys

def update_pubspec():
    pubspec_path = 'pubspec.yaml'
    assets_root = 'assets/www'
    marker = '# END ASSETS'
    
    if not os.path.exists(pubspec_path):
        print(f"Error: {pubspec_path} found.")
        return

    # Scan for directories holding assets
    asset_dirs = set()
    asset_dirs.add('assets/www/')
    
    # Exclude common development or non-asset folders
    exclude_folders = {'.git', 'node_modules', '.next', 'dist', 'build', 'android', 'ios', '__pycache__'}
    
    if os.path.exists(assets_root):
        for root, dirs, files in os.walk(assets_root):
            # Prune directory list
            dirs[:] = [d for d in dirs if d not in exclude_folders]
            
            # If a folder has files, we want to include it in pubspec.yaml
            if files:
                clean_root = root.replace('\\', '/').strip('/')
                if not clean_root.endswith('/'):
                    clean_root += '/'
                asset_dirs.add(clean_root)

    sorted_assets = sorted(list(asset_dirs))

    with open(pubspec_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    marker_idx = -1
    for i, line in enumerate(lines):
        if marker in line:
            marker_idx = i
            break

    if marker_idx == -1:
        print(f"Error: Marker {marker} not found in {pubspec_path}.")
        return

    # Find where the 'assets:' section starts
    # We look backwards from the marker.
    assets_start_idx = -1
    for i in range(marker_idx - 1, -1, -1):
        if 'assets:' in lines[i]:
            assets_start_idx = i
            break

    if assets_start_idx == -1:
         print("Error: 'assets:' section not found before the marker.")
         return

    # Construct the updated pubspec.yaml lines
    # 1. Keep lines before 'assets:'
    new_content = lines[:assets_start_idx+1]
    
    # 2. Inject detected asset folders with precise 4-space indentation
    for asset in sorted_assets:
        # Sanitize slashes (clean trailing/leading)
        p = asset.strip('/')
        new_content.append(f"    - {p}/\n")
    
    # 3. Add the marker and lines after it
    # Find the indent of the marker or fixed to 4
    new_content.append(f"    {marker}\n")
    new_content.extend(lines[marker_idx+1:])

    # Atomically write? Simpler: overwriting.
    with open(pubspec_path, 'w', encoding='utf-8') as f:
        f.writelines(new_content)
    
    print(f"Successfully updated pubspec.yaml with {len(sorted_assets)} asset directories.")

if __name__ == "__main__":
    update_pubspec()
