#!/usr/bin/env python3
"""Stage a self-contained CL without changing HOGE's U280 build or AWS's checkout."""
import argparse
import os
from pathlib import Path
import shutil
import hashlib
import json
import subprocess
from generate import ROOT, generate
from build_paths import BUILD
from f2_wrapper import generate_hbm

p=argparse.ArgumentParser()
p.add_argument('--rtl',type=Path,required=True,help='Chisel-generated HomGateWrap.v')
a=p.parse_args()
hdk=Path(os.environ['HDK_DIR'])
cl=BUILD/'cl_hoge'
if not a.rtl.is_file(): p.error(f'RTL missing: {a.rtl}')
cl.mkdir(parents=True,exist_ok=True)
# Regenerated AWS IP products stay outside the installed HDK checkout.
for rel in ['cl_ip.srcs/sources_1/ip/cl_hbm/cl_hbm.xci',
            'cl_ip.srcs/sources_1/bd/cl_axi_sc_1x1/cl_axi_sc_1x1.bd',
            'cl_ip.gen/sources_1/bd/cl_axi_sc_1x1/hdl/cl_axi_sc_1x1_wrapper.v']:
    dst=cl/'aws_ip'/rel
    dst.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(hdk/'common/ip/cl_ip'/rel,dst)
template=hdk/'cl/examples/CL_TEMPLATE'
shutil.copytree(template/'build',cl/'build',dirs_exist_ok=True)
for d in ['design','build/reports','build/checkpoints','build/src_post_encryption','build/to_aws']:
    (cl/d).mkdir(parents=True,exist_ok=True)
for f in ['BRBack_top.v','BRFront_top.v','HomGate_control_s_axi.v']:
    shutil.copy2(ROOT/'vitis/rtl'/f,cl/'design'/f)
if a.rtl.resolve()!=(cl/'design/HomGateWrap.v').resolve():
    shutil.copy2(a.rtl,cl/'design/HomGateWrap.v')
# Remove only the obsolete staged wrapper; the U280 source is never touched.
(cl/'design/HomGate_top.v').unlink(missing_ok=True)
for f in (ROOT/'aws/rtl').glob('*.sv'):
    shutil.copy2(f,cl/'design')
generate_hbm(hdk/'cl/examples/cl_mem_perf/design/cl_mem_hbm_wrapper.sv',cl/'design/cl_mem_hbm_wrapper.sv')
shutil.copy2(template/'design/cl_id_defines.vh',cl/'design')
# Distinct subsystem identity for the F2 host backend's preflight check.
f=cl/'design/cl_id_defines.vh';f.write_text(f.read_text().replace("32'h1D51_FEDC", "32'h4847_1D0F"))
(cl/'design/cl_hoge_defines.vh').write_text('`define CL_NAME cl_hoge\n`define FPGA_LESS_RST\n')
generate(cl/'design',hdk)
shutil.copy2(ROOT/'aws/scripts/synth_cl_hoge.tcl',cl/'build/scripts')
shutil.copy2(ROOT/'aws/scripts/implement.tcl',cl/'build/scripts')
shutil.copy2(ROOT/'aws/scripts/check_implementation.py',cl/'build/scripts')
# Apply AWS's HBM timing workaround to this design's actual hierarchy.
f=cl/'build/scripts/build_level_1_cl.tcl'
s=f.read_text().replace('WRAPPER/CL/CL_HBM/HBM_PRESENT_EQ_1.HBM_WRAPPER_I/HBM_CORE_I/inst', 'WRAPPER/CL/HBM/HBM_CORE_I/inst')
anchor='if {[llength [get_cells -quiet WRAPPER/CL/HBM/HBM_CORE_I/inst]] > 0} {'
if s.count(anchor)!=1: raise RuntimeError('AWS HBM workaround anchor changed')
s=s.replace(anchor,'if {[llength [get_cells -quiet WRAPPER/CL/HBM/HBM_CORE_I/inst]] != 1} {error "F2 HBM timing workaround target missing"}\n'+anchor)
# This CL uses the shell clock and its own fixed HBM clock wizard. AWS's
# optional dynamic clock-generator hierarchy is absent, so recipes do not apply.
s=s.replace('source $HDK_SHELL_DIR/build/scripts/aws_clock_properties.tcl',
            'puts "INFO: HOGE uses fixed 250 MHz compute and 450 MHz HBM clocks"')
# Retain the HDK shell linking flow; evaluate both floorplans after the shared
# post-link checkpoint. The local implementation script enforces final checks.
anchor='#######################################\n#\n# MMCM Clock recipe constraints'
if s.count(anchor)!=1: raise RuntimeError('AWS post-link anchor changed')
s=s.split(anchor)[0]+'source ${scripts_dir}/implement.tcl\n'
f.write_text(s)
# The generic template copies all local HDL, preserving upstream license notices.
(cl/'build/constraints/cl_synth_user.xdc').write_text('# No U280 placement constraints are inherited.\n')
(cl/'build/constraints/cl_timing_user.xdc').write_text('# Clock wizard, SmartConnect, and XPM supply their scoped CDC constraints.\n')
shutil.copy2(ROOT/'aws/rtl/floorplan.xdc',cl/'build/constraints/small_shell_cl_pnr_user.xdc')
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
sources=list((ROOT/'vitis/rtl').glob('*.v'))+list((ROOT/'vitis/scripts').glob('*.tcl'))+list((ROOT/'chisel/HomGate/src').rglob('*.scala'))+list((ROOT/'aws/scripts').glob('*.py'))+list((ROOT/'aws/scripts').glob('*.tcl'))+list((ROOT/'aws/rtl').glob('*'))+[ROOT/'vitis/cfg/link_config.cfg']
build_inputs=list((cl/'build/constraints').glob('*'))+[cl/'build/scripts'/n for n in ['implement.tcl','check_implementation.py','synth_cl_hoge.tcl','build_level_1_cl.tcl']]
inventory={'sources':{str(f.relative_to(ROOT)):digest(f) for f in sorted(sources) if f.is_file()},
           'design':{f.name:digest(f) for f in sorted((cl/'design').iterdir()) if f.is_file()},
           'build_inputs':{str(f.relative_to(cl)):digest(f) for f in sorted(build_inputs) if f.is_file()},
           'aws_ip_config':{str(f.relative_to(cl)):digest(f) for f in sorted((cl/'aws_ip').rglob('*')) if f.suffix in ['.xci','.bd']},
           'clocks_mhz':{'compute':250,'hbm_axi':450},'hdk':str(hdk),
           'hdk_commit':subprocess.check_output(['git','-C',str(hdk),'rev-parse','HEAD'],text=True).strip()}
(cl/'source_inventory.json').write_text(json.dumps(inventory,indent=2)+'\n')
print(cl)
