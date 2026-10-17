"""Build frozen native prefill-probe inputs from a retained product answer."""
import argparse
import json
from pathlib import Path
import re

from blobs_corpus.extract import write_json
from ..retrieval.host_replay import sha


def build(run_path: Path, controller: Path, protocol_path: Path, output: Path):
    run = json.loads(run_path.read_text(encoding='utf-8'))
    protocol = json.loads(protocol_path.read_text(encoding='utf-8'))
    if sha(run_path) != protocol['input_report']['sha256']:
        raise ValueError('Product report differs from frozen protocol')
    if sha(controller) != protocol['work_controller_sha256']:
        raise ValueError('WorkController differs from frozen protocol')
    if run.get('question') != protocol['question'] or run.get('model_sha256') != protocol['model_sha256']:
        raise ValueError('Question or model differs from frozen protocol')
    evidence = json.loads(run['evidence'])
    source = controller.read_text(encoding='utf-8')
    match = re.search(r'val system = """(.*?)"""\.trimIndent\(\)', source, re.S)
    if not match:
        raise ValueError('Could not locate product system prompt')
    lines = match.group(1).splitlines()
    indentation = min(len(line) - len(line.lstrip()) for line in lines if line.strip())
    system = '\n'.join(line[indentation:] for line in lines).strip()
    if output.exists():
        raise FileExistsError(output)
    output.mkdir(parents=True)
    rows = []
    system_path = output / 'system.txt'
    system_path.write_text(system, encoding='utf-8', newline='\n')
    for configuration in protocol['configurations']:
        indices = configuration['evidence_indices']
        if (not indices or len(indices) != len(set(indices)) or
                any(type(index) is not int or not 1 <= index <= len(evidence) for index in indices)):
            raise ValueError('Invalid evidence selection')
        selected = [evidence[index - 1] for index in indices]
        prompt = ''.join(
            f'\n<source number="{number}">\n{item["title"]}\n{item["text"]}\n</source>\n'
            for number, item in enumerate(selected, 1)
        ) + f'\nQuestion: {run["question"]}\n/no_think'
        prompt_path = output / f'{configuration["id"]}-user.txt'
        prompt_path.write_text(prompt, encoding='utf-8', newline='\n')
        rows.append(dict(
            id=configuration['id'], evidence_indices=indices,
            source_titles=[item['title'] for item in selected],
            source_characters=sum(len(item['text']) for item in selected),
            user_characters=len(prompt),
            user=dict(file=prompt_path.name, bytes=prompt_path.stat().st_size, sha256=sha(prompt_path)),
        ))
    manifest = dict(
        format=1, complete=True, scope=protocol['scope'], output_limit=protocol['output_limit'],
        model_sha256=protocol['model_sha256'], protocol_sha256=sha(protocol_path),
        product_report_sha256=sha(run_path), work_controller_sha256=sha(controller),
        system=dict(file=system_path.name, bytes=system_path.stat().st_size, sha256=sha(system_path)),
        configurations=rows,
    )
    write_json(output / 'manifest.json', manifest)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--controller', type=Path, required=True)
    parser.add_argument('--protocol', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = build(args.run, args.controller, args.protocol, args.output)
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
