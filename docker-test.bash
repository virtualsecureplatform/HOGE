#!/bin/bash
docker buildx build -t hoge-test .
docker run -v $PWD:/HOGE hoge-test bash -c "cd /HOGE/chisel/HomGate && sbt run && cd ../../ && cmake -B build -G Ninja && cd build && ninja && ulimit -s unlimited && ./chisel/HomGate/src/test/cpp/cpptest"
