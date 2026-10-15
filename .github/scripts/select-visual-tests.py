"""Select screenshot tests only for explicit requests or actual menu changes."""
import os
from pathlib import PurePosixPath
import subprocess

def relevant(paths):
    return any(PurePosixPath(path).name.endswith("Screen.java")
        or PurePosixPath(path).name in {"BoundaryColorControls.java", "BoundarySliderTexture.java", "SettingsViewport.java", "OptionRowLayout.java", "UiStyle.java"}
        for path in paths)

def main():
    head=os.environ.get("VISUAL_HEAD","HEAD")
    base=os.environ.get("VISUAL_BASE","")
    if not base or set(base)=={"0"}: base=head+"^"
    paths=subprocess.check_output(["git","diff","--name-only",base,head],text=True).splitlines()
    enabled=os.environ.get("VISUAL_REQUESTED")=="true" or relevant(paths)
    print("visual="+str(enabled).lower())

if __name__=="__main__": main()
