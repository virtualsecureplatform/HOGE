#!/bin/python3

import numpy as np
from jinja2 import Template, Environment, FileSystemLoader

data ={"buss":[[str(x+1).zfill(2),str(x)] for x in range(20)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("axisiks.template").render(data) #Load template
with open("AXISIKS_Top.v","w") as f:
    f.write(template_result) #generate c++ code