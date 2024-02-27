#!/bin/python3

# P = 3*2**30+1
P = 2**33-2**20+1
candidate = []

for i in range(32):
	w = 2**(i+1)
	known = set()
	while not(w in known):
		known.add(w)
		w = w**2 % P
	if 1 in known:
		candidate.append(i+1)
print(candidate)

for i in range(len(candidate)):
	w = 2**candidate[i]
	count = 0

	while w != 1:
		count += 1
		w = w**2 % P
	print(count)

# count = 28

primitive_root = 0

# for i in range(P):
# 	print(i)
# 	if i**(3*2**(30-count))%P == 2**candidate[0]:
# 		primitive_root = i
# 		break

primitive_root = 150937678

# known = set()
# w = 1

# for i in range(P):
# 	print(str(i)+":"+str(w))
# 	if w in known:
# 		print("Error")
# 		break
# 	known.add(w)
# 	w = w*primitive_root %P
