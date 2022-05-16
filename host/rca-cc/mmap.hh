/**
 * @file mmap.hh
 * @author Stefan Wilhelm
 * @license MIT
 * @std c++17
 *
 * File Memory Mapping class template.
 */
#ifndef SW_MMAP_HH
#define SW_MMAP_HH

#if defined(__linux__) || defined(__linux)
  #ifndef OS_LINUX
    #define OS_LINUX
  #endif
  #include <sys/mman.h>
  #include <sys/stat.h>
  #include <unistd.h>
  #include <fcntl.h>
  #include <errno.h>
#elif defined(WIN32) || defined(__MINGW32__) || defined(__MINGW64__) || defined(_MSCVER)
  #include <windows.h>
#else
  #error "Unsupported OS."
#endif
#include <type_traits>
#include <limits>
#include <cstdint>


namespace sw { namespace ipc {

  template<typename ValueType, typename StringType, typename PathType=StringType>
  class memory_mapped_file
  {
  public:

    using path_type = std::decay_t<PathType>;
    using value_type = std::decay_t<ValueType>;
    using string_type = StringType;
    using size_type = std::size_t;
    using offset_type = size_type;
    using index_type = size_type;
    using error_message_type = string_type;

    typedef uint32_t flags_type;
    static constexpr auto flag_nocreate     = flags_type(0x01u);  // File must exist already.
    static constexpr auto flag_readwrite    = flags_type(0x02u);  // Open/map read-write for this process.
    static constexpr auto flag_shared       = flags_type(0x04u);  // Other processes may open/map the same file.
    static constexpr auto flag_protected    = flags_type(0x08u);  // Other processes may read but not write.

    #ifdef OS_LINUX
      using error_type = int;
      using descriptor_type = int;
      static constexpr descriptor_type invalid_descriptor() { return descriptor_type(-1); }
    #else
      using error_type = DWORD;
      using descriptor_type = HANDLE;
      static descriptor_type invalid_descriptor() { return reinterpret_cast<descriptor_type>(INVALID_HANDLE_VALUE); } // pointer, can't be constexpr
    #endif

    static constexpr size_type max_byte_size()  { return 128ul<<20u; }

  public:

    explicit memory_mapped_file() noexcept
      : path_(), handles_(), adr_(nullptr), size_(), offset_(), error_()
      {
        using namespace std;
        static_assert(
          (is_arithmetic_v<value_type> ||  is_trivial_v<value_type>) && (!is_pointer_v<value_type>)
          , "Incompatible value type (must be auto memory)"
        );
      }

    memory_mapped_file(const memory_mapped_file&) = delete;
    memory_mapped_file(memory_mapped_file&&) noexcept = default;
    memory_mapped_file& operator=(const memory_mapped_file&) = delete;
    memory_mapped_file& operator=(memory_mapped_file&&) noexcept = default;
    ~memory_mapped_file() noexcept { close(); }

  public:

    inline const path_type& path() const noexcept
    { return path_; }

    inline size_type size() const noexcept
    { return size_; }

    inline offset_type offset() const noexcept
    { return offset_; }

    inline flags_type flags() const noexcept
    { return flags_; }

    inline bool closed() const noexcept
    { return (handles_.fd == invalid_descriptor()); }

    inline error_type error() const noexcept
    { return error_; }

    inline error_message_type error_message() const noexcept
    {
      if(!error_) return error_message_type();
      #ifdef OS_LINUX
        return error_message_type(::strerror(error_));
      #else
        error_message_type s(256, 0);
        const size_t n = ::FormatMessageA(FORMAT_MESSAGE_FROM_SYSTEM, nullptr, error_, MAKELANGID(LANG_ENGLISH, SUBLANG_DEFAULT), &s[0], s.size()-1, nullptr);
        if(!n) return error_message_type();
        s.resize(n);
        return s;
      #endif
    }

  public:

    inline void close() noexcept
    {
      const auto size = size_;
      size_ = 0;
      error_ = 0;
      if(closed()) return;
      #ifdef OS_LINUX
        if(adr_) ::munmap(adr_, size);
        if(handles_.fd != invalid_descriptor()) ::close(handles_.fd);
      #else
        if(handles_.fm) ::CloseHandle(handles_.fm);
        if(handles_.fd != invalid_descriptor()) ::CloseHandle(handles_.fd);
        if(adr_) ::UnmapViewOfFile(adr_);
        handles_.fm = nullptr;
        (void)size;
      #endif
      handles_.fd = invalid_descriptor();
      offset_ = 0;
      adr_ = nullptr;
    }

    inline bool open(const path_type& file_path, const flags_type flags, const size_type num_value_elements, const offset_type element_offset) noexcept
    {
      using namespace std;
      static_assert(max_byte_size() < std::numeric_limits<uint32_t>::max(), "Large mappings untested.");
      close();
      path_ = file_path;
      flags_ = flags & (flag_nocreate|flag_readwrite|flag_shared|flag_protected);
      offset_ = element_offset;
      const auto file_size = size_type(element_offset * sizeof(value_type)) + (num_value_elements * sizeof(value_type));
      #ifdef OS_LINUX
      {
        if((file_size <= 0) || (file_size > max_byte_size())) {
          error_ = ERANGE;
          return false;
        } else {
          const auto failed = [this](){ const auto e=errno; close(); error_=e; return false; };
          // File open operation.
          {
            auto open_flags = int(O_CLOEXEC|O_NOCTTY|O_NONBLOCK);
            if(flags & flag_readwrite) {
              open_flags |= (O_RDWR) | ((flags & flag_nocreate) ? (0) : (O_CREAT));
            } else {
              open_flags |= (O_RDONLY);
            }
            auto file_mode = ::mode_t(S_IRUSR);
            if(flags & flag_readwrite) file_mode |= ::mode_t(S_IWUSR);
            if(flags & flag_shared) file_mode |= ::mode_t(S_IRGRP|S_IROTH);
            if(!(flags & flag_protected)) file_mode |= ::mode_t(S_IWGRP|S_IWOTH);
            if((handles_.fd=::open(file_path.c_str(), open_flags, file_mode)) < 0) return failed();
            struct ::stat st;
            if(::fstat(handles_.fd, &st) < 0) return failed();
            if(st.st_size < long(file_size) && (::ftruncate(handles_.fd, file_size)<0)) return failed();
          }
          // Memory mapping.
          {
            auto protection_flags = int(PROT_READ);
            auto map_flags = int(MAP_SHARED|MAP_POPULATE);
            if(flags & flag_readwrite) protection_flags |= PROT_WRITE;
            adr_ = ::mmap(nullptr, sizeof(value_type)*num_value_elements, protection_flags, map_flags, handles_.fd, sizeof(value_type)*element_offset);
            if(adr_ == nullptr) return failed();
          }
        }
      }
      #else
      {
        if((file_size <= 0) || (file_size > max_byte_size())) {
          error_ = ERROR_NOT_ENOUGH_MEMORY;
          return false;
        } else {
          // File open operation.
          const auto failed = [this](){ const auto e=::GetLastError(); close(); error_=e; return false; };
          {
            ::InitializeSecurityDescriptor(&handles_.sd, SECURITY_DESCRIPTOR_REVISION);
            ::SetSecurityDescriptorDacl(&handles_.sd, true, nullptr, false);
            handles_.sa.nLength = sizeof(handles_.sa);
            handles_.sa.lpSecurityDescriptor = &handles_.sd;
            handles_.sa.bInheritHandle = true;
            DWORD access_mode = GENERIC_READ;
            DWORD share_mode = FILE_SHARE_READ|FILE_SHARE_WRITE; // <-- No idea why FILE_SHARE_WRITE has to be always specified. Messes up the function of `flag_protected`.
            DWORD creation_disposition = OPEN_EXISTING;
            DWORD flags_attr = FILE_ATTRIBUTE_NORMAL;
            if(flags & flag_readwrite) {
              access_mode |= GENERIC_WRITE;
              if(!(flags & flag_protected)) share_mode |= FILE_SHARE_WRITE;
              if(!(flags & flag_nocreate)) creation_disposition = OPEN_ALWAYS;
            }
            handles_.fd = ::CreateFileA(file_path.c_str(), access_mode, share_mode, &handles_.sa, creation_disposition, flags_attr, nullptr);
            if(handles_.fd == invalid_descriptor()) return failed();
          }
          // File size adaption.
          {
            LARGE_INTEGER size_ull;
            if(::GetFileSizeEx(handles_.fd, &size_ull) && (size_t(size_ull.QuadPart) < file_size)) {
              auto n_left = file_size - size_type(size_ull.QuadPart);
              auto zeros = std::array<uint8_t, 4096>();
              zeros.fill(0);
              if(flags & flag_readwrite) {
                while(n_left > 0) {
                  DWORD n_written = 0;
                  const DWORD n_wr = std::min(n_left, zeros.max_size());
                  if(!::WriteFile(handles_.fd, zeros.data(), n_wr, &n_written, nullptr)) return failed();
                  n_left -= n_written;
                }
              } else {
                close();
                error_ = E_INVALIDARG;
                return false;
              }
            }
          }
          // Memory mapping
          {
            string name = file_path.c_str();
            name.erase(std::remove_if(name.begin(), name.end(), [](const char c){ return (c<0x20)||(c>0x7e)||(c=='\\')||(c=='/');}), name.end());
            name += to_string(long(element_offset*sizeof(value_type))) + "_" + to_string(long(num_value_elements*sizeof(value_type)));
            const DWORD protect = (flags & flag_readwrite) ? (PAGE_READWRITE) : (PAGE_READONLY);;
            const DWORD map_access = (flags & flag_readwrite) ? (FILE_MAP_ALL_ACCESS) : (FILE_MAP_READ);
            handles_.fm = ::CreateFileMappingA(handles_.fd, &handles_.sa, protect, 0, uint32_t(), name.c_str());
            if(handles_.fm == nullptr) return failed();
            adr_ = ::MapViewOfFileEx(handles_.fm, map_access, 0, DWORD(element_offset*sizeof(value_type)), DWORD(num_value_elements*sizeof(value_type)), nullptr);
            if(adr_ == nullptr) return failed();
          }
        }
      }
      #endif
      size_ = num_value_elements; // Setting the size very last prevents locking requirements, as get()/set() early return on size_==0.
      return true;
    }

    inline bool sync() noexcept
    {
      if(closed() || (!(flags_ & flag_readwrite)) || (!adr_) || (!size_)) return false;
      #ifdef OS_LINUX
        return (::msync(adr_, size_, MS_ASYNC)==0);
      #else
        ::SetFilePointer(handles_.fd, 0, nullptr, FILE_BEGIN);
        const uint8_t* data = reinterpret_cast<const uint8_t*>(adr_);
        size_t pos = 0;
        while(pos < size_) {
          DWORD n_written = 0;
          if(!::WriteFile(handles_.fd, &data[pos], (size_-pos), &n_written, nullptr) || (!n_written)) return false;
          pos += n_written;
        }
        return true;
      #endif
    }

    inline bool set(const index_type index, const value_type value) noexcept
    {
      if(index >= size() || (!adr_) || (!(flags_ & flag_readwrite))) return false;
      reinterpret_cast<value_type*>(adr_)[index] = value;
      return true;
    }

    inline value_type get(const index_type index, const value_type default_value) const noexcept
    { return (index >= size() || (!adr_)) ? default_value : reinterpret_cast<const value_type*>(adr_)[index]; }

    inline value_type get(const index_type index) const noexcept
    { return get(index, value_type()); }

  public:

    inline const descriptor_type& descriptor() const noexcept
    { return handles_.fd; }

  protected:

    using address_type = void*;

    struct handles_type {
      #ifdef OS_LINUX
      descriptor_type fd;
      handles_type() noexcept : fd(invalid_descriptor()) {}
      #else
      handles_type() noexcept : fd(invalid_descriptor()), fm(INVALID_HANDLE_VALUE), sa(), sd() {}
      descriptor_type fd;
      HANDLE fm;
      SECURITY_ATTRIBUTES sa;
      SECURITY_DESCRIPTOR sd;
      #endif
    };

  private:

    path_type path_;
    handles_type handles_;
    address_type adr_;
    size_type size_;
    offset_type offset_;
    flags_type flags_;
    error_type error_;
  };

}}

#endif
