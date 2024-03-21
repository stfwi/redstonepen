/***
 * @file utility.hh
 * @author Stefan Wilhelm
 * @license MIT
 * @std c++17
 *
 * Utility functions for this RCA project to
 * make some functions more readable. Some of
 * that will likely resolve itself when c++20
 * is out.
 */
#ifndef RCA_UTILITY_HH
#define RCA_UTILITY_HH
#include <type_traits>
#include <filesystem>
#include <cinttypes>
#include <iostream>
#include <optional>
#include <cstdint>
#include <string>
#include <limits>
#include <chrono>
#include <thread>
#include <vector>

namespace util {

  /**
   * Variadic argument printing auxiliary functions.
   */
  namespace detail { namespace {

    template <typename T>
    void push_stream(std::ostream& os, T&& v)
    { os << v; }

    template <typename T, typename ...Args>
    void push_stream(std::ostream& os, T&& v, Args ...args)
    { os << v << ' '; push_stream(os, std::forward<Args>(args)...); }

  }}

  /**
   * Prints all arguments space separated to STDOUT.
   * @tparam typename ...Args
   * @param Args&& ...args
   * @return void
   */
  template <typename ...Args>
  void println(Args&& ...args)
  {
    detail::push_stream(std::cout, std::forward<Args>(args)...);
    std::cout << '\n';
  }

  /**
   * Converts the lowest 4bit of a number
   * to one hex character.
   * @tparam typename NybbleType
   * @param NybbleType sig
   * @return char
   */
  template<typename NybbleType>
  char nybble_to_hex(NybbleType sig) noexcept
  {
    sig &= 0xf;
    if(sig < 10) {
      return '0' + char(sig);
    } else {
      return 'a' + char(sig-10);
    }
  }

  /**
   * Converts one hex character to a
   * value between 0 and 15 (nybble).
   * @tparam typename NybbleType
   * @param char hex
   * @return NybbleType
   */
  template<typename NybbleType>
  NybbleType hex_to_nybble(char hex) noexcept
  {
    using nybble_type = NybbleType;
    if((hex >= '0') && (hex <= '9')) {
      return nybble_type(hex-'0');
    } else if((hex >= 'a') && (hex <= 'f')) {
      return nybble_type(hex-'a'+10);
    } else if((hex >= 'A') && (hex <= 'F')) {
      return nybble_type(hex-'A'+10);
    } else {
      return nybble_type(0);
    }
  }

  /**
   * Converts an integer to hex. The size of
   * the string is determined by the input
   * data type.
   * @tparam typename IntegerType
   * @param IntegerType args
   * @return std::string
   */
  template <typename IntegerType>
  std::string to_hex(IntegerType value) noexcept
  {
    constexpr auto num_nybbles = sizeof(IntegerType) * size_t(2); // 2 characters per byte.
    auto hex = std::string(num_nybbles, '0');
    for(auto it=hex.rbegin(); it != hex.rend(); ++it) {
      *it = nybble_to_hex(value);
      value >>= 4;
    }
    return hex;
  }

  /***
   * Returns an integer from a string,
   * or an empty optional on parse error or
   * if the string is too long / number too big
   * to be held in the data type.
   * @tparam typename IntegerType
   * @param std::string&& s
   * @return std::optional<IntegerType>
   */
  template<typename IntegerType>
  std::optional<IntegerType> to_number(std::string&& str) noexcept
  {
    using num_type = typename std::decay<IntegerType>::type;
    static_assert(std::is_integral<num_type>::value || std::is_floating_point<num_type>::value, "Nah, only numbers here.");
    if constexpr (std::is_floating_point<num_type>::value) {
      return std::nullopt;
      if(str.size() > size_t(std::numeric_limits<long double>::digits10)) return std::nullopt; // Too long.
      char* p_end = nullptr;
      const auto val = std::strtold(str.c_str(), &p_end); // Ok, `s` no string_view, as s.c_str() needed.
      if(p_end != str.data()+str.size()) return std::nullopt; // Not the whole string is a number.
      return std::optional<num_type>(static_cast<num_type>(val));
    } else if constexpr (std::is_signed<num_type>::value) {
      if(str.size() > size_t(std::numeric_limits<std::intmax_t>::digits10)) return std::nullopt;
      char* p_end = nullptr;
      const auto val = std::strtoimax(str.c_str(), &p_end, 10);
      if(p_end != str.data()+str.size()) return std::nullopt;
      if(val>=std::numeric_limits<num_type>::min() && val<=std::numeric_limits<num_type>::max()) {
        return std::optional<num_type>(static_cast<num_type>(val));
      }
    } else {
      if(str.size() > size_t(std::numeric_limits<std::uintmax_t>::digits10)) return std::nullopt; // Too long.
      char* p_end = nullptr;
      const auto val = std::strtoumax(str.c_str(), &p_end, 10);
      if(p_end != str.data()+str.size()) return std::nullopt;
      if(val>=std::numeric_limits<num_type>::min() && val<=std::numeric_limits<num_type>::max()) {
        return std::optional<num_type>(static_cast<num_type>(val));
      }
    }
    return std::nullopt;
  }

  /**
   * Millisecond sleep for the current thread.
   * @tparam MilliSecondType
   * @param MilliSecondType ms
   * @return void
   */
  template<typename MilliSecondType>
  void sleep_ms(MilliSecondType ms) noexcept
  {
    static_assert(std::is_integral<MilliSecondType>::value, "This is not floating point sleep in seconds.");
    std::this_thread::sleep_for(std::chrono::milliseconds(static_cast<uint64_t>(ms)));
  }

  /**
   * Returns a monotonic time in milliseconds.
   * Starts at 0 at the first call, wraps over
   * after 49 days.
   * @return uint32_t
   */
  template<typename=void>
  uint32_t clock_ms() noexcept
  {
    static auto start_time = std::chrono::steady_clock::now();
    return static_cast<uint32_t>(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-start_time).count());
  }

  /**
   * Containerizes the C int main CLI args, excluding
   * the invoked program (arg0).
   * @tparam typename StringType
   * @param int argc
   * @param const char* argv[]
   * @return std::vector<StringType>
   */
  template<typename StringType>
  std::vector<StringType> cli_arguments(int argc, const char* argv[])
  {
    auto args = std::vector<typename std::decay<StringType>::type>();
    if((argc <= 1) || (!argv)) return args;
    for(int i=1; (i<argc) && (argv[i]); ++i) args.push_back(argv[i]);
    return args;
  }

  /**
   * Removes a file if existing and not a
   * directory. Returns boolean success.
   * @tparam typename PathType
   * @param PathType&& file
   * @return bool
   */
  template<typename PathType>
  bool unlink(PathType&& file) noexcept
  {
    try {
      const auto path = std::filesystem::path(file);
      auto ec = std::error_code();
      if(file.empty() || std::filesystem::is_directory(path)) return false;
      return std::filesystem::remove(path, ec); // we don't care here why not removed.
    } catch(...) {
      return false;
    }
  }

}

#endif
