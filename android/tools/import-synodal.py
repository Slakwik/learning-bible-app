"""Import eBible public-domain russyn VPL archive without changing verse text.
Usage: python3 android/tools/import-synodal.py /path/to/russyn_vpl.zip
Source: https://ebible.org/Scriptures/russyn_vpl.zip
"""
import hashlib, json, sys, zipfile
from pathlib import Path
archive = Path(sys.argv[1])
book_ids = {'EZE':'EZK','JOH':'JHN','MAR':'MRK','SOL':'SNG','JAM':'JAS','PHI':'PHP','JOE':'JOL','NAH':'NAM','1JO':'1JN','2JO':'2JN','3JO':'3JN'}
books = {}
with zipfile.ZipFile(archive) as source:
    for line in source.read('russyn_vpl.txt').decode('utf-8-sig').splitlines():
        if not line.strip(): continue
        book, address, text = line.split(' ', 2)
        book = book_ids.get(book, book)
        chapter, verse = address.split(':')
        verses = books.setdefault(book, {}).setdefault(chapter, {})
        assert verse not in verses, (book, address)
        assert text.strip() and chapter.isdigit() and verse.isdigit(), (book, address)
        verses[verse] = text
assert len(books) == 66
out = Path(__file__).resolve().parents[1] / 'app/src/main/assets/bible-synodal'
out.mkdir(parents=True, exist_ok=True)
for book, chapters in books.items():
    (out / (book + '.json')).write_text(json.dumps(chapters, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
manifest = {'source':'https://ebible.org/Scriptures/russyn_vpl.zip', 'license':'Public Domain',
            'source_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(), 'books':len(books),
            'chapters':sum(len(c) for c in books.values()),
            'verses':sum(len(v) for c in books.values() for v in c.values())}
(out/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
print(json.dumps(manifest))
