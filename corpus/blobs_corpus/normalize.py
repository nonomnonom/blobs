"""Readable article text with explicit headings, tables and source attribution."""

import hashlib
import json
import re
import unicodedata
from urllib.parse import quote, urlsplit, unquote

from .records import validate_document

EXTRACTOR_VERSION = "5"
FURNITURE = (
    "script,style,nav,footer,form,.navbox,.vertical-navbox,.mw-editsection,"
    ".mw-jump-link,.toc,.catlinks,.printfooter,.mw-cite-backlink,.noprint,"
    '#siteSub,#jump-to-nav,.hatnote,[role~="navigation"]'
)
BLOCKS = {
    "p",
    "li",
    "dt",
    "dd",
    "pre",
    "blockquote",
    "div",
    "section",
    "ul",
    "ol",
    "dl",
    "table",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
}


class ArticleRedirect(ValueError):
    def __init__(self, target):
        self.target = target
        super().__init__("HTML redirect")


def clean(text: str) -> str:
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", text)).strip()


def bounded_join(parts, separator=""):
    result, length = [], 0
    for part in parts:
        length += len(part) + len(separator)
        if length > 1_000_000:
            raise ValueError("Expanded text exceeds 1,000,000 character limit")
        result.append(part)
    return separator.join(result)


def _table(node):
    caption = node.css_first("caption")
    if caption:
        yield "Table: " + clean(caption.text())
    # Retain row/column ordering, repeated span values and units. Do not infer
    # relationships that were not present in the rendered source table.
    spans = {}
    for row in node.css("tr"):
        parent = row.parent
        while parent and parent.tag != "table":
            parent = parent.parent
        if parent is None or parent.mem_id != node.mem_id:
            continue
        cells, column = [], 0
        for cell in row.iter():
            if cell.tag not in {"td", "th"}:
                continue
            while column in spans:
                value, remaining = spans.pop(column)
                cells.append(value)
                if remaining > 1:
                    spans[column] = (value, remaining - 1)
                column += 1
            # Flatten nested tables once. Recursively expanding their spans can
            # multiply text exponentially even when the input HTML is small.
            value = clean(cell.text(separator=" "))
            try:
                width = min(50, max(1, int(cell.attributes.get("colspan", "1"))))
                height = min(200, max(1, int(cell.attributes.get("rowspan", "1"))))
            except ValueError:
                width, height = 1, 1
            for _ in range(width):
                cells.append(value)
                if height > 1:
                    spans[column] = (value, height - 1)
                column += 1
        while column in spans:
            value, remaining = spans.pop(column)
            cells.append(value)
            if remaining > 1:
                spans[column] = (value, remaining - 1)
            column += 1
        if any(cells):
            yield bounded_join(cells, " | ")


def _blocks(node, depth=0, details=None):
    if depth > 128:
        raise ValueError("Article block nesting exceeds 128 levels")
    tag = node.tag
    if tag == "table":
        yield bounded_join(_table(node), "\n")
        return
    if tag == "h1":
        return  # Title is stored separately, not duplicated in every lead.
    if tag in {"h2", "h3", "h4", "h5", "h6"}:
        if details:
            pending = details[:]
            details.clear()
            yield f"{'=' * int(tag[1])} Article details {'=' * int(tag[1])}"
            yield from pending
        title = clean(node.text())
        if title:
            yield f"{'=' * int(tag[1])} {title} {'=' * int(tag[1])}"
        return
    if tag == "pre":
        yield unicodedata.normalize("NFC", node.text()).strip()
        return
    buffer = []
    child = node.child
    while child:
        if child.tag in BLOCKS:
            if clean("".join(buffer)):
                yield clean("".join(buffer))
            buffer = []
            yield from _blocks(child, depth + 1, details)
        elif child.tag == "br":
            buffer.append("\n")
        elif child.is_text_node:
            buffer.append(child.text())
        elif not child.is_comment_node:
            # Recurse through wrappers containing blocks, preserving DOM order.
            if child.css_first("p,table,h2,h3,h4,h5,h6,ul,ol,dl"):
                if clean("".join(buffer)):
                    yield clean("".join(buffer))
                buffer = []
                yield from _blocks(child, depth + 1, details)
            else:
                buffer.append(child.text())
        child = child.next
    text = clean("".join(buffer))
    if text:
        yield ("• " if tag == "li" else "") + text


def _leading_infoboxes(root):
    """Keep leading reference tables together, after the introductory prose."""
    tables, ids = [], set()
    for node in root.css("table.infobox,p,h2,h3,h4,h5,h6"):
        parent = node.parent
        inside_table = False
        while parent and parent.mem_id != root.mem_id:
            if parent.mem_id in ids:
                inside_table = True
                break
            parent = parent.parent
        if inside_table:
            continue
        if node.tag == "table":
            tables.append(node)
            ids.add(node.mem_id)
        elif node.tag == "p" and not clean(node.text()):
            continue
        else:
            break
    details = [bounded_join(_table(node), "\n") for node in tables]
    for node in tables:
        node.decompose()
    return details


def _stackexchange_signature(post):
    """Author name and posting date of one Stack Exchange post."""
    signature = post.css_first(".post-signature")
    if signature is None:
        return "", ""
    link = signature.css_first(".s-user-card--link")
    stamp = signature.css_first("time[datetime]")
    return (clean(link.text()) if link is not None else ""), (
        clean(stamp.text()) if stamp is not None else ""
    )


def _stackexchange_post(post):
    body = post.css_first(".js-post-body")
    if body is None:
        return ""
    return "\n\n".join(text for text in _blocks(body) if text.strip())


def _stackexchange_body(tree):
    """Question and answers with per-post attribution instead of site chrome."""
    question = tree.css_first("#question")
    if question is None:
        raise ValueError("Missing Stack Exchange question page")
    question_id = question.attributes.get("data-questionid", "") or ""
    if not re.fullmatch(r"\d+", question_id):
        raise ValueError("Missing Stack Exchange question identity")
    question_text = _stackexchange_post(question)
    if not question_text:
        raise ValueError("Missing Stack Exchange question body")
    answers = tree.css(".answer")
    asker, asked = _stackexchange_signature(question)
    header = ["Asked" + (f" by {asker}" if asker else "")]
    if asked:
        header.append(asked)
    header.append(f"{len(answers)} answer" + ("" if len(answers) == 1 else "s"))
    score = question.attributes.get("data-score", "") or ""
    if re.fullmatch(r"-?\d+", score):
        header.append(f"question score {score}")
    parts = [" · ".join(header), question_text]
    for index, answer in enumerate(answers, start=1):
        text = _stackexchange_post(answer)
        if not text:
            continue
        heading = [f"Answer {index}"]
        score = answer.attributes.get("data-score", "") or ""
        if re.fullmatch(r"-?\d+", score):
            heading.append(f"score {score}")
        if "accepted-answer" in (answer.attributes.get("class") or "").split():
            heading.append("accepted")
        author, date = _stackexchange_signature(answer)
        if author:
            heading.append(f"by {author}")
        if date:
            heading.append(date)
        answer_id = answer.attributes.get("data-answerid", "") or ""
        if re.fullmatch(r"\d+", answer_id):
            heading.append(f"a/{answer_id}")
        parts.append("== " + " · ".join(heading) + " ==")
        parts.append(text)
    return "\n\n".join(parts), dict(question_id=question_id, answers=len(answers))


def article(html: bytes, title: str, path: str, lock: dict) -> dict:
    from selectolax.lexbor import LexborHTMLParser

    # Decode strictly: silently replacing bad bytes can change technical claims.
    tree = LexborHTMLParser(html.decode("utf-8"))
    for meta in tree.css("meta[http-equiv]"):
        if meta.attributes.get("http-equiv", "").lower() == "refresh":
            match = re.search(
                r"(?:^|;)\s*url\s*=\s*(.*)$", meta.attributes.get("content", ""), re.I
            )
            if match:
                target = match.group(1).strip()
                if len(target) >= 2 and target[0] in "'\"" and target[-1] == target[0]:
                    target = target[1:-1]
                target = unquote(target.removeprefix("./"))
                if not target:
                    raise ValueError("Empty HTML redirect target")
                raise ArticleRedirect(target)
    config = {}
    for script in tree.css("script"):
        match = re.search(r"\bRLCONF\s*=\s*", script.text())
        if match:
            try:
                config = json.JSONDecoder().raw_decode(script.text()[match.end() :])[0]
            except ValueError:
                pass
            break
    canonical = tree.css_first('link[rel="canonical"]')
    canonical_url = canonical.attributes.get("href", "") if canonical else ""
    navigation_count = len(tree.css('[role~="navigation"]'))
    for node in tree.css(FURNITURE):
        node.decompose()
    for node in tree.css("math"):
        annotation = node.css_first('annotation[encoding="application/x-tex"]')
        node.replace_with(annotation.text() if annotation else node.text())
    for node in tree.css("img.mwe-math-fallback-image-inline,img.mwe-math-fallback-image-display"):
        node.replace_with(node.attributes.get("alt", ""))
    project = str(lock.get("id", "")).split("-")[0]
    moved_infoboxes = 0
    exchange = None
    if project == "stackexchange":
        body, exchange = _stackexchange_body(tree)
    else:
        root = tree.css_first(".mw-parser-output")
        if root is None:
            root = tree.css_first("#mw-content-text")
        if root is None:
            root = tree.body
        if root is None:
            raise ValueError("Missing article body")
        details = _leading_infoboxes(root)
        moved_infoboxes = len(details)

        def ordered_blocks():
            yield from _blocks(root, details=details)
            if details:
                yield "== Article details =="
                yield from details

        body = bounded_join((text for text in ordered_blocks() if text.strip()), "\n\n")
    # A stable URL identifies the article; the lock + ZIM path identify its
    # actual snapshot. Do not invent a per-article revision or modified date.
    article_path = path[2:] if path.startswith("A/") else path
    wiki_host = lock.get("wiki_host", "en.wikipedia.org")
    if project == "stackexchange":
        # Stack Exchange has no /wiki/ path; the archive path is the site path.
        url = f"https://{wiki_host}/" + quote(article_path, safe="/()_,:-")
        history_url = f"https://{wiki_host}/posts/{exchange['question_id']}/revisions"
    else:
        url = f"https://{wiki_host}/wiki/" + quote(article_path, safe="/()_,:-")
        parsed = urlsplit(canonical_url)
        if (
            parsed.scheme == "https"
            and parsed.netloc == wiki_host
            and parsed.path.startswith("/wiki/")
        ):
            url = canonical_url
        history_url = url + "?action=history"
    revision = config.get("wgCurRevisionId")
    revision = revision if isinstance(revision, int) and revision > 0 else None
    normalization = dict(
        aria_navigation_nodes_removed=navigation_count,
        leading_infoboxes_moved=moved_infoboxes,
    )
    if exchange:
        normalization.update(answers=exchange["answers"], question_id=exchange["question_id"])
    item = dict(
        id="wiki-" + hashlib.sha256(url.encode()).hexdigest()[:32],
        title=clean(title),
        publisher=lock["publisher"],
        url=url,
        license=lock["license"] + " Attribution/history: " + history_url,
        updated="Snapshot " + lock["edition"],
        saved=lock["verified_date"],
        collection=lock["collection"],
        body=body,
        provenance=dict(
            source_sha256=lock["sha256"],
            entry_path=path,
            html_sha256=hashlib.sha256(html).hexdigest(),
            body_sha256=hashlib.sha256(body.encode()).hexdigest(),
            extractor=EXTRACTOR_VERSION,
            normalization=normalization,
            revision_id=revision,
            revision_url=f"https://{wiki_host}/w/index.php?oldid={revision}" if revision else None,
            categories=config.get("wgCategories", []),
            history_url=history_url,
            date_precision="archive edition; per-article modification date unavailable",
        ),
    )
    validate_document(item)
    if len(body) < 80:
        raise ValueError("Article has fewer than 80 characters after normalization")
    return item
