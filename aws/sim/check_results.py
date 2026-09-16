"""Report completed simulation results; missing success markers never mean PASS."""
import argparse
import pathlib
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('logs', nargs='+', type=pathlib.Path)
parser.add_argument('--kind', choices=('nand', 'hbm-smoke', 'dma-smoke'), default='nand')
args = parser.parse_args()
marker = {'nand':'HOGE_HDK_PASS:', 'hbm-smoke':'HOGE_HDK_HBM_SMOKE_PASS:',
          'dma-smoke':'HOGE_HDK_DMA_SMOKE_PASS:'}[args.kind]
failed = incomplete = False
for path in args.logs:
    text = path.read_text(errors='replace') if path.is_file() else ''
    errors = re.findall(r'^[ \t]*(?:HOGE_HDK_FAIL|Fatal:|Error:|ERROR:|FATAL ERROR).*$', text, re.MULTILINE)
    if errors:
        state = 'FAIL'
        failed = True
        detail = errors[0]
    elif re.search(r'^'+re.escape(marker)+r'.*$',text,re.MULTILINE):
        state = 'PASS'
        detail = next(line for line in text.splitlines() if line.startswith(marker))
    else:
        state = 'INCOMPLETE'
        incomplete = True
        progress = [line for line in text.splitlines() if line.startswith('HOGE_HDK')]
        detail = progress[-1] if progress else 'No simulation result found'
    print(f'{state} ({args.kind}): {path}\n  {detail}')
raise SystemExit(1 if failed else 2 if incomplete else 0)
