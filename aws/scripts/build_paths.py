"""One isolated root for all mutable F2 build products."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILD = Path(os.environ.get('HOGE_BUILD_ROOT', ROOT/'aws/build')).resolve()
