#!/bin/bash
singularity build hoge-test.sif Singularity.def
singularity exec --bind $PWD:/HOGE hoge-test.sif bash -c "cd /HOGE/chisel/HomGate && sbt run && cd ../../ && cmake -B build -G Ninja && cd build && ninja && ulimit -s unlimited && ./chisel/HomGate/src/test/cpp/cpptest"
