import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

LIMIT = 15
ROOT = Path.cwd()


def test_failures():
    lines = []
    for report in sorted(ROOT.glob('versions/*/build/test-results/*/TEST-*.xml')):
        target = report.parts[len(ROOT.parts) + 1]
        try:
            suite = ElementTree.parse(report).getroot()
        except ElementTree.ParseError:
            continue
        for case in suite.iter('testcase'):
            problem = case.find('failure')
            if problem is None:
                problem = case.find('error')
            if problem is None:
                continue
            message = (problem.get('message') or problem.text or '').strip().splitlines()
            name = case.get('classname', '').rsplit('.', 1)[-1] + '.' + case.get('name', '')
            lines.append(f'{target} {name}: {message[0][:160] if message else problem.tag}')
    return lines


def log_problems(path):
    lines = []
    if not path or not Path(path).is_file():
        return lines
    text = Path(path).read_text(encoding='utf-8', errors='replace').splitlines()
    for index, line in enumerate(text):
        if re.search(r'\.java:\d+: error:', line):
            lines.append(re.sub(r'^.*?/(versions|shared)/', r'\1/', line.strip())[:200])
        elif line.startswith('* What went wrong:') and index + 1 < len(text):
            lines.append(text[index + 1].strip()[:200])
        elif re.match(r'> Task \S+ FAILED', line):
            lines.append(line.strip())
    return list(dict.fromkeys(lines))


def main():
    problems = test_failures() + log_problems(sys.argv[1] if len(sys.argv) > 1 else None)
    print('### Build failure summary')
    print()
    if not problems:
        print('- No test or compiler details found; see the job log.')
        return
    for line in problems[:LIMIT]:
        print(f'- `{line}`')
    if len(problems) > LIMIT:
        print(f'- … {len(problems) - LIMIT} more')


if __name__ == '__main__':
    main()
