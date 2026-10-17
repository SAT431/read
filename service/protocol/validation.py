import math
from service.protocol.envelopes import MentorRequest, MentorResponse

# Field types mirror Kotlin AppCommand. Android additionally validates semantic state,
# nested educational data, source ownership, presentation policy and interaction locks.
COMMAND_FIELDS = {
    'app.navigate': {'destination': str}, 'app.back': {}, 'subject.open': {'subject': str},
    'chapter.open': {'chapterId': str}, 'library.search': {'text': str},
    'library.request_import': {'chapterId': str}, 'library.open_pdf': {'pdfId': str},
    'library.study_pdf': {'pdfId': str}, 'pdf.page': {'pageIndex': int},
    'source.engineer_page': {'documentId': str, 'pageIndex': int},
    'voice.start': {}, 'voice.finish': {}, 'voice.cancel': {}, 'voice.auto_send': {'enabled': bool},
    'pdf.next': {}, 'pdf.previous': {}, 'pdf.zoom': {'zoom': float},
    'pdf.transform': {'zoom': float, 'panX': float, 'panY': float},
    'preferences.set': {'preferences': dict}, 'workspace.command': {'command': dict},
    'workspace.undo_layout': {}, 'quiz.select': {'activityId': str, 'questionId': str, 'option': int},
    'quiz.submit': {'activityId': str, 'questionId': str},
    'reader.position': {'activityId': str, 'index': int, 'offset': int},
    'component.pin': {'componentId': str, 'pinned': bool},
    'diagram.transform': {'activityId': str, 'zoom': float, 'panX': float, 'panY': float},
}
WORKSPACE_FIELDS = {
    'CREATE_ACTIVITY': {'activityId': str, 'componentId': str, 'kind': str, 'source': dict, 'data': dict},
    'SHOW_COMPONENT': {'componentId': str, 'activityId': str, 'kind': str},
    'FOCUS_ACTIVITY': {'activityId': str}, 'SUSPEND_ACTIVITY': {'activityId': str},
    'RESUME_ACTIVITY': {'activityId': str}, 'HIDE_COMPONENT': {'componentId': str},
    'SET_PRESENTATION': {'componentId': str, 'presentation': str},
    'SET_PRIORITY': {'componentId': str, 'priority': int},
    'PLAY_TRANSITION': {'componentId': str, 'motion': str}, 'FOCUS_SOURCE': {'source': dict},
    'OPEN_CHAPTER': {'chapterId': str}, 'OPEN_PAGE': {'documentId': str, 'pageIndex': int},
    'NEXT_PAGE': {}, 'PREVIOUS_PAGE': {}, 'SHOW_ORIGINAL': {'visible': bool},
}


def check_shape(command, definitions):
    if not isinstance(command, dict) or command.get('type') not in definitions:
        raise ValueError('Unknown action')
    fields = definitions[command['type']]
    if set(command) != {'type', *fields}:
        raise ValueError('Unknown/missing action fields')
    for key, kind in fields.items():
        value = command[key]
        if kind is float:
            if type(value) not in (int, float) or not math.isfinite(value):
                raise ValueError('Invalid numeric argument')
        elif type(value) is not kind:
            raise ValueError('Invalid argument type')
        if isinstance(value, str) and len(value) > 12_000:
            raise ValueError('Argument too long')


def validate_response(response: MentorResponse, request: MentorRequest):
    context = request.capabilityContext
    if response.requestId != request.requestId:
        raise ValueError('Request mismatch')
    batch = response.batch
    if batch is None:
        return
    if batch.requestId != request.requestId or batch.expectedRevision != context['revision']:
        raise ValueError('Revision/request mismatch')
    enabled = {a['id'] for a in context['actions'] if a.get('enabled', True)}
    chapters = {c['id'] for c in context.get('chapters', [])}
    pdfs = {p['id'] for p in context.get('pdfs', [])}
    for command in batch.commands:
        check_shape(command, COMMAND_FIELDS)
        if command['type'] not in enabled:
            raise ValueError('Unavailable action')
        if 'chapterId' in command and command['chapterId'] not in chapters:
            raise ValueError('Unknown curriculum chapter')
        if 'pdfId' in command and command['pdfId'] not in pdfs:
            raise ValueError('Unknown PDF')
        if command['type'] == 'source.engineer_page':
            documents = {p['documentId']: p['pages'] for p in context.get('pdfs', [])}
            if command['documentId'] not in documents or not 0 <= command['pageIndex'] < documents[command['documentId']]:
                raise ValueError('Unknown PDF page')
        if 'subject' in command and command['subject'] not in {'PHYSICS', 'CHEMISTRY', 'BOTANY', 'ZOOLOGY'}:
            raise ValueError('Unknown subject')
        if 'destination' in command and command['destination'] not in {'HOME', 'WORKSPACE', 'EXPLORER', 'SETTINGS', 'ASSISTANT'}:
            raise ValueError('Unknown destination')
        if 'zoom' in command and not 0.5 <= command['zoom'] <= 4:
            raise ValueError('Invalid zoom')
        if command['type'] == 'pdf.transform' and any(abs(command[k]) > 2000 for k in ('panX', 'panY')):
            raise ValueError('Invalid PDF pan')
        if 'pageIndex' in command and command['pageIndex'] < 0:
            raise ValueError('Negative page')
        if command['type'] == 'quiz.select' and command['option'] not in range(4):
            raise ValueError('Invalid option')
        if command['type'] == 'workspace.command':
            check_shape(command['command'], WORKSPACE_FIELDS)
        if command['type'] == 'preferences.set':
            # Older v1 clients omit this newly added preference; native defaults to review mode.
            preferences = {'voiceAutomaticSend': False, **command['preferences']}
            check_shape({'type': 'preferences', **preferences}, {'preferences': {
                'darkTheme': bool, 'readingSizeSp': int, 'reducedMotion': bool, 'voiceCaptions': bool,
                'voiceAutomaticSend': bool}})
