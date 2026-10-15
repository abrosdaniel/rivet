"""Validate local page links, fragments and assets in the generated Pages site."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import sys


class Page(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.ids = set()
        self.links = []
        self.feed(text)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if 'id' in attrs:
            self.ids.add(attrs['id'])
        attribute = 'href' if tag in ('a', 'link') else 'src' if tag in ('img', 'script') else None
        if attribute and attrs.get(attribute):
            self.links.append(attrs[attribute])


def validate(directory):
    root = directory.resolve()
    pages = {p: Page(p.read_text(encoding='utf-8')) for p in root.rglob('*.html')}
    if not pages:
        raise SystemExit(f'No generated pages in {directory}; run mkdocs build first')
    errors = []
    for source, page in pages.items():
        for href in page.links:
            url = urlsplit(href)
            if url.scheme or url.netloc:
                continue
            path = unquote(url.path)
            if path.startswith('/rivet/'):
                target = root / path[len('/rivet/'):]
            elif path.startswith('/'):
                errors.append(f'{source.relative_to(root)}: unexpected absolute path {href}')
                continue
            else:
                target = (source.parent / path).resolve() if path else source
            if target.is_dir():
                target /= 'index.html'
            if not target.is_relative_to(root) or not target.is_file():
                errors.append(f'{source.relative_to(root)}: missing {href}')
            elif url.fragment and target in pages and unquote(url.fragment) not in pages[target].ids:
                errors.append(f'{source.relative_to(root)}: missing anchor {href}')
    for error in errors:
        print(error)
    print(f'{len(pages)} pages checked; {len(errors)} broken local links or assets')
    return bool(errors)


if __name__ == '__main__':
    sys.exit(validate(Path(sys.argv[1] if len(sys.argv) > 1 else 'build/docs-site')))
