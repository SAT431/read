import importlib
import os
import tempfile
os.environ['FOLIO_DATA']=tempfile.mkdtemp(prefix='folio-test-')
from fastapi.testclient import TestClient
import pymupdf
from backend.app import app
client=TestClient(app)

def sample():
    doc=pymupdf.open()
    p=doc.new_page()
    p.insert_text((72,72),'Cells contain genetic material. Ribosomes assemble proteins.')
    return doc.tobytes()

def test_import_verify_duplicate_and_immutable_source():
    data=sample()
    response=client.post('/api/upload',files={'file':('page.pdf',data,'application/pdf')},data={'chapter':'Test cell','number':'1'})
    assert response.status_code==200
    page=response.json(); source=page['elements'][0]['text']; eid=page['elements'][0]['id']
    assert client.get(page['original']).status_code==200
    approved=client.post(f"/api/pages/{page['id']}/verify",json={'version':1,'corrections':{eid:'Approved correction'},'approved':True})
    assert approved.status_code==200
    assert approved.json()['elements'][0]['text']==source
    duplicate=client.post('/api/upload',files={'file':('page.pdf',data,'application/pdf')},data={'chapter':'Test cell','number':'1'})
    assert duplicate.json()['corrections'][eid]=='Approved correction'
    assert client.post(f"/api/pages/{page['id']}/verify",json={'version':1,'corrections':{},'approved':True}).status_code==409

def test_page_bounds_and_invalid_file():
    assert client.post('/api/upload',files={'file':('page.pdf',sample(),'application/pdf')},data={'chapter':'Test','number':'9'}).status_code==400
    assert client.post('/api/upload',files={'file':('bad.txt',b'hello','text/plain')},data={'chapter':'Test'}).status_code==415

def test_workspace_revision_conflict():
    assert client.put('/api/workspace',json={'revision':0,'state':{'version':1,'answers':[2]}}).status_code==200
    assert client.put('/api/workspace',json={'revision':0,'state':{'version':1,'answers':[]}}).status_code==409
    assert client.get('/api/workspace').json()['state']['answers']==[2]

def test_cross_origin_write_denied():
    assert client.put('/api/workspace',headers={'Origin':'https://example.com'},json={'revision':1,'state':{'version':1}}).status_code==403
