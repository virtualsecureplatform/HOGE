This is an FPGA implementation of TFHE presented in the Special-Session of ASP-DAC 2024 as "HOGE: Homomorphic Gate on An FPGA".

The main source codes are  under chisel/HomGate/src/main/scala.
The `python` directory contains the script to generate the Verilog wrapper to make our codes compilable using Vitis RTL Kernel Wizerd. 

If you are trying to run our xclbin on a real FPGA, please modify CMakeLists.txt for testing. 

To Verilog emulation testbench using Verilator, run `docker-test.bash`.
