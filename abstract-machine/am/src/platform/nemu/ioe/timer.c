#include <am.h>
#include <nemu.h>

static uint64_t boot_time = 0;

// 从RTC MMIO读出当前的微秒计数（高低32位组成）
static inline uint64_t read_time_us() {
  uint32_t hi, lo;
  do {
    hi = inl(RTC_ADDR + 4);
    lo = inl(RTC_ADDR + 0);
  } while (hi != inl(RTC_ADDR + 4));  // 处理低位读出时的可能进位
  return ((uint64_t)hi << 32) | lo;
}

void __am_timer_init() {
  boot_time = read_time_us();
}

void __am_timer_uptime(AM_TIMER_UPTIME_T *uptime) {
  uptime->us = read_time_us() - boot_time;
}

void __am_timer_rtc(AM_TIMER_RTC_T *rtc) {
  uint64_t secs = read_time_us() / 1000000;

  rtc->second = secs % 60;
  rtc->minute = (secs / 60) % 60;
  rtc->hour   = (secs / 3600) % 24;

  uint64_t days = secs / 86400;

  // 1970-01-01 as epoch (UTC)
  int year = 1970;
  while (1) {
    int leap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    int ydays = leap ? 366 : 365;
    if (days < (uint64_t)ydays) break;
    days -= ydays;
    year++;
  }

  static const int mdays_norm[12] = {31,28,31,30,31,30,31,31,30,31,30,31};
  int month = 0;
  while (1) {
    int leap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    int mdays = mdays_norm[month];
    if (month == 1 && leap) mdays++;  // February in leap year
    if (days < (uint64_t)mdays) break;
    days -= mdays;
    month++;
  }

  rtc->year  = year;
  rtc->month = month + 1;
  rtc->day   = (int)days + 1;
}