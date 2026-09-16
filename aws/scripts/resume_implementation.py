"""Repeat physical implementation with unchanged synthesized RTL and fresh tags."""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from build_paths import ROOT, BUILD


def digest(path):
    with path.open('rb') as f:
        value=hashlib.sha256()
        for chunk in iter(lambda:f.read(1024*1024),b''): value.update(chunk)
        return value.hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('source_tag')
    a=p.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]+',a.source_tag): p.error('Invalid checkpoint tag')
    cl=BUILD/'cl_hoge'; scripts=cl/'build/scripts'; checkpoints=cl/'build/checkpoints'
    source=checkpoints/f'cl_hoge.{a.source_tag}.post_link.dcp'
    inventory=json.loads((cl/'source_inventory.json').read_text())
    for name,expected in inventory['design'].items():
        if digest(cl/'design'/name)!=expected: p.error(f'Staged design changed: {name}; run a full build')
    for name,expected in inventory['sources'].items():
        logic=name.startswith(('vitis/','chisel/')) or name.startswith('aws/rtl/') and name.endswith('.sv') or name in [
            'aws/scripts/generate.py','aws/scripts/f2_wrapper.py','aws/scripts/create_ip.tcl','aws/scripts/synth_cl_hoge.tcl']
        if logic and digest(ROOT/name)!=expected: p.error(f'RTL/IP source changed: {name}; run a full build')
    if not source.is_file(): p.error(f'Missing linked checkpoint: {source}')
    tag=a.source_tag+'_r'+datetime.now(timezone.utc).strftime('%H%M%S')
    target=checkpoints/f'cl_hoge.{tag}.post_link.dcp'
    if target.exists(): p.error('Resume tag already exists; retry with a fresh timestamp')
    inputs={}
    for src,dst in [('aws/rtl/floorplan.xdc','build/constraints/small_shell_cl_pnr_user.xdc'),
                    ('aws/scripts/implement.tcl','build/scripts/implement.tcl'),
                    ('aws/scripts/check_implementation.py','build/scripts/check_implementation.py'),
                    ('aws/scripts/resume_implementation.tcl','build/scripts/resume_implementation.tcl')]:
        shutil.copy2(ROOT/src,cl/dst);inputs[dst]=digest(cl/dst)
    shutil.copy2(source,target)
    (cl/'build/reports'/f'cl_hoge.{tag}.inputs.json').write_text(json.dumps({
        'source_tag':a.source_tag,'linked_checkpoint_sha256':digest(source),
        'design':inventory['design'],'implementation':inputs},indent=2)+'\n')
    env={**os.environ,'CL_DIR':str(cl),'CL':'cl_hoge','SHELL_MODE':'small_shell'}
    print(f'Resuming unchanged RTL as {tag}',flush=True)
    subprocess.run(['vivado','-mode','batch','-source','resume_implementation.tcl',
                    '-log',tag+'.vivado.log','-journal',tag+'.vivado.jou','-tclargs',tag],
                   cwd=scripts,env=env,check=True)
    if not (cl/'build/reports'/f'cl_hoge.{tag}.accepted.txt').is_file():
        raise SystemExit(f'No accepted implementation: inspect {scripts}/{tag}.vivado.log')
    os.environ.update(env)
    os.chdir(scripts)
    spec=importlib.util.spec_from_file_location('hdk_build',scripts/'aws_build_dcp_from_cl.py')
    hdk=importlib.util.module_from_spec(spec);spec.loader.exec_module(hdk)
    hdk.generate_dcp_tarball('cl_hoge',tag,'A1','B2','C0','H2')
    package=checkpoints/f'{tag}.Developer_CL.tar'
    if not package.is_file(): raise SystemExit('HDK package was not produced')
    print(f'F2 implementation and package passed: {package}')


if __name__=='__main__': main()
