#include "verilated.h"
#include "verilated_vcd_c.h"
#include "Vps2_keyboard.h"

#include <cstdint>
#include <cstdio>
static VerilatedContext* contextp = nullptr;
static Vps2_keyboard* top = nullptr;
static VerilatedVcdC* tfp = nullptr;

static void eval_and_dump() {
    top->eval();
    if (tfp) {
        tfp->dump(contextp->time());
    }
    contextp->timeInc(1);
}

// 系统时钟一个周期（clk 高、低各一次）
static void tick() {
    top->clk = 1;
    eval_and_dump();
    top->clk = 0;
    eval_and_dump();
}

// 前进若干系统时钟周期，并在 ready 时自动读走 fifo 数据
static void advance_and_pop(int cycles) {
    for (int i = 0; i < cycles; ++i) {
        tick();
        if (top->ready) {
            std::printf("t=%llu ns, data=0x%02x, overflow=%d\n",
                        static_cast<unsigned long long>(contextp->time()),
                        top->data, top->overflow);
            top->nextdata = 1;
            tick();
            top->nextdata = 0;
        }
    }
}

// 产生一位 PS/2 数据：在 ps2_clk 的下降沿被采样
static void drive_ps2_bit(uint8_t bit) {
    top->ps2_data = bit & 1;
    advance_and_pop(2);     // 数据建立时间
    top->ps2_clk = 0;       // 下降沿
    advance_and_pop(2);     // 低电平保持
    top->ps2_clk = 1;       // 拉高，准备下一位
    advance_and_pop(2);
}

static uint8_t odd_parity(uint8_t code) {
    // 返回奇校验位：~(^code)
    uint8_t p = 0;
    for (int i = 0; i < 8; ++i) p ^= (code >> i) & 1;
    return static_cast<uint8_t>(~p) & 1;
}

// 发送一个扫描码（起始位 0，8bit 数据，奇校验，停止位 1）
static void send_scan_code(uint8_t code) {
    drive_ps2_bit(0); // start
    for (int i = 0; i < 8; ++i) {
        drive_ps2_bit((code >> i) & 1);
    }
    drive_ps2_bit(odd_parity(code));
    drive_ps2_bit(1); // stop
    advance_and_pop(10); // 码间隔
}

static void sim_init(int argc, char** argv) {
    contextp = new VerilatedContext;
    contextp->commandArgs(argc, argv);

    Verilated::traceEverOn(true);
    top = new Vps2_keyboard;

    tfp = new VerilatedVcdC;
    top->trace(tfp, 0);
    tfp->open("build/ps2_keyboard.vcd");

    // 默认信号
    top->clk = 0;
    top->rst = 1;
    top->ps2_clk = 1;
    top->ps2_data = 1;
    top->nextdata = 0;

    advance_and_pop(10); // 复位保持
    top->rst = 0;
    advance_and_pop(10); // 复位释放后稳态
}

static void sim_exit() {
    if (top) top->final();
    if (tfp) tfp->close();
    delete tfp;
    delete top;
    delete contextp;
}

int main(int argc, char** argv) {
    sim_init(argc, argv);

    // 仿真序列：按 A，松 A，按 S（连击），松 S
    send_scan_code(0x1C); // press 'A'
    send_scan_code(0xF0); // break
    send_scan_code(0x1C); // release 'A'

    send_scan_code(0x1B); // press 'S'
    send_scan_code(0x1B); // keep pressing 'S'
    send_scan_code(0x1B); // keep pressing 'S'
    send_scan_code(0xF0); // break
    send_scan_code(0x1B); // release 'S'

    advance_and_pop(200); // 等待 fifo 读空

    sim_exit();
    return 0;
}

