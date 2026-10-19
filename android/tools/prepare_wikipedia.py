"""Fetch an attributed, revision-pinned starter library. This runs on the build computer only.

Existing JSONL is reused so reruns preserve the captured revision. This collection is a
small integration/evaluation corpus, not comprehensive coverage or a quality benchmark.
"""

from datetime import datetime, timezone
import json
from pathlib import Path
import time
import urllib.parse
import urllib.request
import urllib.error

TOPICS = """Photosynthesis|Cell (biology)|Evolution|Natural selection|DNA|Genetics|Ecosystem|Biodiversity|Carbon cycle|Water cycle|Climate change|Greenhouse effect|Renewable energy|Solar energy|Wind power|Nuclear power|Energy storage|Lithium-ion battery|Heat pump|Urban heat island|Cool roof|Urban forestry|Water purification|Desalination|Ocean acidification|Plate tectonics|Earthquake|Volcano|Gravity|General relativity|Quantum mechanics|Thermodynamics|Entropy|Electricity|Electromagnetism|Semiconductor|Transistor|Computer|Algorithm|Computational complexity|Machine learning|Artificial intelligence|Neural network (machine learning)|Large language model|Retrieval-augmented generation|Cryptography|Public-key cryptography|Computer security|Operating system|Database|Internet|Statistics|Probability|Bayes' theorem|Correlation does not imply causation|Scientific method|Randomized controlled trial|Peer review|Economic growth|Inflation|Opportunity cost|Supply and demand|Externality|Public good (economics)|Game theory|Democracy|Separation of powers|Rule of law|Human rights|Philosophy|Ethics|Logic|Critical thinking|Cognitive bias|Confirmation bias|Memory|Sleep|Circadian rhythm|Exercise|Nutrition|Immune system|Vaccine|Antibiotic|Antimicrobial resistance|Germ theory of disease|Public health|Agriculture|Sustainable agriculture|Soil|Irrigation|Industrial Revolution|Printing press|Renaissance|History of science|History of mathematics|Linguistics|Language acquisition|Education|Music|Art""".split(
    "|"
)


def main():
    output = Path(__file__).resolve().parents[1] / "packages" / "wikipedia-starter.jsonl"
    output.parent.mkdir(exist_ok=True)
    existing = (
        {json.loads(line)["title"] for line in output.read_text(encoding="utf-8").splitlines()}
        if output.exists()
        else set()
    )
    for topic in TOPICS:
        if topic in existing:
            continue
        query = urllib.parse.urlencode(
            dict(
                action="query",
                prop="extracts|info|revisions",
                explaintext=1,
                inprop="url",
                rvprop="timestamp|ids",
                format="json",
                titles=topic,
                redirects=1,
            )
        )
        request = urllib.request.Request(
            "https://en.wikipedia.org/w/api.php?" + query,
            headers={
                "User-Agent": "BlobsOfflineResearch/0.1 (local, attributed educational corpus)"
            },
        )
        for attempt in range(6):
            try:
                data = json.load(urllib.request.urlopen(request, timeout=60))
                break
            except urllib.error.HTTPError as error:
                if error.code not in (429, 502, 503) or attempt == 5:
                    raise
                time.sleep(min(45, 5 * (attempt + 1)))
        page = next(iter(data["query"]["pages"].values()))
        revision = page["revisions"][0]
        item = dict(
            id=str(page["pageid"]),
            title=page["title"],
            publisher="Wikipedia contributors",
            url=f"https://en.wikipedia.org/w/index.php?oldid={revision['revid']}",
            license="CC BY-SA 4.0; https://creativecommons.org/licenses/by-sa/4.0/; adapted into plain text and passages. Article history at the source address identifies contributors.",
            updated=revision["timestamp"],
            saved=datetime.now(timezone.utc).isoformat(),
            body=page["extract"],
            collection="Wikipedia · Science & everyday questions",
        )
        with output.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(item, ensure_ascii=False) + "\n")
        print(item["title"], len(item["body"]), flush=True)
        time.sleep(1)
    print(output)


if __name__ == "__main__":
    main()
