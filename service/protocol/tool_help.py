"""Educational payload shapes mirror the native model; source/state validation stays native."""

ACTIVITY_HELP = '''
CREATE_ACTIVITY data uses a type discriminator (not kind):
READER -> {"type":"reader"}; EXPLANATION -> {"type":"explanation","text":string};
COMPARISON -> {"type":"comparison","headers":string[],"rows":string[][]};
DIAGRAM -> {"type":"diagram"} only with a supplied source figure/media anchor;
QUIZ -> {"type":"quiz","questions":[{"id":new unique string,"prompt":string,
"options":exactly four strings,"correctOption":integer 0..3,"explanation":string,
"sources":[SourceAnchor]}]}. Do not provide answers/submitted state on creation.
SourceAnchor = {"documentId":known source document ID,"pageIndex":zero-based index,
"elementId":known source element ID or null}. Do not invent source elements or page content.
Use componentContracts for supported presentations, motions, actions and gestures;
an enum name existing in code does not prove that a component supports it.
For SET_PRESENTATION use only a supplied contract's presentations; motion names come
from its motions. CREATE_ACTIVITY and SHOW_COMPONENT require fresh activity/component
IDs unless showing an existing activity; never replace existing learner data.
preferences.set must include all current preference fields: darkTheme (boolean),
readingSizeSp (integer 14..36), reducedMotion (boolean), voiceCaptions (boolean),
voiceAutomaticSend (boolean). voice.auto_send changes only the submission mode.
Enable automatic sending only when requested by the user; otherwise keep review mode.
Preserve fields the user did not ask to change. Do not select answers or submit practice
on the learner's behalf unless the user explicitly requests that operation.
'''
