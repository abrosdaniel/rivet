"""Keep repository-file links usable on Pages without copying their contents."""
import re
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"


def on_page_markdown(markdown, page, config, files):
    markdown = re.sub(
        r"^> \[!IMPORTANT\]\n((?:>[^\n]*(?:\n|$))+)",
        lambda m: '!!! important "Важно"\n\n' + "\n".join(
            "    " + line.removeprefix("> ") for line in m[1].splitlines()
        ) + "\n",
        markdown, flags=re.M,
    )

    def replace(match):
        target = match.group(1)
        if target.startswith(("#", "/")) or ":" in target:
            return match.group(0)
        path, sep, fragment = target.partition("#")
        resolved = (DOCS / page.file.src_uri).parent.joinpath(path).resolve()
        if resolved.is_relative_to(DOCS):
            return match.group(0)
        if not resolved.is_relative_to(ROOT) or not resolved.is_file():
            raise ValueError(f"Invalid repository link in {page.file.src_uri}: {target}")
        url = "https://github.com/abrosdaniel/rivet/blob/main/" + quote(resolved.relative_to(ROOT).as_posix())
        return "](" + url + ("#" + fragment if sep else "") + ")"

    # Preserve examples inside fenced code blocks.
    parts = re.split(r"(^```[^\n]*\n.*?^```\s*$)", markdown, flags=re.M | re.S)
    return "".join(part if i % 2 else re.sub(r"\]\(([^)]+)\)", replace, part) for i, part in enumerate(parts))


def slugify(value, separator):
    """Match existing GitHub heading links, including Cyrillic and double hyphens."""
    return re.sub(r"[^\w\- ]", "", value.lower()).replace(" ", separator)
