import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from check_implementation import check


class ImplementationChecks(unittest.TestCase):
    def fixture(self,root):
        prefix=Path(root)/'candidate'
        reports={
            'post_route_timing':' 0.010 0.000 0 100 0.015 0.000 0 100 0.000 0.000 0 100\n',
            'route_status':'''Design Route Status
  # of routable nets..................... : 100 :
  # of unrouted nets..................... : 0 :
  # of fully routed nets................. : 100 :
  # of nets with routing errors.......... : 0 :
''',
            'drc':'No violations found.\n',
            'cdc':'No unsafe crossings.\n',
            'bus_skew':'All constraints met.\n',
            'check_timing':'1. checking no_clock (0)\n-------------------------\n\n2. checking constant_clock (0)\n'+'\n'.join('checking '+name+' (0)' for name in [
                'unconstrained_internal_endpoints','no_input_delay','no_output_delay',
                'multiple_clock','generated_clocks','loops','latch_loops']),
            'utilization':'Utilization report\n'}
        for name,text in reports.items(): Path(str(prefix)+'.'+name+'.rpt').write_text(text)
        return prefix

    def test_missing_reports_are_not_success(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertTrue(check(Path(d)/'missing'))

    def test_setup_hold_and_recovery_violations_rejected(self):
        for text in [
            '-0.010 -0.010 1 100 0.015 0.000 0 100 0.000 0.000 0 100\n',
            '0.010 0.000 0 100 -0.015 -0.015 1 100 0.000 0.000 0 100\n',
            '0.010 0.000 0 100 0.015 0.000 0 100 0.000 0.000 0 100\nSlack (VIOLATED): -0.1 Recovery\n']:
            with self.subTest(text=text),tempfile.TemporaryDirectory() as d:
                p=self.fixture(d)
                Path(str(p)+'.post_route_timing.rpt').write_text(text)
                self.assertTrue(check(p))

    def test_unsafe_cdc_and_unconstrained_paths_rejected(self):
        for suffix,text in [('cdc','CDC-1 Critical 1'),('check_timing','checking unconstrained_internal_endpoints (1)'),('bus_skew','Slack (VIOLATED): -0.01')]:
            with self.subTest(suffix=suffix),tempfile.TemporaryDirectory() as d:
                p=self.fixture(d);Path(str(p)+'.'+suffix+'.rpt').write_text(text)
                self.assertTrue(check(p))

    def test_complete_reports_pass(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual(check(self.fixture(d)),[])

    def test_partial_routing_is_not_success(self):
        for original, replacement in [
            ('fully routed nets................. : 100', 'fully routed nets................. : 99'),
            ('routing errors.......... : 0', 'routing errors.......... : 1'),
            ('unrouted nets..................... : 0', 'unrouted nets..................... : 1')]:
            with self.subTest(replacement=replacement), tempfile.TemporaryDirectory() as d:
                p=self.fixture(d); report=Path(str(p)+'.route_status.rpt')
                report.write_text(report.read_text().replace(original,replacement))
                self.assertIn('Routing is not confirmed complete',check(p))

    def test_only_known_static_unclocked_endpoints_are_accepted(self):
        static='''1. checking no_clock (35)
-------------------------
 There are 34 register/latch pins with no clock driven by root clock pin: SL/UC_PCIE4_BRIDGE/inst/pcie4_ip_i/inst/bufg_gt_sysclk/O (HIGH)
<hidden>
 There is 1 register/latch pin with no clock driven by root clock pin: WRAPPER/RL/HOST_PCIE4C/inst/bufg_gt_sysclk/O (HIGH)
<hidden>
'''
        for extra in [False,True]:
            with self.subTest(extra=extra),tempfile.TemporaryDirectory() as d:
                p=self.fixture(d); report=Path(str(p)+'.check_timing.rpt')
                text=report.read_text().replace('1. checking no_clock (0)\n-------------------------\n',static)
                if extra: text=text.replace('no_clock (35)','no_clock (36)')
                report.write_text(text)
                self.assertEqual(bool(check(p)),extra)


if __name__=='__main__': unittest.main()
