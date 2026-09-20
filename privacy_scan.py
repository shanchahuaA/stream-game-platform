#!/usr/bin/env python3
"""Pre-push privacy scan.

Reports every file that a commit would contain and that looks like it carries a
credential or personal data, so a push can be stopped before anything becomes
public.

The same file is kept byte-for-byte identical in three repositories --
stream-game-platform, defense-system and shanchahuaA.github.io -- and in the
development tree it is edited in; a change to any copy is a change to all.

    python privacy_scan.py            # scan the current directory
    python privacy_scan.py <dir>      # scan somewhere else

Exit status: 0 clean, 1 hits found, 2 the scan could not run.

How the file list is chosen
    ``git ls-files --others --exclude-standard`` is run against a throw-away
    empty index, so the scan sees every file in the working tree that
    ``.gitignore`` does not exclude.  Nothing has to be committed first, and a
    repository does not even have to exist yet -- which is what lets this run
    before ``git init``.  Because the index is empty, a file that is both
    tracked *and* ignored is left out; that combination cannot arise in a fresh
    publish copy, and every such check errs towards scanning too much.

What is reported
    ``FILE:LINE: RULE`` on stdout, sorted, one line per hit.  The matching text
    is deliberately not echoed, so the report can be pasted anywhere.  Progress
    and the summary go to stderr, leaving stdout machine-readable.

Deliberately not reported
    * This file.  Its own text is kept scan-clean -- a test asserts it -- and
      skipping it means a future edit to the rule table cannot fail the scan
      against itself.
    * Binary files: a NUL byte in the first 8 KiB means the file is not text.
    * Placeholder values -- ``${DB_PASSWORD}``, ``changeme``, ``your_password``
      and friends -- so ``.example`` templates can stay in the repository.
    * ``localhost``/``127.0.0.1`` connection strings and RFC 2606 domains, which
      name no secret.

Known limitations
    Each of these narrows the pattern the ticket lists, on purpose:
    * Real names.  A surname rule fires on ordinary Chinese prose (紧张, 行李,
      张开), so names are not scanned; ticket 06 replaces them in the test SQL
      by hand instead.
    * XML attribute and element credentials
      (``<property name="password" value="..."/>``, ``<password>...</password>``).
    * ``jdbc:`` is recognised in its ``//`` form only, not the Oracle thin form
      (``jdbc:oracle:thin:user/pass@host:1521:sid``).
    * A drive path needs a path character after the separator, so a bare drive
      letter and separator quoted in prose is not a path.
    * ``-----BEGIN`` is reported for private keys, not certificates, which are
      public by definition.
"""

import re
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import List, NamedTuple, Optional

EXIT_CLEAN = 0
EXIT_HITS = 1
EXIT_ERROR = 2

BINARY_SNIFF_BYTES = 8192

# A bare number (optionally with a short unit) after a key is a quantity --
# a timeout, a size -- not a credential.  Quoted numbers are still reported.
_QUANTITY_RE = re.compile(r"^\d+[A-Za-z]{0,3}$")

# Characters that mean the value is a code expression, not a literal.  Only
# applied to unquoted values, and only in files that are configuration rather
# than source: `token: string` is a type annotation, `password = getPwd()` is
# a call, and neither is a secret.
_CODE_CHARS_RE = re.compile(r"[()\[\]{}<>,]")

_SENSITIVE_KEY = (
    r"[A-Za-z0-9_.-]*"
    r"(?:password|passwd|pwd|secret|token|api[ _-]?key|access[ _-]?key|private[ _-]?key)"
    r"[A-Za-z0-9_.-]*"
)
# A key in single quotes is a string literal inside an expression
# (`mode ? 'password' : 'text'`, `case 'password':`), not a configuration key:
# JSON, YAML and TOML spell their keys with double quotes.
_KEY_VALUE_RE = re.compile(
    r"(?i)(?<![A-Za-z0-9_.'-])(?P<key>" + _SENSITIVE_KEY + r")['\"]?"
    r"\s*(?::=|=>|:|=)\s*(?!=)(?P<value>.+)"
)

# `PRIVATE KEY` and `PRIVATE KEY BLOCK` (PGP) both count; `CERTIFICATE` and
# `PUBLIC KEY` are public material and are left alone.
_PRIVATE_KEY_RE = re.compile(r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY[A-Z ]*-----")

_CONNECTION_RE = re.compile(
    r"(?i)\b(?:jdbc:[a-z0-9]+|mysql|redis)://(?P<authority>[^\s/\"'<>)\]},]+)"
)
# Without a hostname behind the scheme the match is prose about schemes
# ("mysql:// / redis://"), not a connection string.
_HOSTNAME_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9.-]*$")
_LOCAL_HOSTS = frozenset(
    {"localhost", "127.0.0.1", "0.0.0.0", "::1", "host.docker.internal"}
)
_RESERVED_SUFFIXES = (".example", ".invalid", ".test", ".local")

_NATIONAL_ID_RE = re.compile(
    r"(?<!\d)"
    r"[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx]"
    r"(?!\d)"
)
_ID_WEIGHTS = (7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
_ID_CHECK_CODES = "10X98765432"

_PHONE_RE = re.compile(r"(?<!\d)(1[3-9]\d{9})(?!\d)")
_KNOWN_FAKE_PHONES = frozenset({"13800138000", "13700137000", "13000000000"})

_EMAIL_RE = re.compile(
    r"(?<![A-Za-z0-9._%+-])"
    r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}"
)
# GitHub's own privacy-preserving alias, published in every noreply commit.
# example/invalid/localhost/test domains are covered by _RESERVED_LABELS below.
_RESERVED_DOMAINS = frozenset({"users.noreply.github.com"})
_RESERVED_LABELS = frozenset({"example", "invalid", "localhost", "test"})
# Addresses that belong to a machine rather than a person: `git@github.com` in
# clone instructions, `noreply@` senders.
_NON_PERSONAL_LOCAL_PARTS = frozenset(
    {"donotreply", "git", "hg", "mailer-daemon", "no-reply", "noreply", "svn"}
)

_PATH_TAIL = r"[^\s`\"'<>|]"
_LOCAL_PATH_RE = re.compile(
    r"(?<![A-Za-z0-9/])[A-Za-z]:[\\/]" + _PATH_TAIL + r"|/Users/" + _PATH_TAIL
)

# Values that name no secret: self-describing placeholders, the words a
# developer types while the real value is still unknown, and the names of types
# in the languages used here.  Anything in _PLACEHOLDER_MARKERS is matched as a
# substring and so needs no entry here.
_PLACEHOLDER_VALUES = frozenset(
    {
        "admin",
        "any",
        "auto",
        "boolean",
        "byte",
        "char",
        "default",
        "demo",
        "double",
        "empty",
        "false",
        "fixme",
        "float",
        "guest",
        "inherit",
        "int",
        "integer",
        "key",
        "local",
        "localhost",
        "long",
        "n/a",
        "nil",
        "none",
        "null",
        "number",
        "object",
        "optional",
        "password",
        "passwd",
        "pwd",
        "read",
        "required",
        "root",
        "secret",
        "string",
        "test",
        "testing",
        "token",
        "true",
        "undefined",
        "unknown",
        "void",
        "write",
    }
)
# Substrings, so this catches the `your_password` / `my_api_key` / `xxxx`
# family as well as the bare words.
_PLACEHOLDER_MARKERS = (
    "your",
    "changeme",
    "change_me",
    "change-me",
    "placeholder",
    "example",
    "sample",
    "dummy",
    "fake",
    "todo",
    "xxx",
)

# Files where an unquoted value is a literal.  In source files an unquoted
# value is an expression or a type, so only quoted values are reported there.
# The launch-script extensions stay because these projects ship Windows and
# Bash launchers; Terraform and container build files are gone because nothing
# here uses them.
_CONFIG_SUFFIXES = frozenset(
    {
        ".bat",
        ".bash",
        ".cfg",
        ".cmd",
        ".conf",
        ".config",
        ".env",
        ".ini",
        ".json",
        ".md",
        ".properties",
        ".ps1",
        ".sh",
        ".sql",
        ".toml",
        ".txt",
        ".xml",
        ".yaml",
        ".yml",
    }
)
_CONFIG_NAMES = frozenset({".gitattributes", ".gitignore"})


class Hit(NamedTuple):
    path: str
    line: int
    rule: str


def _looks_like_placeholder(value: str) -> bool:
    lowered = value.lower()
    if lowered in _PLACEHOLDER_VALUES:
        return True
    if value.startswith(("${", "{{", "<%", "%(")):
        return True
    if any(marker in lowered for marker in _PLACEHOLDER_MARKERS):
        return True
    return len(set(value)) == 1


def _strip_syntax(raw: str) -> str:
    value = raw.strip()
    while value[-1:] in (";", ","):
        value = value[:-1].rstrip()
    return value


def _key_value_is_secret(raw: str, is_config: bool) -> bool:
    value = _strip_syntax(raw)
    if not value:
        return False
    quoted = value[0] in "\"'"
    if quoted:
        end = value.find(value[0], 1)
        inner = value[1:end] if end != -1 else value[1:]
    else:
        inner = value
    if not inner or _looks_like_placeholder(inner):
        return False
    if quoted:
        return True
    # An unquoted value is a single token.  Anything with a space in it is
    # prose or an expression -- `PasswordAuthentication=no (see the docs)`,
    # `id-token: write` in a sentence -- not a credential.
    if not is_config or any(char.isspace() for char in inner):
        return False
    if _QUANTITY_RE.match(inner) or _CODE_CHARS_RE.search(inner):
        return False
    return True


def _connection_string_is_secret(line: str) -> bool:
    for match in _CONNECTION_RE.finditer(line):
        credentials, _, hostport = match.group("authority").rpartition("@")
        host = hostport.split(":", 1)[0]
        if any(char in host for char in "{}$%"):
            continue  # templated host: ${DB_HOST}, %s
        if not _HOSTNAME_RE.match(host):
            continue  # prose about schemes, not a URL
        if credentials:
            return True  # credentials embedded in the URL
        lowered = host.lower()
        if lowered in _LOCAL_HOSTS or lowered.endswith(_RESERVED_SUFFIXES):
            continue
        return True
    return False


def _national_id_is_secret(match: "re.Match") -> bool:
    digits = match.group(0)
    total = sum(int(char) * weight for char, weight in zip(digits[:17], _ID_WEIGHTS))
    return _ID_CHECK_CODES[total % 11] == digits[17].upper()


def _phone_is_secret(number: str) -> bool:
    if number in _KNOWN_FAKE_PHONES:
        return False
    return re.search(r"(\d)\1{6,}", number) is None


def _email_is_secret(address: str) -> bool:
    local, _, domain = address.rpartition("@")
    domain = domain.lower()
    if domain in _RESERVED_DOMAINS:
        return False
    if any(label in _RESERVED_LABELS for label in domain.split(".")):
        return False
    if local.lower() in _NON_PERSONAL_LOCAL_PARTS:
        return False
    return not _looks_like_placeholder(local)


def _rules_on_line(line: str, is_config: bool) -> Optional[str]:
    for match in _KEY_VALUE_RE.finditer(line):
        if _key_value_is_secret(match.group("value"), is_config):
            return "sensitive-key"
    if _PRIVATE_KEY_RE.search(line):
        return "private-key"
    if _connection_string_is_secret(line):
        return "connection-string"
    for match in _NATIONAL_ID_RE.finditer(line):
        if _national_id_is_secret(match):
            return "national-id"
    for match in _PHONE_RE.finditer(line):
        if _phone_is_secret(match.group(1)):
            return "phone"
    for match in _EMAIL_RE.finditer(line):
        if _email_is_secret(match.group(0)):
            return "email"
    if _LOCAL_PATH_RE.search(line):
        return "local-path"
    return None


def _is_config_file(relative_path: str) -> bool:
    name = relative_path.rsplit("/", 1)[-1].lower()
    if name in _CONFIG_NAMES or name.startswith(".env"):
        return True
    return "." in name and name[name.rindex(".") :] in _CONFIG_SUFFIXES


def git_visible_files(root: Path) -> List[str]:
    """Paths git would commit from *root*, relative and POSIX-separated.

    A throw-away empty index is used so every file counts as untracked: the
    result is the working tree minus what ``.gitignore`` excludes, whether or
    not anything has been committed yet.
    """
    with tempfile.TemporaryDirectory(prefix="privacy-scan-index-") as scratch:
        subprocess.run(
            ["git", "init", "--quiet", scratch],
            check=True,
            capture_output=True,
        )
        listed = subprocess.run(
            [
                "git",
                "--git-dir",
                str(Path(scratch) / ".git"),
                "--work-tree",
                str(root),
                "-c",
                "core.excludesFile=",
                "ls-files",
                "--others",
                "--exclude-standard",
                "-z",
            ],
            check=True,
            capture_output=True,
        )
    return [name for name in listed.stdout.decode("utf-8", "replace").split("\0") if name]


def scan(root: Path) -> List[Hit]:
    root = root.resolve()
    own_source = Path(__file__).resolve()
    hits: List[Hit] = []
    for relative_path in git_visible_files(root):
        path = root / relative_path
        if path.resolve() == own_source:
            continue
        try:
            handle = path.open("rb")
        except OSError:
            continue
        with handle:
            if b"\0" in handle.read(BINARY_SNIFF_BYTES):
                continue
            handle.seek(0)
            is_config = _is_config_file(relative_path)
            for number, raw in enumerate(handle, 1):
                line = raw.decode("utf-8", "replace")
                rule = _rules_on_line(line, is_config)
                if rule:
                    hits.append(Hit(relative_path, number, rule))
    return hits


def main(argv: List[str]) -> int:
    arguments = argv[1:]
    if arguments and arguments[0] in ("-h", "--help"):
        print(__doc__.strip())
        return EXIT_CLEAN
    if len(arguments) > 1:
        print("usage: privacy_scan.py [DIR]", file=sys.stderr)
        return EXIT_ERROR
    root = Path(arguments[0]) if arguments else Path.cwd()
    if not root.is_dir():
        print("privacy_scan: not a directory: {}".format(root), file=sys.stderr)
        return EXIT_ERROR
    try:
        hits = scan(root)
    except FileNotFoundError:
        print("privacy_scan: git is required but not on PATH", file=sys.stderr)
        return EXIT_ERROR
    except (OSError, subprocess.SubprocessError) as error:
        print("privacy_scan: could not list files: {}".format(error), file=sys.stderr)
        return EXIT_ERROR
    for hit in hits:
        print("{}:{}: {}".format(hit.path, hit.line, hit.rule))
    if hits:
        print(
            "privacy_scan: {} hit(s) in {} file(s) - do not push until resolved".format(
                len(hits), len({hit.path for hit in hits})
            ),
            file=sys.stderr,
        )
        return EXIT_HITS
    print("privacy_scan: clean", file=sys.stderr)
    return EXIT_CLEAN


if __name__ == "__main__":
    sys.exit(main(sys.argv))
