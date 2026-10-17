import hashlib
import re
from collections import Counter


def compile_page(store, evidence, draft, version=1):
    elements = []
    for order, item in enumerate(draft.elements):
        left, top, right, bottom = item.bounds
        key = f"{evidence['documentId']}:{evidence['pageIndex']}:{version}:{order}:{item.kind}:{item.bounds}"
        identifier = 'source-' + hashlib.sha256(key.encode()).hexdigest()[:32]
        media = store.crop(evidence['documentId'], evidence['pageIndex'], item.bounds) if item.kind == 'FIGURE' else None
        elements.append({'id': identifier, 'kind': item.kind, 'originalText': item.text, 'order': order,
                         'bounds': {'left': left, 'top': top, 'right': right, 'bottom': bottom},
                         'parentId': None, 'mediaId': media, 'relatedElementIds': []})
    text_layer = '\n'.join(b.get('text', '') for b in evidence['blocks'] if b['kind'] == 'text')
    reconstructed = '\n'.join(e['originalText'] for e in elements)
    tokenize = lambda text: Counter(re.findall(r'\w+|[^\w\s]', text))
    source_tokens, model_tokens = tokenize(text_layer), tokenize(reconstructed)
    missing = source_tokens - model_tokens
    extra = model_tokens - source_tokens
    fidelity = {'textLayerAvailable': evidence['textLayerPresent'],
                'missingEvidenceTokenCount': sum(missing.values()), 'additionalModelTokenCount': sum(extra.values()),
                'missingEvidenceTokens': list(missing.elements())[:100],
                'additionalModelTokens': list(extra.elements())[:100], 'uncertainties': draft.uncertainties,
                'verification': 'UNCHECKED',
                'note': 'Token comparison is discrepancy evidence, not proof of accuracy; scans require visual review.'}
    page = {'documentId': evidence['documentId'], 'pageIndex': evidence['pageIndex'], 'title': draft.title,
            'elements': elements, 'status': 'UNCHECKED', 'engineeringVersion': version,
            'printedLabel': None, 'corrections': {}, 'isFixture': False,
            'widthPoints': evidence['width'], 'heightPoints': evidence['height']}
    return {'schemaVersion': 1, 'page': page, 'fidelity': fidelity,
            'originalMediaId': evidence['originalMediaId']}
