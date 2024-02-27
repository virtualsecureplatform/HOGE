#!/bin/python3

import numpy as np
from jinja2 import Template, Environment, FileSystemLoader

data ={"buss":[[str(x+1).zfill(2),str(x)] for x in range(10)]}
template_result = Environment(loader=FileSystemLoader('.')).get_template("raiks.template").render(data) #Load template
with open("RAIKSTop.v","w") as f:
    f.write(template_result) #generate c++ code