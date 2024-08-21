/**
 * @file test-pcb-firmware.ino
 * @author sw
 * @date 2018-04|2024-08
 * @version 0.2
 * @license MIT
 * @target arduino nano/CH340
 *
 * Firmware for test a electronics compilation board with mainly passive
 * electronic components:
 *
 * - a LDR with 5.1k voltage divider on A1 (LDR low side, resistance range about 15k to 0.1k).
 * - a 10k potentiometer voltage divider on A2.
 * - a NTC with 10k voltage divider on A7 (NTC R0@25'C 10k, B=?, NTC high side).
 * - a Motorola 8905958460 60-120kPa pressure sensor on A6 (5V, pin1 out, pin2 Vcc, pin3 GND).
 * - a Alpin EC111 jog dial (quadrature encoder) on D10=A, D11=Vcc, D12=B, Vcc and A/B protected using 10k, encoder pulls to GND.
 * - a Reed contact input on A4 (10k pull-up, contact to GND).
 * - a Reed relay output on D2 (HE3631, 20mA, driven via ULN2803).
 * - a AZ850-5 signal relay on D3 (5V, 180ohms coil resistance, driven via ULN2803, out 2A@30VDC / 0.5A@125VAC).
 * - a blue LED on D4 (via ULN2803, voltage drop Vfw=3.4V, 330+10 Ohms).
 * - a white LED on D5 (via ULN2803, voltage drop Vfw=3.2V approx), 330+10 Ohms).
 * - a green LED on D6 (via ULN2803, voltage drop Vfw=3.2V, 330+10 Ohms).
 * - a buzzer on D8 (via ULN2803).
 *
 * ----
 *
 * Notes:
 *
 * - Default until communicating: Set outputs set depending on the LDR and potentiometer thresholds.
 * - Jog dial better with interrupts, here "fast" polling until it's a problem.
 * - Output CSV with CSV headers on startup, 115kbit/s.
 * - For CH340, select "Arduino Nano", processor "AtMega328p (old bootloader)".
 * - Small sketch, a bit of magic numbering is ok.
 * - c++ features updated, <type_traits> or <concepts> not supported yet.
 * - Temporary heap allocation in `communication_parse_line()`, other mem static.
 * - RCA communication added using `with_rca_communication` flag.
 */
#include <stdint.h>

//------------------------------------------------------------------------
// Global variables/settings
//------------------------------------------------------------------------

namespace {

  static constexpr bool with_rca_communication = true;

  using tick_type = uint16_t;

  tick_type raw_adc_dump_interval_ms = 1000;
  tick_type raw_adc_dump_last_time = 0;

  uint32_t rca_inputs = 0;    // Controller outputs = MC RCA inputs
  uint32_t rca_outputs = 0;   // Controller inputs = MC RCA outputs
  bool rca_outputs_changed = false;
}


//------------------------------------------------------------------------
// Utility
//------------------------------------------------------------------------

namespace {

  static tick_type tick_time = 0;

  static inline bool tick() noexcept
  {
    const auto t = tick_type(millis());
    if(t == tick_time) return false;
    tick_time = t;
    return true;
  }

  static inline bool elapsed(tick_type& tck, const tick_type interval) noexcept
  {
    if(interval <= 0) return false; // disabled
    const auto t = tick_type(millis());
    if((t - tck) < interval) return false;
    tck += interval;
    return true;
  }

  template<typename IntegralType, typename IntegralMaskType>
  static inline bool getbit(IntegralType& word, const IntegralMaskType mask) noexcept
  {
    // type_traits: static_assert(std::is_integral<IntegralType>::value && std::is_integral<IntegralMaskType>::value, "getbit() only for integral types.");
    return bool(word & IntegralType(mask));
  }

  template<typename IntegralType, typename IntegralMaskType>
  static inline void setbit(IntegralType& word, const IntegralMaskType mask, const bool value) noexcept
  {
    // type_traits: static_assert(std::is_integral<IntegralType>::value && std::is_integral<IntegralMaskType>::value, "setbit() only for integral types.");
    if(!value) {
      word &= ~IntegralType(mask);
    } else {
      word |= IntegralType(mask);
    }
  }

  template<typename T>
  static inline uint32_t nybble(T val) noexcept
  { return uint32_t((val <= 0) ? (0) : ((val >= 15) ? (15) : (val))); } // just clamp to nybble.

  static inline uint8_t hex_to_nybble(char hex) noexcept
  {
    if((hex >= '0') && (hex <= '9')) return uint8_t(hex-'0');
    if((hex >= 'a') && (hex <= 'f')) return uint8_t(hex-'a'+10);
    if((hex >= 'A') && (hex <= 'F')) return uint8_t(hex-'A'+10);
    return uint8_t(0xff);
  }

}

//------------------------------------------------------------------------
// Communication
//------------------------------------------------------------------------

namespace {

  static constexpr uint8_t serial_buffer_size = 64;
  static char serial_buffer[serial_buffer_size+4]; // Reserve trailing zeros.
  static uint8_t serial_buffer_pos = 0;
  static bool communication_up = false;

  static inline void communication_clear()
  {
    for(uint8_t i=0; i < sizeof(serial_buffer); ++i) { serial_buffer[i] = 0; }
    serial_buffer_pos = 0;
  }

  static inline void communication_parse_line(const char* received_line)
  {
    // Check line, ignore empty
    auto line = String(received_line); // note: temporary heap.
    line.trim();
    if(line.length() == 0) return;
    line.toLowerCase();
    if(!communication_up) {
      Serial.print("# Switching to RCA\n\n");
      communication_up = true;
    }
    if(line.length() != 16) {
      Serial.print("# Invalid line length, must be 16 hex digits.\n");
      return;
    }

    // Parse RCA output word, as we don't have more than 8 channels,
    // a 32bit number suffices.
    auto outputs = uint32_t(0);
    for(uint8_t i=0; i<16; ++i) {
      const auto val = hex_to_nybble(line[15-i]); // parse big endian.
      if(val == 0xff) {
        Serial.print("# Invalid hex digit in line '");
        Serial.print(line);
        Serial.print("'\n");
        return;
      }
      outputs |= uint32_t(val) << (4u*i);
    }
    // Assign and notify.
    if(rca_outputs != outputs) {
      rca_outputs = outputs;
      rca_outputs_changed = true;
    }
  }

  static inline void communication_update()
  {
    while(Serial.available()) {
      const char c = Serial.read();
      if(c >= ' ') {
        if(c > 0x7e) {
          // Invalid non-ASCII character.
          serial_buffer_pos = serial_buffer_size;
        } else if(serial_buffer_pos < serial_buffer_size) {
          // Valid character
          serial_buffer[serial_buffer_pos++] = c;
        }
      } else if((c == '\n') || (c == '\r')) {
        // Line termination.
        if(serial_buffer_pos >= serial_buffer_size) {
          // Parse error: Invalid character.
          while(Serial.available()) { (void)Serial.read(); }
          Serial.print("#err: line too long\n");
        } else {
          // Possibly valid line.
          serial_buffer[serial_buffer_pos] = '\0';
          communication_parse_line(serial_buffer);
        }
        communication_clear();
      } else {
        // Unaccepted control character, spaces are ignored.
        serial_buffer_pos = serial_buffer_size;
      }
    }
  }

}

//------------------------------------------------------------------------
// Analog inputs
//------------------------------------------------------------------------

namespace {

  static int16_t adc_raw_ldr = 0;
  static int16_t adc_raw_potentiometer = 0;
  static int16_t adc_raw_air_pressure = 0;
  static int16_t adc_raw_thermistor = 0;

  static inline void analog_initialize()
  {
    adc_raw_ldr = 0;
    adc_raw_potentiometer = 0;
    adc_raw_air_pressure = 0;
    adc_raw_thermistor = 0;
  }

  static inline void analog_update()
  {
    adc_raw_ldr           = int16_t(analogRead(A1));
    adc_raw_potentiometer = int16_t(analogRead(A2));
    adc_raw_air_pressure  = int16_t(analogRead(A6));
    adc_raw_thermistor    = int16_t(analogRead(A7));
  }

}

//------------------------------------------------------------------------
// Digital I/O
//------------------------------------------------------------------------

namespace {

  static constexpr uint16_t jog_dial_count_max = 127;
  static uint16_t jog_dial_count = 0;
  static uint8_t reed_switch = 0;
  static uint8_t output_word = 0;

  static inline void set_onboard_led(const bool en)
  { digitalWrite(LED_BUILTIN, en); setbit(output_word, 1u<<0, en); }

  static inline void set_reed_relay(const bool en)
  { digitalWrite(2, en); setbit(output_word, 1u<<1, en); }

  static inline void set_print_relay(const bool en)
  { digitalWrite(3, en); setbit(output_word, 1u<<2, en); }

  static inline void set_blue_led(const bool en)
  { digitalWrite(4, en); setbit(output_word, 1u<<3, en); }

  static inline void set_white_led(const bool en)
  { digitalWrite(5, en); setbit(output_word, 1u<<4, en); }

  static inline void set_green_led(const bool en)
  { digitalWrite(6, en); setbit(output_word, 1u<<5, en); }

  static inline void set_buzzer(const bool en)
  { digitalWrite(8, en); setbit(output_word, 1u<<6, en); }

  static inline void update_jog_dial()
  {
    // @note: Depending on further prints/processing etc, this has to be an ISR.
    static const int8_t counting[16] = {
      0, // 00->00 no change
      -1, // 00->01 --
      +1, // 00->10 ++
      0, // 00->11 inval
      +1, // 01->00 ++
      0, // 01->01 no change
      0, // 01->10 inval
      -1, // 01->11 --
      -1, // 10->00 --
      0, // 10->01 inval
      0, // 10->10 no change
      +1, // 10->11 ++
      0, // 11->00 inval
      +1, // 11->01 ++
      -1, // 11->10 --
      0, // 11->11 no change
    };
    static uint8_t jog_dial_state = 0;
    const auto st = uint8_t((digitalRead(10) ? 1:0) | (digitalRead(12) ? 2:0));
    jog_dial_state = ((jog_dial_state << 2u) | st) & 0xfu;
    const auto inc = counting[jog_dial_state];
    if(inc == 0) return;
    jog_dial_count += inc;
    if(int16_t(jog_dial_count) < 0) {
      jog_dial_count = 0;
    } else if(jog_dial_count > jog_dial_count_max) {
      jog_dial_count = jog_dial_count_max;
    }
  }

  static inline void digital_initialize()
  {
    pinMode(LED_BUILTIN, OUTPUT);
    pinMode(A4, INPUT); // Reed switch
    pinMode(2, OUTPUT); // reed relay
    pinMode(3, OUTPUT); // print relay
    pinMode(4, OUTPUT); // blue LED
    pinMode(5, OUTPUT); // white LED
    pinMode(6, OUTPUT); // green LED
    pinMode(7, INPUT);  // NC
    pinMode(8, OUTPUT); // buzzer
    pinMode(10, INPUT);  // NOT EC111.A
    pinMode(12, INPUT);  // NOT EC111.B
    pinMode(11, OUTPUT); // EC111 A/B pullup via 10k.
    digitalWrite(11, true);
  }

  static inline void digital_update()
  {
    reed_switch = digitalRead(A4) ? 1 : 0;
  }

}

//------------------------------------------------------------------------
// Main
//------------------------------------------------------------------------

static inline void process_signals_no_communication()
{
  // onboard LED on time 127ms of 1024ms.
  set_onboard_led((tick_time & 1023u) < 128);

  // Reed relay actuation depending on light level.
  static constexpr int16_t reedrelay_threshold = 500;
  set_reed_relay(adc_raw_ldr < reedrelay_threshold);

  // LED and print-relay actuation depending on potentiometer.
  set_blue_led(adc_raw_potentiometer >= 200);
  set_white_led(adc_raw_potentiometer >= 400);
  set_green_led(adc_raw_potentiometer >= 600);
  set_print_relay(adc_raw_potentiometer >= 800);
  set_buzzer(adc_raw_potentiometer >= 1000);

  // CSV transmission interval modified by the jog dial
  raw_adc_dump_interval_ms = 1024 - 8 * jog_dial_count;
}

static inline void transmit_csv_header()
{
  Serial.print("\n\nldr,potentiometer,air_pressure,thermistor,jogdial,reed_switch,outputs\n");
}

static inline void transmit_csv()
{
  //@todo check if compiler supports variadics for `print(...)` function template.
  Serial.print(adc_raw_ldr);
  Serial.print(",");
  Serial.print(adc_raw_potentiometer);
  Serial.print(",");
  Serial.print(adc_raw_air_pressure);
  Serial.print(",");
  Serial.print(adc_raw_thermistor);
  Serial.print(",");
  Serial.print(jog_dial_count);
  Serial.print(",");
  Serial.print(reed_switch);
  Serial.print(",");
  Serial.print(output_word);
  Serial.print("\n");
}

static inline void process_signals_rca()
{
  // RCA output handling
  {
    if(rca_outputs_changed) {
      rca_outputs_changed = false;
      set_reed_relay( rca_outputs & 0x0000000fu);
      set_print_relay(rca_outputs & 0x000000f0u);
      set_blue_led(   rca_outputs & 0x00000f00u);
      set_white_led(  rca_outputs & 0x0000f000u);
      set_green_led(  rca_outputs & 0x000f0000u);
      set_buzzer(     rca_outputs & 0x00f00000u);
    }
  }

  // RCA input handling
  {
    const auto inputs = uint32_t(0)
      | (nybble((adc_raw_potentiometer+50)>>6)  <<  0) // Linear scaled
      | (nybble((adc_raw_thermistor-500)  >>3)  <<  4) // Empiric, thermistor constant B unknown, "scaled" between room and body temperature.
      | (nybble((1023-adc_raw_ldr)        >>6)  <<  8) // Inverse scaled 10bit to 4bit.
      | (nybble((jog_dial_count+1)        >>1)  << 12) // Scaled/2, clamped to 0xf.
      ;

    if(rca_inputs != inputs) {
      // Update earlier on change (within the next 20ms).
      rca_inputs = inputs;
      if(elapsed(raw_adc_dump_last_time, 50)) raw_adc_dump_last_time = millis()-raw_adc_dump_interval_ms;
    }
  }
}

static inline void transmit_rca()
{
  char line[18];
  ::sprintf(line, "%016x\n", rca_inputs);
  line[17] = '\0';
  Serial.print(line);
}

void setup()
{
  analog_initialize();
  digital_initialize();
  communication_clear();
  Serial.begin(115200);
  if(with_rca_communication) {
    raw_adc_dump_interval_ms = 50;
  }
}

void loop()
{
  communication_update();
  update_jog_dial();
  if(!tick()) return;
  analog_update();
  digital_update();
  if(!with_rca_communication) {
    transmit_csv_header();
    process_signals_no_communication();
    if(elapsed(raw_adc_dump_last_time, raw_adc_dump_interval_ms)) {
      transmit_csv();
    }
  } else {
    process_signals_rca();
    if(elapsed(raw_adc_dump_last_time, raw_adc_dump_interval_ms)) {
      transmit_rca();
    }
  }
}

//---
