#!/bin/python3
import sympy

# for i in range(3,65):
#     for j in range(i):
#         if sympy.isprime(2**i+2**j+1):
#             print(2**i+2**j+1)
#         if sympy.isprime(2**i-2**j+1):
#             print(2**i-2**j+1)
#         if sympy.isprime(2**i+2**j-1):
#             print(2**i+2**j-1)
#         if sympy.isprime(2**i-2**j-1):
#             print(2**i-2**j-1)
for i in range(15,33):
    if sympy.isprime(2**(2*i)+2**i+1):
       print(2**(2*i)+2**i+1)
    if sympy.isprime(2**(2*i)-2**i+1):
       print(2**(2*i)-2**i+1)

for i in range(15,22):
    if sympy.isprime(2**(3*i)+2**(2*i)+2**i+1):
       print(2**(3*i)+2**(2*i)+2**i+1)
    if sympy.isprime(2**(3*i)-2**(2*i)+2**i+1):
       print(2**(3*i)-2**(2*i)+2**i+1)
    if sympy.isprime(2**(3*i)+2**(2*i)-2**i+1):
       print(2**(3*i)+2**(2*i)-2**i+1)
    if sympy.isprime(2**(3*i)-2**(2*i)-2**i+1):
       print(2**(3*i)-2**(2*i)-2**i+1)