"""Fail closed on absent/violated routed implementation reports."""
import argparse
import re
from pathlib import Path


def check(prefix):
    errors=[]
    reports={}
    for suffix in ['post_route_timing','route_status','drc','cdc','bus_skew','check_timing','utilization']:
        p=Path(str(prefix)+'.'+suffix+'.rpt')
        if not p.is_file() or not p.stat().st_size:
            errors.append(f'Missing {suffix} report')
        else: reports[suffix]=p.read_text()
    if errors: return errors
    timing=reports['post_route_timing']
    match=re.search(r'^\s*(-?\d+\.\d+)\s+(-?\d+\.\d+)\s+(\d+)\s+\d+\s+(-?\d+\.\d+)\s+(-?\d+\.\d+)\s+(\d+)\s+\d+\s+(-?\d+\.\d+)\s+(-?\d+\.\d+)\s+(\d+)\s+\d+\s*$',timing,re.M)
    if not match or any(float(v)<0 for v in match.groups()) or (match and any(int(match[i]) for i in [3,6,9])):
        errors.append('Setup/hold/pulse-width timing is missing or violated')
    if 'VIOLATED' in timing: errors.append('Timing path violation')
    for category in ['unconstrained_internal_endpoints','no_input_delay','no_output_delay','multiple_clock','generated_clocks','loops','latch_loops']:
        m=re.search(r'checking '+category+r'\s*\((\d+)\)',reports['check_timing'])
        if not m or int(m[1]): errors.append(f'Unresolved timing check: {category}')
    # Match the observed static Shell baseline exactly; do not excuse new
    # unclocked endpoints just because their encrypted names are hidden.
    no_clock = re.search(
        r'^\d+\. checking no_clock \((\d+)\)\n-+\n(.*?)(?=\n\d+\. checking|\Z)',
        reports['check_timing'], re.M | re.S)
    static_roots = {
        'SL/UC_PCIE4_BRIDGE/inst/pcie4_ip_i/inst/bufg_gt_sysclk/O': 34,
        'WRAPPER/RL/HOST_PCIE4C/inst/bufg_gt_sysclk/O': 1,
    }
    if not no_clock:
        errors.append('Missing detailed no_clock check')
    elif int(no_clock[1]):
        roots = {root: int(count) for count, root in re.findall(
            r'There (?:are|is) (\d+) register/latch pins? with no clock driven by root clock pin: (\S+) \(HIGH\)',
            no_clock[2])}
        if int(no_clock[1]) != 35 or roots != static_roots or 'WRAPPER/CL/' in no_clock[2]:
            errors.append('Unreviewed unclocked endpoint')
    route_counts = {
        name: int(value) for name, value in re.findall(
            r'# of ([a-z ]+?)\.*\s*:\s*([0-9]+)\s*:',
            reports['route_status'], re.I)
    }
    if (route_counts.get('routable nets', 0) <= 0
            or route_counts.get('fully routed nets') != route_counts.get('routable nets')
            or route_counts.get('nets with routing errors') != 0
            or route_counts.get('unrouted nets', 0) != 0
            or route_counts.get('nets with no placed pins', 0) != 0):
        errors.append('Routing is not confirmed complete')
    if re.search(r'\b(?:ERROR|Error)\b',reports['drc']):
        errors.append('Implementation DRC error requires review')
    if re.search(r'\bCritical\b',reports['cdc']):
        errors.append('Critical CDC finding requires review')
    if re.search(r'VIOLATED|Slack\s*[:=]\s*-',reports['bus_skew'],re.I):
        errors.append('Bus skew violation')
    return errors


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('prefix',type=Path)
    args=p.parse_args(); errors=check(args.prefix)
    if errors: raise SystemExit('\n'.join(errors))
    print(f'HOGE_IMPLEMENTATION_PASS: {args.prefix}')
