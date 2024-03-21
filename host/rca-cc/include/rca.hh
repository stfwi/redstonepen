/**
 * @file rca.hh
 * @author Stefan Wilhelm
 * @license MIT
 * @std c++17
 *
 * Redstone Logic Control Client Adapter.
 */
#ifndef RLC_CLIENT_ADAPTER__RCA_HH
#define RLC_CLIENT_ADAPTER__RCA_HH
#include "./mmap.hh"
#include "./util.hh"
#include <exception>
#include <stdexcept>
#include <filesystem>
#include <string>

namespace rca {

  /**
   * Redstone Logic Control adapter.
   */
  class rlc_client_adapter
  {
  public: // Types and constants

    using signal_strength_type = int;    // Redstone value representation.
    using signal_word_type = uint64_t;   // Contains all channels.
    using channel_type = size_t;         // RCA channel selection/number, starting at 0.

    static const/*expr*/ std::string mod_input_file_name() noexcept { return "redstonepen.i.mmap"; }
    static const/*expr*/ std::string mod_output_file_name() noexcept { return "redstonepen.o.mmap"; }
    static constexpr size_t num_redstone_channels() noexcept { return 16; }

  public: // Destructor and constructors

    // Default and move construction allowed, copy not.
    ~rlc_client_adapter() noexcept { close(); };
    explicit rlc_client_adapter() noexcept = default;
    rlc_client_adapter(const rlc_client_adapter&) = delete;
    rlc_client_adapter(rlc_client_adapter&&) = default;
    rlc_client_adapter& operator=(const rlc_client_adapter&) = delete;
    rlc_client_adapter& operator=(rlc_client_adapter&&) = default;

  public: // Getters and setters

    /**
     * Returns true if the RCA is opened and
     * without any errors (aka, can be used).
     * @return bool
     */
    bool ok() const noexcept
    { return (!inp_.closed()) && (!out_.closed()); }

    /**
     * Set an RCA input (RLC DI0..DI15) for the mod.
     * (For this application, this is an output, for the mod and input).
     * Does nothing if the channel is out of range, clamps signals to
     * the redstone range (0..15).
     * @param channel_type channel
     * @param signal_strength_type value
     * @return void
     */
    void set_input(channel_type channel, signal_strength_type value) noexcept
    {
      (void)inp_.set(channel_pos(channel), util::nybble_to_hex(std::clamp(value, 0, 15)));
    }

    /**
     * Get an RCA input (RLC DI0..DI15) for the mod.
     * (For this application, this is an output, for the mod and input).
     * Does nothing if the channel is out of range, clamps signals to
     * the redstone range (0..15).
     * @param channel_type channel
     * @return signal_strength_type
     */
    signal_strength_type get_input(channel_type channel) const noexcept
    {
      return util::hex_to_nybble<signal_strength_type>(inp_.get(channel_pos(channel), 0));
    }

    /**
     * Get an RCA output (RLC DO0..D015) for the mod.
     * (For this application, this is an output, for the mod and input).
     * Does nothing if the channel is out of range, clamps signals to
     * the redstone range (0..15).
     * @param channel_type channel
     * @return signal_strength_type
     */
    signal_strength_type get_output(channel_type channel) const noexcept
    {
      return util::hex_to_nybble<signal_strength_type>(out_.get(channel_pos(channel), 0));
    }

    /**
     * Returns all mod output channels, byte 0 is
     * channel 0 etc. Returns 0 on error.
     * @return signal_word_type
     */
    signal_word_type get_outputs() const noexcept
    {
      static constexpr auto num_channels = std::min(sizeof(signal_word_type)*2, num_redstone_channels()); // Redstone 0..15=4bit = 1/2byte
      auto word = signal_word_type(0);
      for(channel_type channel=0; channel<channel_type(num_channels); ++channel) {
        word <<=4;
        word |= static_cast<signal_word_type>(get_output(15u-channel));
      }
      return word;
    }

    /**
     * Returns all mod input channels, byte 0 is
     * channel 0 etc. Returns 0 on error.
     * @return signal_word_type
     */
    signal_word_type get_inputs() const noexcept
    {
      static constexpr auto num_channels = std::min(sizeof(signal_word_type)*2, num_redstone_channels());
      auto word = signal_word_type(0);
      for(channel_type channel=0; channel<channel_type(num_channels); ++channel) {
        word <<=4;
        word |= static_cast<signal_word_type>(get_input(15u-channel));
      }
      return word;
    }

    /**
     * Sets all inputs from a single u64, where bits (ow whole
     * nybbles) can be masked out.
     * @param signal_word_type word
     * @param signal_word_type [mask_out =0]
     * @return void
     */
    void set_inputs(signal_word_type word, signal_word_type mask_out=signal_word_type(0)) noexcept
    {
      static constexpr auto num_channels = std::min(sizeof(signal_word_type)*2, num_redstone_channels());
      word = signal_word_type((word & ~mask_out) | (get_inputs() & mask_out)); // apply mask.
      for(channel_type channel=0; channel<channel_type(num_channels); ++channel) {
        set_input(channel, static_cast<signal_strength_type>(word & 0xf));
        word >>=4;
      }
    }

  public: // Methods

    /**
     * Opens input and output map files,
     * throws std::runtime_error on error.
     * @throw std::runtime_error
     * @return void
     */
    void open()
    {
      using namespace std::filesystem;
      close();
      const auto mod_inp_file = (path(temp_directory_path().string()) / mod_input_file_name()).string();
      if(!inp_.open(mod_inp_file, inp_.flag_shared|inp_.flag_readwrite, num_redstone_channels(), 0)) {
        throw std::runtime_error(std::string("Failed to open mod input file ('") + mod_inp_file + "'), errpr: " + inp_.error_message());
      }
      const auto mod_out_file = (path(temp_directory_path().string()) / mod_output_file_name()).string();
      if(!out_.open(mod_out_file, inp_.flag_shared, num_redstone_channels(), 0)) {
        inp_.close();
        throw std::runtime_error(std::string("Failed to open mod output file ('") + mod_out_file + "'), errpr: " + out_.error_message());
      }
    }

    /**
     * Closes input and output map files.
     * @return void
     */
    void close() noexcept
    {
      inp_.close();
      out_.close();
      // Also remove the files, may fail on windows if Minecraft with RCA is
      // still running - but hey, can't have everything.
      util::unlink(inp_.path());
      util::unlink(out_.path());
    }

  private: // Private functions

    static std::size_t channel_pos(channel_type ch) noexcept
    {
      return num_redstone_channels()-1-ch;
    }

  private: // Instance variables

    ::sw::ipc::memory_mapped_file<char, std::string, std::string> inp_; // mod input map
    ::sw::ipc::memory_mapped_file<char, std::string, std::string> out_; // mod output map
  };

}

#endif
