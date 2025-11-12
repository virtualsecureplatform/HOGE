FROM ubuntu:24.04

LABEL maintainer="nindanaoto(Kotaro MATSUOKA) <matsuoka.kotaro@gmail.com>"

# install build dependencies
RUN apt-get update && apt-get upgrade -y
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y build-essential g++ libomp-dev cmake git libgoogle-perftools-dev verilator ninja-build curl default-jre zlib1g-dev

#sbt
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add && apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y sbt
