"""Strict result parsing: process success alone is not Android test success."""
import re

def accepted(returncode, output, expected=None):
    codes = re.findall(r"^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$", output, re.M)
    if returncode != 0 or codes != ["0"]:
        return False
    flags = re.findall(r"^INSTRUMENTATION_STATUS:\s*stream=([^\r\n]*)", output, re.M)
    if any(re.search(r"=(?:FAIL|PENDING)(?:_|$|\s)", flag) for flag in flags):
        return False
    if re.search(r"^INSTRUMENTATION_RESULT:\s*summary=FAIL\b", output, re.M):
        return False
    return expected is None or expected in flags
