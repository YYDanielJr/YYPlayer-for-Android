// Tiny flat JSON helpers for the control bridge.
//
// The bridge carries control data only (policy requests and snapshots); PCM never travels through
// it. This reader supports exactly the subset the ABI documents: objects, strings, numbers,
// booleans and arrays of objects, addressed by dotted member paths such as "route.deviceId". It
// resolves one member per path step, refuses malformed input instead of guessing, and never builds
// a DOM or allocates per lookup.
#ifndef YY_CORE_JSON_H
#define YY_CORE_JSON_H

#include <cstdint>
#include <string>
#include <string_view>

namespace yy::core::json {

// Reads an integer member at `path`. Returns false when the member is missing, is not an integer,
// or falls outside [min, max]; callers must then treat the value as unknown.
bool TryGetInt(std::string_view object, std::string_view path, std::int64_t min, std::int64_t max,
               std::int64_t* out);

bool TryGetBool(std::string_view object, std::string_view path, bool* out);

// Reads a string member. The value is copied verbatim; escape sequences are not decoded because
// this bridge only transports ASCII identifiers.
bool TryGetString(std::string_view object, std::string_view path, std::string* out);

// Copies the body of an object member (without its outer braces) into `*out`.
bool TryGetObject(std::string_view object, std::string_view path, std::string* out,
                  std::string* context = nullptr);

// Copies an array member and counts its top-level elements. An empty array is valid.
bool TryGetObjectArray(std::string_view object, std::string_view path, std::string* out,
                       std::int32_t* element_count);

// Appends helpers used to build the response document.
void AppendKey(std::string* out, const char* key);
void AppendString(std::string* out, const std::string& value);
void AppendInt(std::string* out, std::int64_t value);
void AppendBool(std::string* out, bool value);

}  // namespace yy::core::json

#endif  // YY_CORE_JSON_H
