"""Experimental answer envelope; valid references do not imply supported claims."""
import json


def source_ids(source_count):
    if type(source_count) is not int or not 1 <= source_count <= 6:
        raise ValueError('Require 1..6 supplied sources')
    return [f'S{index}' for index in range(1, source_count + 1)]


def answer_schema(source_count):
    identifiers = source_ids(source_count)
    text = dict(type='string', minLength=1, maxLength=1200)
    claim = dict(type='object', additionalProperties=False, required=['text', 'sources'],
                 properties=dict(text=text, sources=dict(type='array', minItems=1,
                    maxItems=source_count, items=dict(type='string', enum=identifiers))))
    return dict(oneOf=[
        dict(type='object', additionalProperties=False, required=['status', 'claims'],
             properties=dict(status=dict(const='answered'),
                 claims=dict(type='array', minItems=1, maxItems=6, items=claim))),
        dict(type='object', additionalProperties=False, required=['status', 'reason'],
             properties=dict(status=dict(const='insufficient'), reason=text)),
    ])


def parse_answer(content, source_count):
    identifiers = set(source_ids(source_count))
    if not isinstance(content, str) or len(content.encode('utf-8')) > 64000:
        raise ValueError('Answer envelope is not bounded text')

    def object_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate JSON key')
            result[key] = value
        return result

    def invalid_constant(value):
        raise ValueError('Non-JSON numeric constant')

    def valid_text(value):
        if not isinstance(value, str) or not 1 <= len(value) <= 1200 or not value.strip():
            return False
        try:
            value.encode('utf-8')
        except UnicodeError:
            return False
        return True

    try:
        parsed = json.loads(content, object_pairs_hook=object_pairs, parse_constant=invalid_constant)
    except (RecursionError, UnicodeError) as error:
        raise ValueError('Invalid JSON envelope') from error
    if not isinstance(parsed, dict):
        raise ValueError('Expected an answer object')
    if parsed.get('status') == 'insufficient':
        if set(parsed) != {'status', 'reason'} or not valid_text(parsed['reason']):
            raise ValueError('Invalid insufficient-evidence answer')
    elif parsed.get('status') == 'answered':
        if set(parsed) != {'status', 'claims'} or not isinstance(parsed['claims'], list) or not 1 <= len(parsed['claims']) <= 6:
            raise ValueError('Expected 1..6 cited answer segments')
        for claim in parsed['claims']:
            if not isinstance(claim, dict) or set(claim) != {'text', 'sources'} or not valid_text(claim['text']):
                raise ValueError('Invalid claim text')
            sources = claim['sources']
            if not isinstance(sources, list) or not 1 <= len(sources) <= source_count:
                raise ValueError('Claim needs supplied source IDs')
            if any(not isinstance(item, str) or item not in identifiers for item in sources):
                raise ValueError('Claim references an unavailable source')
            if len(set(sources)) != len(sources):
                raise ValueError('Repeated source ID in a claim')
    else:
        raise ValueError('Unknown answer status')
    return parsed
