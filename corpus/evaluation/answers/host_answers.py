"""Controlled Windows CPU answer comparison, separate from Android product evidence."""
import argparse
from contextlib import contextmanager
import ctypes
import json
from pathlib import Path
import platform
import re
import secrets
import shutil
import socket
import struct
import subprocess
import tempfile
import textwrap
import time
import urllib.error
import urllib.request
import zipfile

from blobs_corpus.extract import write_json
from blobs_corpus.package import verify_package
from ..retrieval.host_replay import sha
from .citation_output import answer_schema, parse_answer


SAMPLING_PROFILES = ('controlled', 'product', 'qwen35-general', 'qwen35-no-penalty',
                     'qwen35-2b-text')


def sampling_for(profile):
    sampling = dict(n_predict=768, temperature=0.3, top_k=20, top_p=0.8, min_p=0,
                    repeat_penalty=1.0, presence_penalty=0.0, frequency_penalty=0.0,
                    repeat_last_n=256, seed=42, cache_prompt=False,
                    samplers=['penalties', 'top_k', 'top_p', 'temperature'])
    if profile == 'product':
        sampling.update(presence_penalty=1.5)
    elif profile == 'qwen35-general':
        sampling.update(temperature=0.7, presence_penalty=1.5)
    elif profile == 'qwen35-no-penalty':
        sampling.update(temperature=0.7)
    elif profile == 'qwen35-2b-text':
        sampling.update(temperature=1.0, top_p=1.0, presence_penalty=2.0)
    elif profile != 'controlled':
        raise ValueError('Unknown sampling profile')
    return sampling


def record_chat_chunk(response, packet):
    """Keep parsed channels separate; raw terminal output may be absent in streaming mode."""
    choices = packet.get('choices', [])
    if len(choices) > 1 or (choices and choices[0].get('index') != 0):
        raise ValueError('Expected a single completion')
    if choices:
        choice = choices[0]
        delta = choice.get('delta', {})
        if delta.get('tool_calls'):
            raise ValueError('This evaluator does not execute tools')
        for field in ('content', 'reasoning_content'):
            value = delta.get(field)
            if value is None:
                value = ''
            if not isinstance(value, str):
                raise ValueError('Expected text in ' + field)
            response[field] = response.get(field, '') + value
        if choice.get('finish_reason') is not None:
            response['finish_reason'] = choice['finish_reason']
    if '__verbose' in packet and packet['__verbose'].get('stop'):
        raw = packet['__verbose']
        response.update({key: value for key, value in raw.items() if key != 'content'})
        response['raw_content'] = raw['content']
        response['raw_output_available'] = bool(raw['content'])
    for field in ('usage', 'timings'):
        if field in packet:
            response[field] = packet[field]


def completion_packets(opener, request, non_streaming):
    with opener.open(request, timeout=300 if non_streaming else 60) as response:
        if non_streaming:
            packet = json.load(response)
            if 'error' not in packet:
                packet['choices'] = [dict(choice, delta=choice['message']) for choice in packet['choices']]
            yield packet
            yield None
        else:
            for line in response:
                if line.startswith(b'data: '):
                    if line[6:].strip() == b'[DONE]':
                        yield None
                        return
                    yield json.loads(line[6:])


@contextmanager
def prepared_model(args):
    if args.model_package is None:
        yield args.model
        return
    manifest = verify_package(args.model_package)
    if manifest['kind'] != 'model' or manifest['sha256'] != args.model_sha256:
        raise ValueError('Package differs from the selected model artifact')
    work = args.work_directory or Path(tempfile.gettempdir())
    work.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(work).free < manifest['bytes'] + 1024**3:
        raise OSError('Allow one temporary model payload plus 1 GiB headroom')
    with tempfile.TemporaryDirectory(prefix='blobs-host-model-', dir=work) as folder:
        model = Path(folder) / (manifest['id'] + '.gguf')
        count = 0
        with zipfile.ZipFile(args.model_package) as archive, archive.open('model.gguf') as source, model.open('xb') as output:
            while block := source.read(1024 * 1024):
                count += len(block)
                if count > manifest['bytes']:
                    raise ValueError('Model package changed or exceeds its declared size')
                output.write(block)
        if count != manifest['bytes'] or sha(model) != args.model_sha256:
            raise ValueError('Extracted model differs from the selected artifact')
        yield model


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True)
    model = parser.add_mutually_exclusive_group(required=True)
    model.add_argument('--model', type=Path)
    model.add_argument('--model-package', type=Path,
                       help='Verified .blobs package; extract one temporary payload for the run')
    parser.add_argument('--work-directory', type=Path,
                        help='Temporary extraction volume when using --model-package')
    parser.add_argument('--model-sha256', required=True)
    parser.add_argument('--retrieval-report', type=Path, required=True)
    parser.add_argument('--cases', nargs='+', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--system-file', type=Path,
                        help='Explicit experimental system prompt; does not change the Android application')
    parser.add_argument('--retrieval-variant',
                        help='Required when the input report contains more than one library variant')
    parser.add_argument('--omit-soft-switch', action='store_true',
                        help='Ablation: omit legacy trailing /no_think text; explicit template enable_thinking=false remains')
    parser.add_argument('--namespaced-citations', action='store_true',
                        help='Use product source IDs S1, S2, ... to distinguish references from article footnotes')
    parser.add_argument('--structured-citations', action='store_true',
                        help='Experimental runtime JSON-schema constraint and independent envelope validation; requires namespaced citations')
    parser.add_argument('--sampling-profile', choices=SAMPLING_PROFILES, default='controlled',
                        help='Controlled baseline, exact product values, Qwen3.5 controls, or the pinned Qwen3.5-2B non-thinking text profile')
    parser.add_argument('--reasoning-budget', type=int, choices=[0, 512],
                        help='Use the chat API for a bounded comparison: 0 disables thinking, 512 enables it. Both allow 1280 total generated tokens.')
    parser.add_argument('--non-streaming', action='store_true',
                        help='Chat diagnostic: retain complete raw output/token IDs; no first-content latency measurement')
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    if args.work_directory is not None and args.model_package is None:
        parser.error('--work-directory requires --model-package')
    if args.structured_citations and (not args.namespaced_citations or args.system_file is None):
        parser.error('--structured-citations requires --namespaced-citations and --system-file')
    if args.reasoning_budget is not None and not (args.omit_soft_switch and args.structured_citations):
        parser.error('--reasoning-budget requires --omit-soft-switch and --structured-citations')
    if args.non_streaming and args.reasoning_budget is None:
        parser.error('--non-streaming requires --reasoning-budget')
    if args.sampling_profile == 'qwen35-2b-text':
        if not args.omit_soft_switch:
            parser.error('--sampling-profile qwen35-2b-text requires --omit-soft-switch')
        if args.reasoning_budget not in (None, 0):
            parser.error('--sampling-profile qwen35-2b-text is a non-thinking profile')
    with prepared_model(args) as model:
        args.model = model
        evaluate(args)


def evaluate(args):
    if sha(args.model) != args.model_sha256:
        raise ValueError('Model differs from its locked artifact')
    source = json.loads(args.retrieval_report.read_text(encoding='utf-8'))
    if not source['complete'] or len(set(args.cases)) != len(args.cases):
        raise ValueError('Require a complete retrieval report and unique cases')
    definitions = {row['id']: row for row in source['cases']['cases']}
    variants = {row.get('variant', 'candidateLibrary') for row in source['results']}
    if args.retrieval_variant is None and len(variants) != 1:
        raise ValueError('Choose one --retrieval-variant explicitly')
    variant = args.retrieval_variant or next(iter(variants))
    if variant not in variants:
        raise ValueError('Requested retrieval variant is missing')
    selected = [row for row in source['results'] if row.get('variant', 'candidateLibrary') == variant]
    evidence = {row['case_id']: row['evidence'] for row in selected}
    if len(evidence) != len(selected):
        raise ValueError('Retrieval report repeats cases within one variant')
    if any(case not in definitions or case not in evidence for case in args.cases):
        raise ValueError('Requested case is missing')
    if any(definitions[case].get('previous_question') for case in args.cases):
        raise ValueError('This component comparison does not synthesize missing conversation history')
    root = Path(__file__).resolve().parents[2]
    version = subprocess.run([str(args.server.resolve()), '--version'], capture_output=True,
                             text=True, encoding='utf-8', check=True,
                             creationflags=subprocess.CREATE_NO_WINDOW if platform.system() == 'Windows' else 0)
    runtime_version = version.stdout + version.stderr
    if '620d2225' not in runtime_version:
        raise ValueError('Host runtime differs from the pinned native commit')
    host_memory = None
    if platform.system() == 'Windows':
        memory = ctypes.create_string_buffer(64)
        struct.pack_into('<I', memory, 0, 64)
        if ctypes.windll.kernel32.GlobalMemoryStatusEx(memory):
            host_memory = struct.unpack_from('<Q', memory, 8)[0]
    controller = root.parent / 'android/app/src/main/kotlin/dev/blobs/app/data/WorkController.kt'
    system = textwrap.dedent(re.search(r'val system = """(.*?)"""\.trimIndent\(\)',
                                      controller.read_text(encoding='utf-8'), re.S)[1]).strip()
    if args.system_file is not None:
        if not 0 < args.system_file.stat().st_size <= 16384:
            raise ValueError('Experimental system prompt must be 1..16,384 bytes')
        system = args.system_file.read_text(encoding='utf-8').strip()
        if not system:
            raise ValueError('Experimental system prompt is empty')
    if args.omit_soft_switch:
        system = system.removesuffix('/no_think').rstrip()
    sampling = sampling_for(args.sampling_profile)
    chat_api = args.reasoning_budget is not None
    template_kwargs = {'enable_thinking': bool(args.reasoning_budget)}
    if chat_api:
        sampling['n_predict'] = 1280
    input_limit = min(3072, 4096 - sampling['n_predict'] - 1)
    report = dict(format=1, complete=False, platform=platform.platform(), processor=platform.processor(),
                  scope='Host component comparison, not the Android product path or a <=12 GB environment test. CPU only; one resident model; no prompt cache. Timings are not phone predictions. Exact sampler values and the selected profile are recorded; value agreement alone does not prove native sampler-history behavior. A model recommendation still needs native evaluation.',
                  host_physical_memory_bytes=host_memory, runtime_version=runtime_version,
                  model=dict(file=args.model.name, bytes=args.model.stat().st_size, sha256=args.model_sha256),
                  model_package_sha256=sha(args.model_package) if args.model_package else None,
                  server_sha256=sha(args.server), runtime_commit='620d2225cc436ab0b001e3200e590e3b286ef9ab',
                  script_sha256=sha(Path(__file__)), retrieval_report_sha256=sha(args.retrieval_report),
                  retrieval_variant=variant,
                  work_controller_sha256=sha(controller), system=system,
                  experimental_system_sha256=sha(args.system_file) if args.system_file else None,
                  template_kwargs=template_kwargs,
                  endpoint='/v1/chat/completions' if chat_api else '/completion',
                  reasoning_budget=args.reasoning_budget,
                  streaming=not args.non_streaming,
                  generation_budget_scope='Shared total including reasoning and final answer; no independent final-answer cap.',
                  legacy_soft_switch=not args.omit_soft_switch,
                  source_id_scheme='S-prefixed' if args.namespaced_citations else 'numeric',
                  structured_citations=args.structured_citations,
                  sampling_profile=args.sampling_profile,
                  penalty_history='Pinned llama-server accepts prompt tokens before generation; the current JNI sampler starts after prefill. With penalties disabled, that history has no penalty effect.',
                  native_bridge_sha256=sha(root.parent / 'android/app/src/main/cpp/blobs.cpp'),
                  citation_contract_sha256=sha(Path(__file__).with_name('citation_output.py')) if args.structured_citations else None,
                  requested_cases=args.cases, sampling=sampling, context=4096, input_limit=input_limit,
                  threads=4, batch=256, ubatch=128, results=[])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    key = secrets.token_hex(24)
    base = f'http://127.0.0.1:{port}'
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def request(path, value=None, timeout=300):
        data = json.dumps(value).encode() if value is not None else None
        req = urllib.request.Request(base + path, data=data,
                                     headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + key})
        with opener.open(req, timeout=timeout) as response:
            return json.load(response)

    command = [str(args.server.resolve()), '-m', str(args.model.resolve()), '--host', '127.0.0.1',
               '--port', str(port), '--api-key', key, '--offline', '-ngl', '0', '-c', '4096',
               '-b', '256', '-ub', '128', '-t', '4', '-tb', '4', '-np', '1', '--no-warmup',
               '--no-cache-prompt', '--jinja', '--chat-template-kwargs', json.dumps(template_kwargs)]
    with args.output.with_suffix('.server.log').open('wb') as log:
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW if platform.system() == 'Windows' else 0)
        try:
            deadline = time.monotonic() + 90
            while True:
                if process.poll() is not None:
                    raise RuntimeError(f'Server exited during model load: {process.returncode}')
                try:
                    if request('/health', timeout=1).get('status') == 'ok':
                        break
                except (urllib.error.URLError, TimeoutError):
                    pass
                if time.monotonic() > deadline:
                    raise TimeoutError('Model load exceeded 90 seconds')
                time.sleep(0.2)
            for case in args.cases:
                item = definitions[case]
                sources = evidence[case]
                row = dict(case_id=case, question=item['question'], evidence=sources)
                report['results'].append(row)
                if not sources:
                    row['status'] = 'NO_SOURCES'
                    write_json(args.output, report)
                    continue
                attribute, prefix = ('id', 'S') if args.namespaced_citations else ('number', '')
                prompt = ''.join(f'\n<source {attribute}="{prefix}{index}">\n{entry["title"]}\n{entry["text"]}\n</source>\n'
                                 for index, entry in enumerate(sources, 1)) + f'\nQuestion: {item["question"]}'
                if not args.omit_soft_switch:
                    prompt += '\n/no_think'
                row['escaped_special_token_openers'] = prompt.count('<|')
                prompt = prompt.replace('<|', '< |')
                row['user_prompt'] = prompt
                write_json(args.output, report)
                try:
                    messages = [dict(role='system', content=system), dict(role='user', content=prompt)]
                    formatted = request('/apply-template', dict(messages=messages, chat_template_kwargs=template_kwargs))['prompt']
                    row['formatted_prompt'] = formatted
                    tokens = request('/tokenize', dict(content=formatted, add_special=True, parse_special=True))['tokens']
                    row['prompt_tokens'] = len(tokens)
                    if len(tokens) > input_limit:
                        row['status'] = 'INPUT_TOO_LONG'
                    else:
                        started = time.monotonic()
                        completion = dict(prompt=tokens, stream=True, **sampling)
                        if args.structured_citations:
                            row['output_schema'] = answer_schema(len(sources))
                            completion['json_schema'] = row['output_schema']
                        if chat_api:
                            completion = dict(messages=messages, stream=not args.non_streaming, **sampling,
                                stream_options={'include_usage': True}, verbose=True, return_tokens=True,
                                reasoning_format='deepseek', reasoning_budget_tokens=args.reasoning_budget,
                                chat_template_kwargs=template_kwargs,
                                response_format={'type': 'json_schema', 'json_schema': {'schema': row['output_schema']}})
                        row['completion_request'] = completion
                        body = json.dumps(completion).encode()
                        req = urllib.request.Request(base + report['endpoint'], data=body,
                                                     headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + key})
                        row['response'] = {'content': ''}
                        finished, last_save = False, started
                        for packet in completion_packets(opener, req, args.non_streaming):
                            if time.monotonic() - started > 300:
                                raise TimeoutError('Generation exceeded 300 seconds; partial text preserved')
                            if packet is None:
                                finished = bool(row['response'].get('finish_reason') and
                                                row['response'].get('stop') and 'usage' in row['response'])
                                break
                            if 'error' in packet:
                                raise RuntimeError(str(packet['error']))
                            if chat_api:
                                previous = row['response']['content']
                                record_chat_chunk(row['response'], packet)
                                text = row['response']['content'][len(previous):]
                                if not args.non_streaming and row['response'].get('reasoning_content') and 'first_reasoning_arrival_ms' not in row:
                                    row['first_reasoning_arrival_ms'] = (time.monotonic() - started) * 1000
                            else:
                                text = packet.get('content', '')
                                content = row['response']['content'] + text
                                row['response'] = dict(packet, content=content)
                            if not args.non_streaming and text and 'first_content_arrival_ms' not in row:
                                row['first_content_arrival_ms'] = (time.monotonic() - started) * 1000
                            if time.monotonic() - last_save >= 0.5:
                                write_json(args.output, report)
                                last_save = time.monotonic()
                            if not chat_api and packet.get('stop'):
                                finished = True
                                break
                        if not finished:
                            raise RuntimeError('Completion stream ended without terminal metadata')
                        row['host_elapsed_ms'] = (time.monotonic() - started) * 1000
                        if args.structured_citations:
                            try:
                                parsed = parse_answer(row['response']['content'], len(sources))
                                row['structured_validation'] = dict(valid=True, value=parsed,
                                    scope='Structure and source IDs only; factual support still requires review.')
                            except ValueError as error:
                                row['structured_validation'] = dict(valid=False, error=str(error))
                        row['status'] = 'RECORDED'
                except Exception as error:
                    row.update(status='ERROR', error=repr(error))
                write_json(args.output, report)
                print(case + ': ' + row['status'], flush=True)
            report['complete'] = True
        except Exception as error:
            report['error'] = repr(error)
            raise
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
            write_json(args.output, report)


if __name__ == '__main__':
    main()
