import os
from pathlib import Path
import re
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from generate import generate, ROOT, ports

class WiringTest(unittest.TestCase):
    def test_all_ports_and_streams(self):
        with tempfile.TemporaryDirectory() as d:
            generate(Path(d),Path(os.environ['HDK_DIR']))
            text=(Path(d)/'cl_hoge.sv').read_text()
            metadata=(Path(d)/'physical_metadata.tcl').read_text()
        for module in ['HomGate','BRBack','BRFront']:
            implementation='HomGate_f2' if module=='HomGate' else module
            block=text.split(f'{implementation} {module}_1 (')[1].split(');',1)[0]
            actual=re.findall(r'\.(\w+)\(',block)
            expected=[p for _,_,p in ports(module)]
            self.assertEqual(sorted(actual),sorted(expected))
            self.assertEqual(len(actual),len(set(actual)))
        self.assertEqual(text.count('cl_axi_sc_1x1_wrapper HBM_CONVERTER_'),22)
        self.assertEqual(text.count('.DEPTH(4096)'),8)
        self.assertEqual(text.count('.DEPTH(512)'),8)
        self.assertIn('.apb_clk(clk_hbm_ref)',text)
        self.assertNotIn('xcu280',text)
        # All output/request HBM signals have exactly one source; no spare port floats.
        self.assertIn('assign hbm_arvalid[30]',text)
        self.assertIn('.AXI4_awaddr(pcis_awaddr)',text)
        self.assertIn('hoge_stream_pipeline #(.WIDTH(105)',text)
        self.assertEqual(text.count('"F2 stream overflow:'),6)
        self.assertIn('.i_hbm_apb_preset_n_0(hbm_apb_preset_n_0)',text)
        write_only=text.split('cl_axi_sc_1x1_wrapper HBM_CONVERTER_0 (')[1].split(');',1)[0]
        read_only=text.split('cl_axi_sc_1x1_wrapper HBM_CONVERTER_1 (')[1].split(');',1)[0]
        self.assertIn(".AXI4_arvalid('0)",write_only)
        self.assertIn(".AXI4_awvalid('0)",read_only)
        self.assertIn(".AXI4_wvalid('0)",read_only)
        self.assertIn(".AXI3_wready('0)",read_only)
        self.assertIn(".AXI3_awready('0)",read_only)
        self.assertIn(".AXI3_bvalid('0)",read_only)
        self.assertIn(".AXI3_rvalid('0)",write_only)
        self.assertIn("assign hbm_wvalid[1] = '0;",text)
        self.assertIn("assign hbm_arvalid[0] = '0;",text)
        self.assertIn('{PCIS_W 5 1}',metadata)
        self.assertIn('{PCIS_R 5 0}',metadata)
        self.assertIn('{OCL_W 3 1}',metadata)
        reset_paths=re.findall(r'\{(WRAPPER/CL/[^}]+)\}',metadata)
        self.assertEqual(len(reset_paths),len(set(reset_paths)))
        self.assertIn('WRAPPER/CL/PCIS_W/STAGE[4].RESET',reset_paths)
        self.assertIn('WRAPPER/CL/HBM/READY_TREE/ROOT/RESET',reset_paths)
        self.assertIn('WRAPPER/CL/STREAM_44/RESET',reset_paths)

if __name__=='__main__': unittest.main()
