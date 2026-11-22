"""Small Android UI inspection helper for repeatable, installed-APK verification.

Uses the Android SDK's adb and UIAutomator hierarchy. It never writes directly to
the wallet database. Select targets from a fresh hierarchy before sending input.
"""
from pathlib import Path
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET


class AndroidUI:
    def __init__(self, output, serial='emulator-5554', adb=None):
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        if not sdk:
            sdk = str(Path.home() / 'AppData/Local/Android/Sdk')
        self.executable = adb or str(Path(sdk) / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb'))
        self.serial = serial
        self.output = Path(output)
        self.output.mkdir(parents=True, exist_ok=True)

    def run(self, *args, binary=False):
        result = subprocess.run([self.executable, '-s', self.serial, *args],
                                capture_output=True, timeout=40, check=True)
        return result.stdout if binary else result.stdout.decode('utf-8', errors='replace').strip()

    def hierarchy(self):
        self.run('shell', 'uiautomator', 'dump', '/sdcard/taplink_ui.xml')
        raw = self.run('exec-out', 'cat', '/sdcard/taplink_ui.xml', binary=True)
        return ET.fromstring(raw)

    def nodes(self):
        root = self.hierarchy()
        return [{'text': n.get('text'), 'description': n.get('content-desc'),
                 'class': n.get('class'), 'bounds': n.get('bounds'),
                 'enabled': n.get('enabled')} for n in root.iter('node')
                if n.get('text') or n.get('content-desc') or n.get('class') == 'android.widget.EditText']

    def find(self, text=None, description=None, cls=None, label=None):
        matches = [n for n in self.hierarchy().iter('node')
                   if (text is None or n.get('text') == text)
                   and (description is None or n.get('content-desc') == description)
                   and (cls is None or n.get('class') == cls)
                   and (label is None or any(c.get('text') == label for c in n.iter('node')))]
        if len(matches) != 1:
            raise AssertionError(f'Expected one UI target, found {len(matches)}: {text=}, {description=}, {cls=}')
        return matches[0]

    def tap(self, **selector):
        node = self.find(**selector)
        if node.get('enabled') == 'false':
            raise AssertionError('Target is disabled')
        left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
        self.run('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

    def type_ascii(self, value, **selector):
        if not value.isascii() or any(c.isspace() for c in value):
            raise ValueError('Use an ASCII token without spaces to avoid device IME substitutions.')
        self.tap(**selector)
        self.run('shell', 'input', 'text', value)

    def replace_ascii(self, value, commit_word=False, **selector):
        """Replace a focused field; commit_word accepts a Gboard composing word."""
        self.tap(**selector)
        self.run('shell', 'input', 'keycombination', '113', '29')
        self.run('shell', 'input', 'keyevent', '67')
        self.run('shell', 'input', 'text', value)
        if commit_word:
            self.run('shell', 'input', 'keyevent', '62')
        self.run('shell', 'input', 'keyevent', '4')

    def scroll_to(self, text, attempts=8, up=False):
        for _ in range(attempts):
            root = self.hierarchy()
            if any(n.get('text') == text for n in root.iter('node')):
                return
            areas = [n for n in root.iter('node') if n.get('scrollable') == 'true']
            if len(areas) != 1:
                raise AssertionError('Expected one scrollable content area')
            l, t, r, b = map(int, re.findall(r'\d+', areas[0].get('bounds')))
            start, end = (t+(b-t)//5, t+(b-t)*4//5) if up else (t+(b-t)*4//5, t+(b-t)//5)
            self.run('shell', 'input', 'swipe', str((l+r)//2), str(start),
                     str((l+r)//2), str(end), '350')
        raise AssertionError('Cannot scroll to ' + text)

    def screenshot(self, name):
        path = self.output / (name + '.png')
        path.write_bytes(self.run('exec-out', 'screencap', '-p', binary=True))
        return path

    def restart(self):
        self.run('shell', 'am', 'force-stop', 'com.taplink.app')
        return self.run('shell', 'am', 'start', '-W', '-n', 'com.taplink.app/.MainActivity')

    def wait_for(self, text, attempts=8):
        for _ in range(attempts):
            if any(n.get('text') == text for n in self.hierarchy().iter('node')):
                return
            time.sleep(.25)
        raise AssertionError('Expected UI text not found: ' + text)
