#include <nvboard.h>
#include "Vbcd7seg.h"

void nvboard_bind_all_pins(Vbcd7seg* top) {
	nvboard_bind_pin( &top->h, 7, SEG0A, SEG0B, SEG0C, SEG0D, SEG0E, SEG0F, SEG0G);
	nvboard_bind_pin( &top->b, 4, SW3, SW2, SW1, SW0);
	nvboard_bind_pin( &top->en, 1, SW4);
}
