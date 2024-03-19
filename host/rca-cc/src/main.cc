/**
 * @file main.cc
 * @author Stefan Wilhelm
 * @license MIT
 * @std c++17
 *
 * Redstone Logic Control RCA test/example connector
 * application in c++.
 */
#include <util.hh>
#include <rca.hh>
#include <string>
#include <cstdint>


/**
 * Application entry point, RCA example use.
 * @param int argc
 * @param const char* argv[]
 * @return int
 */
int main(int argc, const char* argv[])
{
  using namespace std;
  using util::println;

  // Get the time after which the programm shall be aborted via CLI arg, default 1min.
  const auto args = util::cli_arguments<std::string_view>(argc, argv);
  auto test_run_time = uint32_t(60000); // ms
  if(args.size() > 0) {
    const auto deadline_ms = util::to_number<uint32_t>(std::string(args.front()));
    if(!deadline_ms.has_value()) {
      println("Only CLI argument possible for this example is the test run time in milliseconds.");
      return 1;
    } else {
      test_run_time = deadline_ms.value();
      println("Test run-time set to", test_run_time, "via CLI argument.");
    }
  }

  // Create and open RCA
  auto rca = rca::rlc_client_adapter();
  try {
    rca.open();
    rca.set_inputs(0);
  } catch(const std::exception& e) {
    println("Error: ", e.what());
    return 1;
  }

  // All channels as 64bit number.
  auto last_rlc_outputs = rca.get_outputs();

  // Main (infinite) loop, "tick" interval 1/2 Minecraft tick.
  for(uint32_t tick=0; rca.ok() && util::clock_ms()<test_run_time; ++tick) {
    util::sleep_ms(10);

    // Example, steady counter DI0: Incremented every 100ms, wrap-over at 15 (0xf).
    if(tick % 25 == 0) {
      rca.set_input(0, (rca.get_input(0)+1) & 0xf);
    }

    // Example, gather output and input words, all on one.
    auto log_this_tick = false;
    const auto rlc_outputs = rca.get_outputs();
    const auto rlc_inputs = rca.get_inputs();

    // Reflect all channels (DOx->DIx) except DO0/DI0
    if(rlc_outputs != last_rlc_outputs) {
      last_rlc_outputs = rlc_outputs;
      log_this_tick = true;
      rca.set_inputs(rlc_outputs, 0x000000000000000full);
    }

    if(log_this_tick) {
      println(
        std::string("tick=0x") + util::to_hex(tick),
        std::string("outputs=0x") + util::to_hex(rlc_outputs),
        std::string("inputs=0x") + util::to_hex(rlc_inputs)
      );
    }
  }

  println("exit.");
  return 0;
}
