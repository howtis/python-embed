#!/usr/bin/env python3
"""PythonEmbed bridge: Persistent REPL that communicates via MessagePack (binary) over stdin/stdout.

Binary protocol (4-byte big-endian length prefix, then MessagePack bytes):
  Input:  {"id": <int>, "type": "eval"|"exec"|"exit", "code": "<...>"}
  Output: {"id": <int>, "type": "result", "value": <serializable>}
          {"id": <int>, "type": "error", "message": "<error traceback>"}

Additional command types:
  "ref"     — {"type":"ref","name":"<var>"} → {"type":"result","value":<ref_id>}
  "release" — {"type":"release","ref_id":<int>} → {"type":"result","value":null}
  "call"    — {"type":"call","ref_id":<int>,"method":"<name>","args":[...]}
  "getattr" — {"type":"getattr","ref_id":<int>,"name":"<attr>"}
  "ping"    — {"type":"ping"} → {"type":"result","value":"pong"}
  "stream"  — {"type":"stream","code":"<generator expression>"}
              → multiple {"type":"stream_item","value":...} + {"type":"stream_end"}

  "batch"   — {"type":"batch","items":[{"id":<int>,"type":"eval"|"exec","code":"<...>"},...]}
              → one response frame per item: {"id":<int>,"type":"result","value":...}
              or {"id":<int>,"type":"error","message":"..."}

The process stays alive until it receives an EOF on stdin or an exit command.

**SECURITY WARNING — Namespace Sandbox**: This bridge does **not** restrict
``__builtins__`` in the eval/exec namespace. Python code sent via eval/exec has
full access to ``__import__()``, ``open()``, ``os``, ``shutil``, and all other
built-in functions. This means **arbitrary code execution is possible**,
including file system access, network access, and subprocess spawning.

Only pass trusted code to eval/exec. If user-controlled input reaches
eval/exec, it must be sanitized — for example via ``PythonEmbed.arg()`` on the
Java side for parameter substitution into literal strings.
"""

import sys
import msgpack
import traceback
import struct
import os
import logging
import gc

try:
    import resource as _resource
except ImportError:
    _resource = None

# Maximum code length to prevent DoS via huge payloads
MAX_CODE_LENGTH = 100_000


def parse_max_code_length(args):
    """Parse --max-code-length from CLI args."""
    global MAX_CODE_LENGTH
    for i, arg in enumerate(args):
        if arg == '--max-code-length' and i + 1 < len(args):
            try:
                MAX_CODE_LENGTH = int(args[i + 1])
            except ValueError:
                pass


def validate_code(code):
    """Validate code before execution. Returns (is_valid, error_message)."""
    if len(code) > MAX_CODE_LENGTH:
        return False, f"Code exceeds maximum length of {MAX_CODE_LENGTH} characters"
    return True, None


# ---- Object reference storage ----
_refs = {}
_next_ref_id = 0

# ---- Callback infrastructure ----
_callback_id = 0


class _ProtocolLogHandler(logging.Handler):
    """Logging handler that forwards Python log records to Java via protocol frames.

    Each log record is sent as a type:"log" push frame over stdout.
    The Java side routes these to SLF4J loggers.

    Errors in this handler are written to stderr to avoid infinite recursion.
    """

    def emit(self, record):
        try:
            msg = {
                "id": 0,
                "type": "log",
                "level": record.levelname,
                "logger": record.name,
                "message": self.format(record),
            }
            write_frame(sys.stdout.buffer, msg)
        except Exception:
            # Fallback to stderr to avoid infinite recursion
            import traceback as _tb
            _tb.print_exc(file=sys.stderr)


class _Bridge:
    """Bridge object injected into Python namespace for calling back to Java.

    Usage from Python code:
        _bridge.call('handler_name', arg1, arg2)  # synchronous call with return value
        _bridge.push('handler_name', value)        # fire-and-forget push
    """

    def __init__(self):
        pass

    def call(self, name, *args):
        """Call a Java-registered handler synchronously and return its result.

        Raises RuntimeError if the handler throws an exception or is not found.
        """
        global _callback_id
        _callback_id += 1
        cid = _callback_id
        msg = {
            "id": cid,
            "type": "callback_request",
            "name": name,
            "args": list(args),
        }

        write_frame(sys.stdout.buffer, msg)
        while True:
            response = read_frame(sys.stdin.buffer)
            if response is None:
                raise RuntimeError("Bridge connection closed")
            if response.get("id") == cid:
                if response.get("type") == "callback_result":
                    return response.get("value")
                elif response.get("type") == "callback_error":
                    raise RuntimeError(response.get("message", "Callback error"))

    def push(self, name, value):
        """Push a value to a Java-registered push handler (fire-and-forget).

        Does not wait for a response.
        """
        global _callback_id
        _callback_id += 1
        cid = _callback_id
        msg = {
            "id": cid,
            "type": "push",
            "name": name,
            "value": value,
        }

        write_frame(sys.stdout.buffer, msg)


def handle_ref(request):
    """Store a variable in _refs and return its ref_id."""
    global _next_ref_id
    name = request.get("name", "")
    if not name:
        raise ValueError("'name' is required for ref command")
    obj = eval(name, namespace)
    _next_ref_id += 1
    ref_id = _next_ref_id
    _refs[ref_id] = obj
    return {"ref_id": ref_id, "type": type(obj).__name__}


def handle_release(request):
    """Release a stored object reference."""
    ref_id = request.get("ref_id")
    if ref_id is not None:
        _refs.pop(ref_id, None)
    return None


def handle_call(request):
    """Call a method on a stored object reference."""
    ref_id = request.get("ref_id")
    method = request.get("method", "")
    args = request.get("args", [])
    obj = _refs.get(ref_id)
    if obj is None:
        raise ValueError(f"Invalid ref_id: {ref_id}")
    fn = getattr(obj, method)
    return fn(*args)


def handle_health():
    """Collect health metrics from the Python process."""
    if _resource is not None:
        try:
            usage = _resource.getrusage(_resource.RUSAGE_SELF)
            memory_rss_kb = usage.ru_maxrss
        except Exception:
            memory_rss_kb = 0
    else:
        memory_rss_kb = 0
    return {
        "memory_rss_kb": memory_rss_kb,
        "ref_count": len(_refs),
        "gc_enabled": gc.isenabled(),
        "gc_counts": list(gc.get_count()),
    }


def handle_getattr(request):
    """Get an attribute from a stored object reference."""
    ref_id = request.get("ref_id")
    name = request.get("name", "")
    obj = _refs.get(ref_id)
    if obj is None:
        raise ValueError(f"Invalid ref_id: {ref_id}")
    return getattr(obj, name)


# ---- Main entry point ----

def main():
    global namespace

    # Parse CLI args
    parse_max_code_length(sys.argv[1:])

    run_binary_loop()


def run_binary_loop():
    """MessagePack binary protocol loop."""
    global namespace

    # WARNING: The namespace does not restrict __builtins__. eval/exec can
    # access __import__(), open(), and all built-in functions.
    # Only pass trusted code. For parameter substitution, use PythonEmbed.arg()
    # on the Java side to escape values safely.
    namespace = {'_bridge': _Bridge()}

    # Install logging handler to forward Python logs to Java via protocol frames
    _log_handler = _ProtocolLogHandler()
    logging.root.addHandler(_log_handler)
    logging.root.setLevel(logging.INFO)

    stdin_buf = sys.stdin.buffer
    stdout_buf = sys.stdout.buffer

    while True:
        try:
            request = read_frame(stdin_buf)
            if request is None:
                break
        except Exception as e:
            write_error_msgpack(None, f"Frame read error: {e}")
            continue

        if request.get("type") == "stream":
            handle_stream_binary(request, stdout_buf)
            continue

        success, response = handle_request(request)
        if response is not None:
            write_frame(stdout_buf, response)
        if not success:
            break

    sys.exit(0)


def handle_stream_binary(request, stdout_buf):
    """Handle a stream request in binary mode."""
    req_id = request.get("id")
    code = request.get("code", "")
    try:
        is_valid, err_msg = validate_code(code)
        if not is_valid:
            write_frame(stdout_buf, make_error(req_id, err_msg))
            return
        result = eval(code, namespace)
        if _is_iterable(result):
            for item in result:
                write_frame(stdout_buf, {
                    "id": req_id, "type": "stream_item", "value": _encode_value(item)
                })
        else:
            write_frame(stdout_buf, {
                "id": req_id, "type": "stream_item", "value": _encode_value(result)
            })
        write_frame(stdout_buf, {"id": req_id, "type": "stream_end"})
    except Exception as e:
        write_frame(stdout_buf, make_error(req_id, *format_error(e)))


def handle_batch(request):
    """Process a batch of requests, writing each response to stdout.
    Returns (continue_loop, None) — batch responses are written inline."""
    stdout_buf = sys.stdout.buffer
    items = request.get("items", [])
    for item in items:
        if not isinstance(item, dict):
            write_frame(stdout_buf, make_error(None, f"Invalid batch item: {item}"))
            continue
        item_id = item.get("id")
        item_type = item.get("type", "")
        code = item.get("code", "")

        is_valid, err_msg = validate_code(code)
        if not is_valid:
            write_frame(stdout_buf, make_error(item_id, err_msg))
            continue

        if item_type == "eval":
            try:
                result = eval(code, namespace)
                write_frame(stdout_buf, make_result(item_id, result))
            except Exception as e:
                write_frame(stdout_buf, make_error(item_id, *format_error(e)))

        elif item_type == "exec":
            try:
                exec(code, namespace)
                write_frame(stdout_buf, make_result(item_id, None))
            except Exception as e:
                write_frame(stdout_buf, make_error(item_id, *format_error(e)))

        else:
            write_frame(stdout_buf, make_error(item_id, f"Unknown batch item type: {item_type}"))

    return True, None


def _is_iterable(obj):
    """Check if obj is iterable but not a string or bytes."""
    return hasattr(obj, '__iter__') and not isinstance(obj, (str, bytes))


def handle_request(request):
    """Process a single request. Returns (continue_loop, response_dict)."""
    try:
        req_id = request.get("id")
        req_type = request.get("type", "")
        code = request.get("code", "")

        # Validate code length
        is_valid, err_msg = validate_code(code)
        if not is_valid:
            return True, make_error(req_id, err_msg)

        if req_type == "exit":
            return False, make_result(req_id, None)

        if req_type == "eval":
            try:
                result = eval(code, namespace)
                return True, make_result(req_id, result)
            except Exception as e:
                return True, make_error(req_id, *format_error(e))

        elif req_type == "exec":
            try:
                exec(code, namespace)
                return True, make_result(req_id, None)
            except Exception as e:
                return True, make_error(req_id, *format_error(e))

        elif req_type == "ref":
            try:
                result = handle_ref(request)
                return True, make_result(req_id, result)
            except Exception as e:
                return True, make_error(req_id, *format_error(e))

        elif req_type == "release":
            try:
                handle_release(request)
                return True, make_result(req_id, None)
            except Exception as e:
                return True, make_error(req_id, *format_error(e))

        elif req_type == "call":
            try:
                result = handle_call(request)
                return True, make_result(req_id, result)
            except Exception as e:
                return True, make_error(req_id, *format_error(e))

        elif req_type == "getattr":
            try:
                result = handle_getattr(request)
                return True, make_result(req_id, result)
            except Exception as e:
                return True, make_error(req_id, *format_error(e))

        elif req_type == "ping":
            return True, make_result(req_id, "pong")

        elif req_type == "health":
            try:
                result = handle_health()
                return True, make_result(req_id, result)
            except Exception as e:
                return True, make_error(req_id, *format_error(e))

        elif req_type == "stream":
            return True, make_error(req_id, "stream must be handled by the calling loop")

        elif req_type == "batch":
            return handle_batch(request)

        else:
            return True, make_error(req_id, f"Unknown request type: {req_type}")

    except Exception as e:
        return True, make_error(request.get("id") if isinstance(request, dict) else None,
                                f"Handler error: {e}")


# ---- MessagePack framing ----

def read_frame(stream):
    """Read a length-prefixed MessagePack frame from a binary stream.
    Returns the unpacked dict, or None on EOF."""
    # Read 4-byte big-endian length
    length_bytes = stream.read(4)
    if not length_bytes:
        return None
    if len(length_bytes) < 4:
        raise EOFError(f"Incomplete frame header: expected 4 bytes, got {len(length_bytes)}")

    length = struct.unpack('>I', length_bytes)[0]

    if length == 0:
        return None
    if length > MAX_CODE_LENGTH * 10:
        raise ValueError(f"Frame too large: {length} bytes")

    # Read exact number of bytes
    data = stream.read(length)
    if len(data) < length:
        raise EOFError(f"Incomplete frame: expected {length} bytes, got {len(data)}")

    return msgpack.unpackb(data, raw=False)


def write_frame(stream, obj):
    """Write a dict as a length-prefixed MessagePack frame to a binary stream."""
    data = msgpack.packb(obj, default=str)
    length = len(data)
    stream.write(struct.pack('>I', length))
    stream.write(data)
    stream.flush()


def default_encoder(obj):
    """Fallback encoder for non-MessagePack-serializable objects."""
    return str(obj)


# ---- Value encoding ----

def _encode_value(obj):
    """Encode dict/list with extension type hints."""
    if isinstance(obj, dict):
        return msgpack.ExtType(1, msgpack.packb(obj))
    elif isinstance(obj, list):
        return msgpack.ExtType(2, msgpack.packb(obj))
    return obj


# ---- Response builders ----

def make_result(req_id, value):
    return {
        "id": req_id,
        "type": "result",
        "value": _encode_value(value),
    }


def make_error(req_id, message, error_type=None):
    result = {
        "id": req_id,
        "type": "error",
        "message": message,
    }
    if error_type is not None:
        result["error_type"] = error_type
    return result


def write_error_msgpack(req_id, message):
    """Write an error response in binary mode."""
    write_frame(sys.stdout.buffer, make_error(req_id, message))


def format_error(e):
    """Format an exception returning (traceback, error_type)."""
    return traceback.format_exc().strip(), type(e).__name__


if __name__ == "__main__":
    namespace = {}
    try:
        main()
    except SystemExit:
        pass
