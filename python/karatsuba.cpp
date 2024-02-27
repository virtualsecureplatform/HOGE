#include <bits/stdint-uintn.h>
#include <cassert>
#include <cstdint>
#include <iostream>
#include <random>

int main(){
	std::random_device seed_gen;
	std::default_random_engine engine(seed_gen());
    std::uniform_int_distribution<uint32_t> uniform(0, UINT32_MAX);

	for(int i = 0; i < 1000; i++){
		const uint32_t x0 = uniform(engine);
		const uint32_t x1 = uniform(engine);
		const uint32_t y0 = uniform(engine);
		const uint32_t y1 = uniform(engine);

		const uint64_t z0 = static_cast<uint64_t>(x0) * y0;
		const uint64_t z2 = static_cast<uint64_t>(x1) * y1;
		const __uint128_t z1 = static_cast<__uint128_t>(z0) + z2 - static_cast<__uint128_t>(static_cast<__int128_t>(x1-static_cast<int64_t>(x0))*(y1-static_cast<int64_t>(y0)));
		const __uint128_t res = (static_cast<__uint128_t>(z2)<<64)+(z1<<32)+z0;
		const __uint128_t trueres = ((static_cast<__uint128_t>(x1)<<32)+x0)*((static_cast<__uint128_t>(y1)<<32)+y0);
		std::cout<<static_cast<uint64_t>(res>>64)<<":"<<static_cast<uint64_t>(trueres>>64)<<std::endl;
		assert(res == trueres);
	}
	std::cout<<"PASS"<<std::endl;
}