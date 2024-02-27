#!/bin/python3

import numpy as np
from jinja2 import Template, Environment, FileSystemLoader

data ={"buss":[[str(x+2).zfill(2),str(x)] for x in range(8)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("axisbr.template").render(data) #Load template
with open("AXISBR.v","w") as f:
    f.write(template_result) #generate c++ code

data ={"passbuss":[[str(x),str(x+6).zfill(2),str(x+10).zfill(2)] for x in range(4)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("axisbrfront.template").render(data) #Load template
with open("AXISBRFront.v","w") as f:
    f.write(template_result) #generate c++ code

data ={}
template_result = Environment(loader=FileSystemLoader('.')).get_template("axisbrback.template").render(data) #Load template
with open("AXISBRBack.v","w") as f:
    f.write(template_result) #generate verilog