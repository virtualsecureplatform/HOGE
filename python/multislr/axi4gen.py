#!/bin/python3

import numpy as np
from jinja2 import Template, Environment, FileSystemLoader

# data ={"buss":[[str(x+1).zfill(2),str(x)] for x in range(10)]}
# template_result = Environment(loader=FileSystemLoader('.')).get_template("axi4iks.template").render(data) #Load template
# print(str(cloud_template_result))
# with open("AXI4IKSTop.v","w") as f:
    # f.write(template_result) #generate c++ code

# data ={"buss":[[str(x+1).zfill(2),str(x)] for x in range(8)]}
# template_result = Environment(loader=FileSystemLoader('.')).get_template("axi4br.template").render(data) #Load template
# with open("AXI4BRTop.v","w") as f:
#     f.write(template_result) #generate c++ code

data ={"buss":[[str(x+1).zfill(2),str(x),str(x+4).zfill(2)] for x in range(8)],"formerbuss":[[str(x).zfill(2),str(x)] for x in range(2)],"laterbuss":[[str(x+2).zfill(2),str(x)] for x in range(2)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("multislr_axi4br.template").render(data) #Load template
with open("AXI4BRTop.v","w") as f:
    f.write(template_result) #generate c++ code

data ={"inbuss":[[str(x).zfill(2),str(x)] for x in range(2)],"outbuss":[[str(x+2).zfill(2),str(x)] for x in range(2)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("multislr_axi4extpformer.template").render(data) #Load template
with open("AXI4ExtpFormerTop2.v","w") as f:
    f.write(template_result) #generate c++ code

data ={"inbuss":[[str(x).zfill(2),str(x)] for x in range(2)],"outbuss":[[str(x+2).zfill(2),str(x)] for x in range(4)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("multislr_axi4extppremiddle.template").render(data) #Load template
with open("AXI4ExtpPreMiddleTop.v","w") as f:
    f.write(template_result) #generate c++ code

data ={"trlwebuss":[[str(x+4).zfill(2),str(x)] for x in range(2)],"nttbuss":[[str(x).zfill(2),str(x)] for x in range(4)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("multislr_axi4extplater.template").render(data) #Load template
with open("AXI4ExtpLaterTop.v","w") as f:
    f.write(template_result) #generate c++ code

data ={"outbuss":[[str(x+2).zfill(2),str(x)] for x in range(2)],"inbuss":[[str(x).zfill(2),str(x)] for x in range(2)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("multislr_axi4extplast.template").render(data) #Load template
with open("AXI4ExtpLastTop2.v","w") as f:
    f.write(template_result) #generate c++ code