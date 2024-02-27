#!/bin/python3

import numpy as np
from jinja2 import Template, Environment, FileSystemLoader

data ={"ikskbuss":[[str(x+3).zfill(2),str(x),str(hex(x+3))[2:]] for x in range(10)],"bkbuss":[[str(x+13).zfill(2),str(x),str(x+2).zfill(2),str(hex(32-8+x))[2:]] for x in range(8)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("homnand.template").render(data) #Load template
with open("HomNAND.v","w") as f:
    f.write(template_result) #generate verilog

data ={"buss":[[str(x+6).zfill(2),str(x)] for x in range(8)],"inbuss":[[str(x).zfill(2),str(x)] for x in range(4)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("brback.template").render(data) #Load template
with open("BRBack.v","w") as f:
    f.write(template_result) #generate verilog

data ={}
template_result = Environment(loader=FileSystemLoader('.')).get_template("brfront.template").render(data) #Load template
with open("BRFront.v","w") as f:
    f.write(template_result) #generate verilog