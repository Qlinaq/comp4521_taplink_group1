"""Package a built TapLink APK and portable Android Studio source (standard library only)."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXCLUDE = {'.git', '.gradle', '.idea', '.kotlin', 'build', 'local.properties',
           '__pycache__', '.DS_Store', 'captures'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT.parent / 'deliverables' / 'TapLink_Handoff.zip')
    args = parser.parse_args()
    output = args.output.resolve()
    if output.is_relative_to(ROOT):
        raise SystemExit('Choose an output location outside the source tree.')
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    if not apk.is_file():
        raise SystemExit('Build assembleDebug before packaging.')
    required = ['README.md', 'API_CONTRACT.md', 'TEAM.json', 'docs/AI_USE.md',
                'gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar']
    for name in required:
        if not (ROOT / name).is_file():
            raise SystemExit('Missing required handoff file: ' + name)
    sources = [p for p in ROOT.rglob('*') if p.is_file()
               and not any(part in EXCLUDE for part in p.relative_to(ROOT).parts)
               and p.suffix.lower() not in {'.jks', '.keystore', '.iml', '.apk', '.log'}]
    metadata = {
        'applicationId': 'com.taplink.app',
        'apk': 'TapLink.apk',
        'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'sourceFileCount': len(sources),
        'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'sourceHasUncommittedChanges': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()),
        'note': 'Debug APK for demonstration. See docs for recorded verification and remaining team metadata.'
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.write(apk, 'TapLink.apk')
        for path in sorted(sources):
            archive.write(path, 'TapLink/' + path.relative_to(ROOT).as_posix())
        archive.writestr('BUILD_INFO.json', json.dumps(metadata, indent=2))
    with zipfile.ZipFile(output) as archive:
        assert archive.testzip() is None
        assert all(not any(part in EXCLUDE for part in name.split('/')) for name in archive.namelist())
    print(json.dumps({'archive': str(output), 'sizeMiB': round(output.stat().st_size / 1048576, 2), **metadata}, indent=2))


if __name__ == '__main__':
    main()
