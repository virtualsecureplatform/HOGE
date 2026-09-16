"""Derive F2 integration from U280 source; never rewrite the U280 wrapper."""
import re
from pathlib import Path


def replace_one(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f'Expected exactly one wrapper anchor: {old!r}')
    return text.replace(old, new)


def pipeline(name, width, source, sink, reset='ap_rst_n & user_rst_n'):
    return f'''hoge_stream_pipeline #(.WIDTH({width}), .STAGES(3)) {name} (
  .clk(ap_clk), .rst_n({reset}),
  .s_data({source[0]}), .s_valid({source[1]}), .s_ready({source[2]}),
  .m_data({sink[0]}), .m_valid({sink[1]}), .m_ready({sink[2]})
);
'''


def generate_homgate(source, output):
    text=Path(source).read_text()
    text=replace_one(text, 'module HomGate #', 'module HomGate_f2 #')
    pattern=r'(axi_datamover_[01])\s+(datamover\d+)\s*\((.*?)\n\);'
    matches=list(re.finditer(pattern,text,re.S))
    if [m[2] for m in matches] != [f'datamover{i:02d}' for i in range(21)]:
        raise ValueError('Expected the original 21 directional DataMovers')
    extra=[]
    def mover(match):
        typ,name,body=match.groups()
        i=int(name[-2:]); direction='s2mm' if i==0 else 'mm2s'
        con=dict(re.findall(r'\.(\w+)\(([^()]*)\)',body))
        def port(p, value):
            nonlocal body
            old=con[p]
            body=replace_one(body,f'.{p}({old})',f'.{p}({value})')
        extra.append(f'wire dm{i:02d}_rst_n;\nhoge_local_reset DMA_RESET_{i} (.clk(ap_clk), .arst_n(ap_rst_n & user_rst_n), .rst_n(dm{i:02d}_rst_n));\n')
        for p in con:
            if p.endswith('aresetn'): port(p,f'dm{i:02d}_rst_n')
        cmd=f's_axis_{direction}_cmd_'
        extra.append(f'wire [103:0] dm{i:02d}_cmd_data; wire dm{i:02d}_cmd_valid, dm{i:02d}_cmd_ready;\n')
        extra.append(pipeline(f'DMA_CMD_{i}',104,
            [con[cmd+s] for s in ['tdata','tvalid','tready']],
            [f'dm{i:02d}_cmd_{s}' for s in ['data','valid','ready']]))
        for s in ['data','valid','ready']: port(cmd+'t'+s,f'dm{i:02d}_cmd_{s}')
        if 1<=i<=12:
            p='m_axis_mm2s_'
            extra.append(f'wire [511:0] dm{i:02d}_data; wire [63:0] dm{i:02d}_keep, dm{i:02d}_unused_keep; wire dm{i:02d}_last, dm{i:02d}_unused_last, dm{i:02d}_valid, dm{i:02d}_ready;\n')
            extra.append(pipeline(f'DMA_READ_{i}',577,
                [f'{{dm{i:02d}_last,dm{i:02d}_keep,dm{i:02d}_data}}',f'dm{i:02d}_valid',f'dm{i:02d}_ready'],
                ['{'+f'dm{i:02d}_unused_last,dm{i:02d}_unused_keep,'+con[p+'tdata']+'}',con[p+'tvalid'],con[p+'tready']]))
            if con[p+'tlast'] or con[p+'tkeep']:
                raise ValueError('Read sideband consumer changed; update F2 mapping')
            for s in ['data','valid','ready','keep','last']: port(p+'t'+s,f'dm{i:02d}_{s}')
        elif i==0:
            extra.append('wire [31:0] f2_out_data; wire [3:0] f2_out_keep; wire f2_out_last, f2_out_valid, f2_out_ready;\n')
            extra.append(pipeline('DMA_WRITE_0',37,
                ["{axi4sout_TLAST,4'hf,axi4sout_TDATA}",'axi4sout_TVALID','axi4sout_TREADY'],
                ['{f2_out_last,f2_out_keep,f2_out_data}','f2_out_valid','f2_out_ready']))
            for s in ['data','valid','ready','keep']: port('s_axis_s2mm_t'+s,'f2_out_'+s)
            extra.append('wire [7:0] dm00_status_raw, dm00_status; wire dm00_status_valid_raw, dm00_status_ready_raw;\n')
            extra.append(pipeline('DMA_STATUS_0',8,
                ['dm00_status_raw','dm00_status_valid_raw','dm00_status_ready_raw'],
                ['dm00_status','dm00_s2mm_sts_tvalid',"1'b1"]))
            port('m_axis_s2mm_sts_tdata','dm00_status_raw')
            port('m_axis_s2mm_sts_tvalid','dm00_status_valid_raw')
            port('m_axis_s2mm_sts_tready','dm00_status_ready_raw')
            extra.append('wire dm00_err_raw; (* SHREG_EXTRACT="NO" *) reg [2:0] dm00_err_pipe=0, dm00_beat_pipe=0;\n'
                'always @(posedge ap_clk) begin\n'
                "  if (!ap_rst_n || !user_rst_n) begin dm00_err_pipe<=0; dm00_beat_pipe<=0; end\n"
                '  else begin dm00_err_pipe<={dm00_err_pipe[1:0],dm00_err_raw}; dm00_beat_pipe<={dm00_beat_pipe[1:0],f2_out_valid && f2_out_ready}; end\n'
                'end\nassign dm00_s2mm_err=dm00_err_pipe[2];\n')
            port('s2mm_err','dm00_err_raw')
        return f'{typ} {name} ({body}\n);'
    text=re.sub(pattern,mover,text,flags=re.S)
    # Keep output framing and completion observation at the actual DMA input.
    start=text.index('reg [11:0] axi4sout_output_word_cnt')
    end=text.index('axi_datamover_0 datamover00',start)
    block=text[start:end].replace('axi4sout_TVALID','f2_out_valid').replace('axi4sout_TREADY','f2_out_ready')
    text=text[:start]+block+text[end:]
    for old,new in [('.io_brvalid(axi4sout_TVALID)','.io_brvalid(dm00_beat_pipe[2])'),
                    ('.io_brready(axi4sout_TREADY)',".io_brready(1'b1)"),
                    ('.io_ap_start(ap_start)','.io_ap_start(f2_kernel_start)'),
                    ('.io_ap_done(ap_done)','.io_ap_done(f2_controller_done)'),
                    ('.io_ap_idle(ap_idle)','.io_ap_idle()'),
                    ('.io_ap_ready(ap_ready)','.io_ap_ready()')]:
        text=replace_one(text,old,new)
    extra.append('wire f2_kernel_start, f2_controller_done, f2_dma_failed;\n'
        'hoge_dma_completion DMA_COMPLETION (.clk(ap_clk), .rst_n(ap_rst_n), .start(ap_start),\n'
        '  .controller_done(f2_controller_done), .status_valid(dm00_s2mm_sts_tvalid), .status_data(dm00_status),\n'
        '  .kernel_start(f2_kernel_start), .done(ap_done), .idle(ap_idle), .failed(f2_dma_failed));\n'
        'assign ap_ready=ap_done;\n')
    text=replace_one(text,'if (dm00_s2mm_err)', 'if (dm00_s2mm_err || f2_dma_failed)')
    text=replace_one(text,'\nendmodule','\n// F2-only physical integration.\n'+''.join(extra)+'\nendmodule')
    Path(output).write_text('// Generated F2 variant; U280 source is unchanged.\n'+text)


def generate_hbm(source, output):
    text=Path(source).read_text()
    old="  lib_pipe #(.WIDTH(1), .STAGES(4)) PIPE_HBM_AXI_RST (.clk(axi_clk), .rst_n(1'b1), .in_bus(i_rst_450m_n), .out_bus(axi_rst_n));"
    new='''  wire [31:0] port_rst_n;
  for (genvar rp=0; rp<32; rp=rp+1) begin: PORT_RESET
    hoge_local_reset LOCAL_RESET (.clk(axi_clk), .arst_n(i_rst_450m_n), .rst_n(port_rst_n[rp]));
  end
  hoge_ready_tree READY_TREE (.clk(axi_clk), .arst_n(i_rst_450m_n),
    .ready_in(port_rst_n), .ready_out(axi_rst_n));
  wire local_reset_ready;
  xpm_cdc_single #(.DEST_SYNC_FF(4), .SRC_INPUT_REG(1)) RESET_READY_CDC
    (.src_clk(axi_clk), .src_in(axi_rst_n), .dest_clk(clk), .dest_out(local_reset_ready));'''
    text=replace_one(text,old,new)
    text=replace_one(text,'(axi_rst_n                      ),  // input wire AXI_ARESET_N',
                         '(port_rst_n[P]                  ),  // input wire AXI_ARESET_N')
    text=replace_one(text,'o_hbm_ready <= &hbm_ready_q;', 'o_hbm_ready <= rst_n && local_reset_ready && (&hbm_ready_q);')
    Path(output).write_text(text)
