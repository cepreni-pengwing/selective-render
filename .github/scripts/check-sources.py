import os
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
CLIENT = 'src/client/java/de/selectiverender'
DEFAULT_KEYS = {'TOGGLE_KEY': 'F9', 'HIDE_TOGGLE_KEY': 'F10', 'SETTINGS_KEY': 'APOSTROPHE'}
DOC_KEYS = {'toggle': 'F9', 'hide': 'F10'}
KEY_NUMBER = re.compile(r'\b(?:key|keyCode|keycode|scancode|scanCode|key\(\)|input\(\))\s*[!=]=\s*\d{2,}|getOrCreate\(\s*\d')


def versions():
    return sorted(path for path in (ROOT / 'versions').iterdir() if (path / CLIENT).is_dir())


def java_files(*folders):
    for folder in folders:
        yield from sorted(folder.rglob('*.java'))


def check_key_numbers(errors):
    modern = [path / 'src' for path in versions() if path.name.startswith('mc26')] + [ROOT / 'shared/modern/src']
    for file in java_files(*modern):
        text = file.read_text(encoding='utf-8')
        rel = file.relative_to(ROOT)
        for number, line in enumerate(text.splitlines(), 1):
            if KEY_NUMBER.search(line):
                errors.append(f'{rel}:{number}: hardcoded key number')
            if 'GLFW_KEY_' in line and (file.name != 'SelectiveRenderClient.java' or 'mc26.3' in str(rel)):
                errors.append(f'{rel}:{number}: GLFW key constant; use InputConstants.KEY_*')


def check_key_defaults(errors):
    for version in versions():
        file = version / CLIENT / 'SelectiveRenderClient.java'
        text = file.read_text(encoding='utf-8')
        for field, key in DEFAULT_KEYS.items():
            match = re.search(rf'\b{field}\s*=\s*new\s+\w+\((.*?)\);', text, re.S)
            if not match:
                errors.append(f'{file.relative_to(ROOT)}: {field} missing')
            elif not re.search(rf'KEY_{key}\b', match.group(1)):
                errors.append(f'{file.relative_to(ROOT)}: {field} default is not {key}')


def check_encoding(errors):
    for file in java_files(ROOT / 'shared', ROOT / 'versions'):
        text = file.read_text(encoding='utf-8')
        for number, line in enumerate(text.splitlines(), 1):
            if re.search('[ÂÃ][\u0080-¿]|â€|�', line):
                errors.append(f'{file.relative_to(ROOT)}:{number}: broken text encoding')


def method_body(text, name):
    start = re.search(rf'\b{name}\(String name\)\s*\{{', text)
    if not start:
        return ''
    depth, index = 1, start.end()
    while depth and index < len(text):
        depth += {'{': 1, '}': -1}.get(text[index], 0)
        index += 1
    return text[start.end():index]


def registered_commands(text):
    root = method_body(text, 'command')
    commands = {}
    for builder, name in re.findall(r'\.then\((\w+)\("([\w-]+)"\)\)', root):
        commands.setdefault(builder, []).append(name)
    for name, action in re.findall(r'literal\("([\w-]+)"\)(.*)', root):
        commands.setdefault(action.strip(), []).append(name)
    result = {}
    for builder, names in commands.items():
        names = sorted(set(names), key=lambda value: (-len(value), value))
        body = method_body(text, builder) if builder.endswith('Command') else ''
        nested = set(re.findall(r'literal\("([\w-]+)"\)', body)) | {
            name for sub, name in re.findall(r'\.then\((\w+)\("([\w-]+)"\)\)', body)}
        result[names[0]] = {'aliases': set(names[1:]), 'subcommands': nested - set(names)}
    return result


def documented_commands(markdown):
    rows = re.findall(r'<tr>(.*?)</tr>', markdown, re.S)
    docs, alias, spanned = {}, None, 0
    for row in rows:
        cells = re.findall(r'<td[^>]*>(.*?)</td>', row, re.S)
        if spanned:
            cells, spanned = [''] + cells, spanned - 1
        else:
            span = re.search(r'<td rowspan="(\d+)"', row)
            spanned = int(span.group(1)) - 1 if span else 0
            alias = None
        if len(cells) < 2:
            continue
        first = re.search(r'/sr&nbsp;([\w-]+)', cells[0])
        if first:
            alias = first.group(1)
        command = re.sub(r'<[^>]+>', ' ', cells[1]).split()
        if not command:
            continue
        entry = docs.setdefault(command[0], {'aliases': set(), 'text': '', 'keys': ''})
        if alias and alias != command[0]:
            entry['aliases'].add(alias)
        entry['text'] += ' ' + re.sub(r'<[^>]+>', ' ', cells[1])
        entry['keys'] += ' ' + re.sub(r'<[^>]+>', ' ', cells[3] if len(cells) > 3 else '')
    return docs


def check_docs(errors):
    sources = [version / CLIENT / 'SelectiveRenderClient.java' for version in versions()]
    registered = [registered_commands(file.read_text(encoding='utf-8')) for file in sources]
    for file, commands in zip(sources[1:], registered[1:]):
        if commands != registered[0]:
            errors.append(f'{file.relative_to(ROOT)}: commands differ from {sources[0].relative_to(ROOT)}')
    commands = registered[0]
    for document in ('README.md', 'MODRINTH.md'):
        docs = documented_commands((ROOT / document).read_text(encoding='utf-8'))
        for name, command in commands.items():
            entry = docs.get(name)
            if entry is None:
                errors.append(f'{document}: /sr {name} missing from command table')
                continue
            for alias in command['aliases'] - entry['aliases']:
                if not re.search(rf'\b{re.escape(alias)}\b', entry['text']):
                    errors.append(f'{document}: alias {alias} of /sr {name} missing')
            for sub in command['subcommands']:
                if not re.search(rf'\b{re.escape(sub)}\b', entry['text']):
                    errors.append(f'{document}: /sr {name} {sub} missing')
        for name in docs:
            if name not in commands:
                errors.append(f'{document}: /sr {name} is documented but not registered')
        for name, key in DOC_KEYS.items():
            if name in docs and key not in docs[name]['keys']:
                errors.append(f'{document}: /sr {name} should list key {key}')
        if not re.search(r'Open settings</td><td><code>\'</code>', (ROOT / document).read_text(encoding='utf-8')):
            errors.append(f"{document}: settings key ' missing")


def main():
    errors = []
    for check in (check_key_numbers, check_key_defaults, check_encoding, check_docs):
        check(errors)
    if errors:
        lines = ['### Source checks failed', ''] + [f'- {error}' for error in errors[:30]]
        if len(errors) > 30:
            lines.append(f'- … {len(errors) - 30} more')
        print('\n'.join(lines))
        summary = os.environ.get('GITHUB_STEP_SUMMARY')
        if summary:
            with open(summary, 'a', encoding='utf-8') as output:
                output.write('\n'.join(lines) + '\n')
        return 1
    print('Source checks passed')
    return 0


if __name__ == '__main__':
    sys.exit(main())
