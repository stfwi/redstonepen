/**
 * @file rca-arduino.ino
 * @author sw
 * @license MIT
 *
 * Redstone Client Adapter - Small Arduino Example
 *
 * The sketch contains some rudimentary programmed functions
 * for I/O via the serial communication peripheral, as well
 * as the controller pin I/O.
 *
 * Written/tested with IDE1.18.19 on Arduino Nano, which
 * has pins "A0" to "A7", "D2" to "D12", and one on-board
 * LED. In this example:
 *
 * The example is intentionally focussed on verbose implementation
 * rather rather than using standard C or contemporary c++
 * library functionality.
 *
 * The functionality for pins are assigned according to the
 * capabilities of the Arduino Nano:
 *
 *  - The pins A0 to A7 are inputs (analog mode)
 *  - The pins 12 to 13 are inputs (digital mode)
 *  - The builtin LED is output (digital mode)
 *  - The pins 2,4,7,8 are outputs (digital mode)
 *  - The pins 3,5,6,9,10,11 are outputs (PWM mode)
 *
 * The RCA channels are the pin numbers of the Arduino,
 * and the builtin LED is the first output channel.
 */
#include <stdint.h>


/**
 * Global state variables. Quite a few Arduino controllers
 * are 8bit'ers, so we prefer byte operations over e.g.
 * using a 64 number to store our RCA redstone channels.
 * The "changed" flags are use to determine when to send
 * data to the host.
 */
namespace {

  uint8_t rca_inputs[16]   = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};  // RCA input state
  uint8_t rca_outputs[16]  = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};  // RCA output state
  bool rca_inputs_changed  = false;                              // RCA input changed flag.

  char serial_rx_buffer[0x41] = {0}; // 64 bytes buffer + 1 byte reserved for string termination.
  uint8_t serial_rx_position  = 0;   // Number of bytes in serial_rx_buffer.

  const uint16_t sampling_interval_ms = 5;        // I/O tick rate in milliseconds.
  const uint16_t transmission_interval_ms = 100;  // I/O input data transmission interval in milliseconds. Change interval to 0 for response based transmission.
}

/**
 * Pin I/O functions.
 */
namespace {

  /**
   * Assigns the signal value of a GPIO to a RCA channel.
   * The values assigned are pin HIGH=15 or pin LOW=0.
   * Sets `rca_inputs_changed` if the new reading differs.
   *
   * @param const uint8_t channel
   * @param const uint8_t pin
   * @return void
   */
  void get_redstone_dio(const uint8_t channel, const uint8_t pin) noexcept
  {
    const uint8_t new_value = digitalRead(pin) ? 15 : 0;
    if(new_value != rca_inputs[channel]) {
      rca_inputs_changed = true;
      rca_inputs[channel] = new_value;
    }
  }

  /**
   * Assigns the signal value of a RCA channel to a GPIO pin.
   * The values assigned are HIGH if the channel has at least
   * signal vlaue 1, and LOW if the redstone channel is 0.
   *
   * @param const uint8_t channel
   * @param const uint8_t pin
   * @return void
   */
  void set_redstone_dio(const uint8_t channel, const uint8_t pin) noexcept
  {
    digitalWrite(pin, (rca_outputs[channel] > 0) ? HIGH : LOW);
  }

  /**
   * Assigns the signal value of an ADC to a RCA channel.
   * Sets `rca_inputs_changed` if the new reading differs.
   *
   * Arduino analogRead is normalized to 10bit, 0..1023
   * (even if the ADC has higher resolution). Redstone
   * has 4bit 0..15. So our scaler is 1/64, or faster,
   * >>6.
   *
   * @param const uint8_t channel
   * @param const uint8_t pin
   */
  void get_redstone_adc(const uint8_t channel, const uint8_t pin) noexcept
  {
    const uint8_t new_value = static_cast<uint8_t>(analogRead(pin) >> 6);
    if(new_value != rca_inputs[channel]) {
      rca_inputs_changed = true;
      rca_inputs[channel] = new_value;
    }
  }

  /**
   * Assigns the signal value of an RCA channel to a controller
   * PWM channel.
   *
   * Arduino analogWrite is normalized to 8bit, 0..255. To scale
   * up the value from 4bit to 8bit fast and with respect to the
   * full-scale-output of the PWM value, simply repeat the input
   * nibble, so 1 becomes 0x11, 3 0x33, 10 0xaa, and 15 0xff.
   *
   * @param const uint8_t channel
   * @param const uint8_t pin
   */
  void set_redstone_pwm(const uint8_t channel, const uint8_t pin) noexcept
  {
    const uint8_t new_value = rca_outputs[channel];
    analogWrite(pin, (new_value<<4) | (new_value<<0));
  }

  /**
   * I/O assignments.
   */
  void process_pin_ios() noexcept
  {
    // Inputs (args: rca channel, pin)
    get_redstone_adc( 0, A0);
    get_redstone_adc( 1, A1);
    get_redstone_adc( 2, A2);
    get_redstone_adc( 3, A3);
    get_redstone_adc( 4, A4);
    get_redstone_adc( 5, A5);
    get_redstone_adc( 6, A6);
    get_redstone_adc( 7, A7);
    get_redstone_dio(12, 12);
    get_redstone_dio(12, 13);

    // Outputs (args: rca channel, pin)
    // Example assignment:
    //   - Channel 0 is LED, all others have the same pin like the RCA channels.
    //   - Channel 1 does not exist on Nano.
    //   - First 5 channels PWM, others logic.
    set_redstone_dio( 0, LED_BUILTIN);
    set_redstone_dio( 2, 2);
    set_redstone_pwm( 3, 3);
    set_redstone_dio( 4, 4);
    set_redstone_pwm( 5, 5);
    set_redstone_pwm( 6, 6);
    set_redstone_dio( 7, 7);
    set_redstone_dio( 8, 8);
    set_redstone_pwm( 9, 9);
    set_redstone_pwm(10,10);
    set_redstone_pwm(11,11);
  }

}

/**
 * Serial I/O functions.
 */
namespace {

  /**
   * Prints all input channels to the serial peripheral.
   * Format is "%16x\n", aka 16 lower case hex digits and LF
   * as line termination.
   */
  void process_serial_output() noexcept
  {
    // We reserve the needed number of hex digits + one for '\n'.
    static const uint8_t last_index = sizeof(rca_inputs)-1;
    char line[sizeof(rca_inputs)+1];
    for(uint8_t i=0; i<sizeof(rca_inputs); ++i) {
      const char value = static_cast<char>(rca_inputs[last_index-i] & 0xf);
      if(value < 10) {
        line[i] = '0' + value; // 0-9 -> '0' -> '9'
      } else {
        line[i] = ('a'-char(10)) + value; // 10-15 -> 'a' -> 'f'
      }
    }
    line[sizeof(line)-1] = '\n';
    Serial.write(line, sizeof(line));
  }

  /**
   * Serial input line parser. Sets the I/O ports according
   * to the incoming hex nibbles. The input channels are
   * ordered as a big endian uint64_t (channel 15 first,
   * channel 0 last byte).
   */
  bool process_serial_input_line(const char* line) noexcept
  {
    uint8_t channels[sizeof(rca_outputs)]; // Intentionally left uninitialized.
    uint8_t channel_index = 0;
    for(uint8_t line_index=0; line[line_index]; ++line_index) {
      const uint8_t ch = line[line_index];
      if((ch==' ') || (ch=='\t')) {
        continue; // aka poor man's ::isspace(), spaces are ignored.
      } else if((ch>='0') && (ch<='9')) {
        channels[channel_index] = uint8_t(ch-'0'); // decimal hex range
      } else if((ch>='A') && (ch<='F')) {
        channels[channel_index] = uint8_t(ch-'A'+10); // upper case hex range
      } else if((ch>='a') && (ch<='f')) {
        channels[channel_index] = uint8_t(ch-'a'+10); // lower case hex range
      } else {
        return false; // invalid character
      }
      if((++channel_index) > sizeof(channels)) {
        return false; // invalid line, too many hex digits, must be done here to prevent memory overflow.
      }
    }
    if(channel_index != sizeof(channels)) {
      return false; // not all channels set (line too short), invalid.
    } else {
      static const uint8_t last_index = sizeof(rca_outputs)-1;
      for(uint8_t i=0; i<sizeof(channels); ++i) {
        rca_outputs[last_index-i] = channels[i]; // reverse memcpy, big endian number to channels.
      }
      return true;
    }
  }

  /**
   * Serial port communication.
   */
  void process_serial_input() noexcept
  {
    // Read available bytes until newline or no data left.
    bool newline_received = false;
    while(Serial.available() > 0) {
      char ch = Serial.read();
      if((ch=='\n') || (ch=='\r')) {
        serial_rx_buffer[serial_rx_position] = '\0'; // newline -> string termination.
        if(serial_rx_position==0) continue; // empty line, also if "\r\n" line termination was sent.
        process_serial_input_line(serial_rx_buffer);
        serial_rx_position = 0;
        newline_received = true;
      } else {
        serial_rx_buffer[serial_rx_position & 0x3f] = ch;
        ++serial_rx_position;
      }
    }
    if(newline_received && (transmission_interval_ms == 0)) {
      process_serial_output();
    }
  }
}


/**
 * Arduino init.
 */
void setup()
{
  // Memory/variables initialization
  for(uint8_t ch_no=0; ch_no<sizeof(rca_inputs); ++ch_no)  { rca_inputs[ch_no]  = 0; }
  for(uint8_t ch_no=0; ch_no<sizeof(rca_outputs); ++ch_no) { rca_outputs[ch_no] = 0; }
  rca_inputs_changed = true;   // Directly send the first data after boot.
  for(uint8_t i=0; i<sizeof(serial_rx_buffer); ++i) serial_rx_buffer[i] = '\0'; // aka memset()
  serial_rx_position = 0;

  // MCU peripheral initialization
  pinMode(LED_BUILTIN, OUTPUT);
  for(uint8_t pin_no= 2; pin_no<=11; ++pin_no) { pinMode(pin_no, OUTPUT); }
  for(uint8_t pin_no=12; pin_no<=13; ++pin_no) { pinMode(pin_no, INPUT); }
  Serial.begin(115200);
}


/**
 * Arduino main loop function.
 */
void loop()
{
  static uint16_t t_last_sample = 0;
  static uint16_t t_last_transmission = 0;
  const uint16_t t_now = static_cast<uint16_t>(millis()); // wrap-over difference is ok.

  // We keep a steady sampling rate. This is generally recommended for
  // micro controller signal processing, especially when any kind of
  // actual signal processing is done ("filters", debouncing, etc).
  // For whatever reason this seems seldom to be taught in school.
  if((t_now - t_last_sample) >= sampling_interval_ms) {
    t_last_sample = t_now;
    process_pin_ios();
  }

  // Communication port I/O processing.
  process_serial_input();
  if((t_now - t_last_transmission) > transmission_interval_ms) { // > for transmission_interval_ms==0 never true.
    t_last_transmission = t_now - 1; // -1 because of > instead of >= above.
    process_serial_output();
  }
}
