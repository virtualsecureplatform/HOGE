#!/bin/python3

import numpy as np
from sympy import ntt, intt
from jinja2 import Template, Environment, FileSystemLoader

P = np.uint64(2**64-2**32+1)
N = 1024
xaitt = []

for i in range(2*N):
    xai = np.zeros(N,np.uint64)
    xai[0] = P-1
    if (i < N):
        xai[i] += np.uint64(1)
    elif(i==N):
        xai[i - N] = P-np.uint64(2)
    else:
        xai[i - N] += P-np.uint64(1)
    xaitt.append(np.array(ntt(xai,P),dtype=np.uint64))
xaitt = np.array(xaitt).flatten()
print(xaitt)

data ={"xaitt":xaitt,"lenxaitt": len(xaitt), "Nbit": 10, "numwords": 32, "rowbit": 5}
template_result = Environment(loader=FileSystemLoader('.')).get_template("xaitt.scala.template").render(data) #Load template
with open("xaitt.scala","w") as f:
    f.write(template_result) #generate verilog