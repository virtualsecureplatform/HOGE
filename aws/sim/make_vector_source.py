"""Reuse the NAND reference/packing code without modifying the U280 source."""
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text()
replacements = {
    '#include <tfhe++.hpp>': '#include <tfhe++.hpp>\nextern void hoge_vector_expected(const void*, size_t);\nextern bool hoge_vector_plain(int);',
    'const bool pa = (binary(engine) > 0);': 'const bool pa = hoge_vector_plain(0);',
    'const bool pb = (binary(engine) > 0);': 'const bool pb = hoge_vector_plain(1);',
    'TFHEpp::SampleExtractIndex<TFHEpp::lvl1param>(res,brres,0);':
    'TFHEpp::SampleExtractIndex<TFHEpp::lvl1param>(res,brres,0);\n'
    '    if (TFHEpp::tlweSymDecrypt<TFHEpp::lvl1param>(res,*sk) != !(pa && pb)) {\n'
    '        std::cerr << "CPU NAND reference failed" << std::endl; return 1;\n'
    '    }\n    hoge_vector_expected(res.data(), sizeof(res));',
}
for old, new in replacements.items():
    if source.count(old) != 1:
        raise SystemExit(f'NAND source changed; review vector exporter: {old}')
    source = source.replace(old, new)
pathlib.Path(sys.argv[2]).write_text(source)
