#include "yy/core/json.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace yy::core::json {
namespace {

constexpr std::size_t kNotFound = static_cast<std::size_t>(-1);

bool IsSpace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
}

struct Span {
    std::size_t begin = kNotFound;
    std::size_t end = kNotFound;
    bool found() const { return begin != kNotFound; }
    std::size_t size() const { return end - begin; }
};

// One past the last character of the value starting at `begin` inside `text`.
std::size_t ValueEnd(std::string_view text, std::size_t begin) {
    const char first = text[begin];
    if (first == '{' || first == '[') {
        const char close = first == '{' ? '}' : ']';
        std::size_t depth = 0;
        bool in_string = false;
        bool escaped = false;
        for (std::size_t i = begin; i < text.size(); ++i) {
            const char c = text[i];
            if (in_string) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    in_string = false;
                }
                continue;
            }
            if (c == '"') {
                in_string = true;
            } else if (c == first) {
                ++depth;
            } else if (c == close) {
                --depth;
                if (depth == 0) return i + 1;
            }
        }
        return text.size();
    }
    if (first == '"') {
        std::size_t i = begin + 1;
        while (i < text.size()) {
            if (text[i] == '\\') {
                i += 2;
                continue;
            }
            if (text[i] == '"') return i + 1;
            ++i;
        }
        return text.size();
    }
    std::size_t i = begin;
    while (i < text.size()) {
        const char c = text[i];
        if (c == ',' || c == '}' || c == ']') break;
        ++i;
    }
    while (i > begin && IsSpace(text[i - 1])) --i;
    return i;
}

// Resolves one member of an object body (the text between its braces).
//
// Resolves one member of an object body (the text between its braces, or a whole bare body).
//
// Scanning walks member by member at this level only. After a name it consumes the whole value, so
// a nested object can never expose one of its own members here: a member that only exists deeper in
// the tree has to be addressed through its path. A string is a value when it follows a colon and a
// name when it follows a comma, which is why {"label":"sampleRate"} cannot match a member named
// sampleRate.
Span FindMember(std::string_view text, std::size_t body_begin, std::size_t body_end,
                std::string_view name) {
    bool in_string = false;
    bool escaped = false;
    char last_significant = ',';
    for (std::size_t i = body_begin; i < body_end; ++i) {
        const char c = text[i];
        if (in_string) {
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                in_string = false;
            }
            continue;
        }
        if (c == '"') {
            const std::size_t open_quote = i;
            const std::size_t close_quote = text.find('"', open_quote + 1);
            if (close_quote == std::string_view::npos) return Span{};
            const bool member_position = last_significant == ',';
            std::size_t after = close_quote + 1;
            while (after < text.size() && IsSpace(text[after])) ++after;
            const bool has_colon = after < text.size() && text[after] == ':';
            if (member_position && has_colon) {
                const std::size_t name_len = close_quote - open_quote - 1;
                if (name_len == name.size() && text.compare(open_quote + 1, name_len, name) == 0) {
                    std::size_t begin = after + 1;
                    while (begin < text.size() && IsSpace(text[begin])) ++begin;
                    if (begin >= text.size()) return Span{};
                    return Span{begin, ValueEnd(text, begin)};
                }
                // A different member: consume its whole value so its contents stay invisible.
                std::size_t value_begin = after + 1;
                while (value_begin < text.size() && IsSpace(text[value_begin])) ++value_begin;
                if (value_begin >= text.size()) return Span{};
                const std::size_t value_end = ValueEnd(text, value_begin);
                if (value_end <= value_begin) return Span{};
                i = value_end - 1;
                last_significant = 'v';
                continue;
            }
            i = close_quote;
            last_significant = '"';
            continue;
        }
        if (!IsSpace(c)) last_significant = c;
    }
    return Span{};
}

// Body bounds of a container span: between the braces for an object, the whole span otherwise.
Span BodyOf(std::string_view text, Span container) {
    if (container.found() && container.begin < text.size() && text[container.begin] == '{' &&
        container.size() >= 2) {
        return Span{container.begin + 1, container.end - 1};
    }
    return container;
}

// Walks a dotted path. `container` is an object span including its braces, or a bare object body.
Span Resolve(std::string_view text, Span container, std::string_view path) {
    std::size_t step = 0;
    while (step <= path.size()) {
        std::size_t dot = path.find('.', step);
        const bool last = dot == std::string_view::npos;
        if (last) dot = path.size();
        const std::string_view name = path.substr(step, dot - step);
        if (name.empty() || !container.found()) return Span{};
        if (container.size() < 2) return Span{};
        const Span body = BodyOf(text, container);
        if (!body.found() || body.end <= body.begin) return Span{};
        const Span member = FindMember(text, body.begin, body.end, name);
        if (!member.found()) return Span{};
        if (last) return member;
        container = member;
        step = dot + 1;
    }
    return Span{};
}

// Accepts either a whole JSON document or a bare object body, so a caller can pass the text that
// TryGetObject/TryGetObjectArray handed back and keep resolving members from it. A document that
// starts with '{' is resolved strictly as that object: a member that only exists deeper in the tree
// must be addressed with its path instead of being found by this fallback.
Span ResolvePath(std::string_view document, std::string_view path) {
    std::size_t begin = 0;
    while (begin < document.size() && IsSpace(document[begin])) ++begin;
    if (begin >= document.size()) return Span{};
    if (document[begin] == '{') {
        const std::size_t end = ValueEnd(document, begin);
        if (end >= 2 && document[end - 1] == '}') {
            return Resolve(document, Span{begin, end}, path);
        }
    }
    // Bare body: the caller already stripped the braces, or a fragment was handed over directly.
    return Resolve(document, Span{begin, document.size()}, path);
}

// Copies a container value (an object or an array) without its outer delimiters.
bool CopyContainer(std::string_view document, const Span& span, char open, char close,
                   std::string* out) {
    if (!span.found() || span.begin >= document.size() || document[span.begin] != open) return false;
    if (span.end < 2 || document[span.end - 1] != close) return false;
    out->assign(document.substr(span.begin + 1, span.end - span.begin - 2));
    return true;
}

}  // namespace

bool TryGetInt(std::string_view object, std::string_view path, std::int64_t min, std::int64_t max,
               std::int64_t* out) {
    const Span span = ResolvePath(object, path);
    if (!span.found() || span.size() == 0) return false;
    const char* begin = object.data() + span.begin;
    char* end = nullptr;
    const long long parsed = std::strtoll(begin, &end, 10);
    if (end == begin) return false;
    if (parsed < min || parsed > max) return false;
    *out = static_cast<std::int64_t>(parsed);
    return true;
}

bool TryGetBool(std::string_view object, std::string_view path, bool* out) {
    const Span span = ResolvePath(object, path);
    if (!span.found()) return false;
    const std::string_view value = object.substr(span.begin, span.size());
    if (value == "true") {
        *out = true;
        return true;
    }
    if (value == "false") {
        *out = false;
        return true;
    }
    return false;
}

bool TryGetString(std::string_view object, std::string_view path, std::string* out) {
    const Span span = ResolvePath(object, path);
    if (!span.found() || span.size() < 2 || object[span.begin] != '"') return false;
    if (object[span.end - 1] != '"') return false;
    out->assign(object.substr(span.begin + 1, span.size() - 2));
    return true;
}

bool TryGetObject(std::string_view object, std::string_view path, std::string* out,
                  std::string* context) {
    const Span span = ResolvePath(object, path);
    if (!CopyContainer(object, span, '{', '}', out)) return false;
    if (context != nullptr) {
        if (span.begin >= object.size() || object[span.begin] != '{') return false;
        context->assign(object.substr(span.begin, span.size()));
    }
    return true;
}

bool TryGetObjectArray(std::string_view object, std::string_view path, std::string* out,
                       std::int32_t* element_count) {
    *element_count = 0;
    const Span span = ResolvePath(object, path);
    if (!CopyContainer(object, span, '[', ']', out)) return false;
    // Count top-level elements; each element of this schema must be an object.
    std::size_t depth = 0;
    bool in_string = false;
    bool escaped = false;
    bool in_element = false;
    for (char c : *out) {
        if (in_string) {
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                in_string = false;
            }
            continue;
        }
        if (c == '"') {
            in_string = true;
        } else if (c == '{') {
            if (depth == 0) in_element = true;
            ++depth;
        } else if (c == '}') {
            if (depth > 0) --depth;
            if (depth == 0 && in_element) {
                ++(*element_count);
                in_element = false;
            }
        }
    }
    return true;
}

void AppendKey(std::string* out, const char* key) {
    out->push_back('"');
    out->append(key);
    out->append("\":");
}

void AppendString(std::string* out, const std::string& value) {
    out->push_back('"');
    for (char c : value) {
        if (c == '"' || c == '\\') {
            out->push_back('\\');
            out->push_back(c);
        } else if (static_cast<unsigned char>(c) < 0x20) {
            char buffer[8];
            std::snprintf(buffer, sizeof(buffer), "\\u%04X", static_cast<unsigned>(c));
            out->append(buffer);
        } else {
            out->push_back(c);
        }
    }
    out->push_back('"');
}

void AppendInt(std::string* out, std::int64_t value) {
    char buffer[32];
    std::snprintf(buffer, sizeof(buffer), "%lld", static_cast<long long>(value));
    out->append(buffer);
}

void AppendBool(std::string* out, bool value) {
    out->append(value ? "true" : "false");
}

}  // namespace yy::core::json
