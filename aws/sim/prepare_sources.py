"""Collect the installed HDK shell testbench sources for a local Vivado project."""
import os
import subprocess
import sys
from pathlib import Path

root=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'aws/scripts'))
from build_paths import BUILD
fast=os.environ.get('HOGE_HBM_MODEL','amd')=='axi'
work=BUILD/('hdk_sim_axi' if fast else 'hdk_sim')
work.mkdir(parents=True,exist_ok=True)
hdk=Path(os.environ['HDK_DIR'])
sources=[]
includes=[]
generated=work/'include'
generated.mkdir(exist_ok=True)
subprocess.run(['perl',str(hdk/'common/verif/scripts/cl_ports_gen.pl')],
               env={**os.environ,'OUTPUT_DIR':str(generated)},check=True)
includes.append(str(generated))
if fast:
    original=(BUILD/'cl_hoge/design/cl_mem_hbm_wrapper.sv').read_text()
    header=original.split('\n);',1)[0]+'\n);\n'
    (work/'cl_mem_hbm_wrapper.sv').write_text(header+(root/'aws/sim/axi_memory_body.svh').read_text()+'\nendmodule\n')
for line in (hdk/'common/verif/tb/filelists/tb.xsim.f').read_text().splitlines():
    line=os.path.expandvars(line.strip())
    if not line or line.startswith('#') or line.startswith('-define'): continue
    if line.startswith('-include '):
        path=Path(line.split(maxsplit=1)[1])
        if path.is_dir(): includes.append(str(path))
        continue
    # Project IP objects provide their own simulation products and wrappers.
    if '/ip/cl_ip/' in line or line.endswith('/hbm_wrapper.sv'): continue
    if line.startswith('-') or not Path(line).is_file(): raise SystemExit(f'Unexpected HDK file: {line}')
    sources.append(line)
def tcl_list(items): return ' '.join('{'+str(item)+'}' for item in items)
(work/'hdk_sources.tcl').write_text('add_files -fileset sim_1 [list '+tcl_list(sources)+']\nset hdk_includes [list '+tcl_list(includes)+']\n')
