#!/usr/bin/env python3
"""
ACGXmh Cosplay Index Builder

Crawls www.acgxmh.com/cos/ listing pages and builds a JSON index
mapping photo set titles to their IDs for search functionality.

Usage:
    python crawl_index.py [--output index.json] [--max-pages 0]

Options:
    --output    Output file path (default: index.json)
    --max-pages Maximum pages to crawl, 0 for all (default: 0)
    --delay     Delay between requests in seconds (default: 1.0)
"""

import argparse
import json
import os
import re
import sys
import time
from urllib.request import Request, urlopen
from html.parser import HTMLParser


class CosListParser(HTMLParser):
    """Parse cosplay listing pages to extract title and ID."""

    def __init__(self):
        super().__init__()
        self.entries = []
        self.last_page = 1
        self._in_list = False
        self._in_li = False
        self._current_entry = None

    def handle_starttag(self, tag, attrs):
        attrs_dict = dict(attrs)

        if tag == "ul" and attrs_dict.get("id") == "list":
            self._in_list = True
            return

        if self._in_list and tag == "li":
            self._in_li = True
            self._current_entry = {}
            return

        if self._in_li and tag == "a" and "thumb" in attrs_dict.get("class", ""):
            href = attrs_dict.get("href", "")
            title = attrs_dict.get("title", "")
            match = re.search(r"/cos/(\d+)\.html", href)
            if match and self._current_entry is not None:
                self._current_entry["id"] = int(match.group(1))
                self._current_entry["title"] = title

        if self._in_li and tag == "img" and self._current_entry is not None:
            src = attrs_dict.get("src", "")
            if src:
                self._current_entry["thumb"] = src

        # Parse pagination for last page
        if tag == "div" and "bigpage" in attrs_dict.get("class", ""):
            pass
        if tag == "a":
            href = attrs_dict.get("href", "")
            match = re.search(r"index-(\d+)\.html", href)
            if match:
                page_num = int(match.group(1))
                self.last_page = max(self.last_page, page_num)

    def handle_endtag(self, tag):
        if self._in_list and tag == "ul":
            self._in_list = False
        if self._in_li and tag == "li":
            if self._current_entry and "id" in self._current_entry:
                self.entries.append(self._current_entry)
            self._in_li = False
            self._current_entry = None


def fetch_page(url, retries=3, delay=1.0):
    """Fetch a URL with retries."""
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                       "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    }
    for attempt in range(retries):
        try:
            print(f"  [HTTP] GET {url} ...")
            start = time.time()
            req = Request(url, headers=headers)
            with urlopen(req, timeout=30) as resp:
                data = resp.read().decode("utf-8", errors="replace")
                elapsed = time.time() - start
                print(f"  [HTTP] {resp.status} OK  ({len(data)} bytes, {elapsed:.1f}s)")
                return data
        except Exception as e:
            elapsed = time.time() - start
            if attempt < retries - 1:
                print(f"  [WARN] Retry {attempt + 1}/{retries} after {elapsed:.1f}s: {e}")
                time.sleep(delay * (attempt + 1))
            else:
                print(f"  [ERROR] Failed after {retries} attempts: {e}")
                raise


def crawl_index(base_url="https://www.acgxmh.com", max_pages=0, delay=1.0):
    """Crawl all listing pages and return entries."""
    all_entries = []
    seen_ids = set()
    crawl_start = time.time()

    # Fetch first page to determine total pages
    print(f"\n{'='*60}")
    print(f"[PAGE 1] Fetching first page to discover total pages ...")
    print(f"{'='*60}")
    html = fetch_page(f"{base_url}/cos/")
    parser = CosListParser()
    parser.feed(html)

    for entry in parser.entries:
        if entry["id"] not in seen_ids:
            seen_ids.add(entry["id"])
            all_entries.append(entry)

    total_pages = parser.last_page
    if max_pages > 0:
        total_pages = min(total_pages, max_pages)

    print(f"[INFO] Page 1: found {len(parser.entries)} entries")
    print(f"[INFO] Total pages to crawl: {total_pages}")
    print(f"[INFO] Estimated time: ~{total_pages * (delay + 1):.0f}s "
          f"(delay={delay}s per page)")

    # Fetch remaining pages
    for page in range(2, total_pages + 1):
        elapsed = time.time() - crawl_start
        remaining_pages = total_pages - page + 1
        eta = remaining_pages * (elapsed / (page - 1))

        print(f"\n[PAGE {page}/{total_pages}] "
              f"Progress: {(page - 1) / total_pages * 100:.1f}%  |  "
              f"Entries: {len(all_entries)}  |  "
              f"Elapsed: {elapsed:.0f}s  |  "
              f"ETA: {eta:.0f}s")
        try:
            html = fetch_page(f"{base_url}/cos/index-{page}.html")
            parser = CosListParser()
            parser.feed(html)

            new_count = 0
            for entry in parser.entries:
                if entry["id"] not in seen_ids:
                    seen_ids.add(entry["id"])
                    all_entries.append(entry)
                    new_count += 1

            dup_count = len(parser.entries) - new_count
            print(f"  [OK] +{new_count} new entries"
                  f"{f', {dup_count} duplicates skipped' if dup_count else ''}"
                  f"  (total: {len(all_entries)})")
        except Exception as e:
            print(f"  [ERROR] Page {page} failed: {e}")

        time.sleep(delay)

    total_elapsed = time.time() - crawl_start
    print(f"\n{'='*60}")
    print(f"[DONE] Crawled {total_pages} pages in {total_elapsed:.1f}s")
    print(f"[DONE] Total unique entries: {len(all_entries)}")
    print(f"{'='*60}")

    return all_entries


def main():
    argparser = argparse.ArgumentParser(description="ACGXmh Cosplay Index Builder")
    argparser.add_argument("--output", default="index.json", help="Output file path")
    argparser.add_argument("--max-pages", type=int, default=0, help="Max pages to crawl (0=all)")
    argparser.add_argument("--delay", type=float, default=1.0, help="Delay between requests (seconds)")
    args = argparser.parse_args()

    print(f"\n{'='*60}")
    print(f"[START] ACGXmh Cosplay Index Builder")
    print(f"[CONFIG] max_pages={args.max_pages}, delay={args.delay}s, output={args.output}")
    print(f"{'='*60}")
    entries = crawl_index(max_pages=args.max_pages, delay=args.delay)

    # Sort by ID descending (newest first)
    entries.sort(key=lambda x: x["id"], reverse=True)

    output = {
        "version": 1,
        "updated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "count": len(entries),
        "entries": entries,
    }

    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    file_size = os.path.getsize(args.output)
    print(f"\n[SAVED] {args.output}  ({len(entries)} entries, {file_size / 1024:.1f} KB)")
    print(f"[TIP] Copy this file to:")
    print(f"       src/zh/acgxmhcos/assets/index.json")
    print(f"       Then rebuild the extension to include the search index.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
